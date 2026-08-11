package org.example.lead_service;

import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.dto.CreationCommandeRequest;
import org.example.lead_service.dto.LeadDTO;
import org.example.lead_service.dto.NoteInteractionDTO;
import org.example.lead_service.entity.*;
import org.example.lead_service.mapper.CommandeMapper;
import org.example.lead_service.mapper.LeadMapper;
import org.example.lead_service.repository.CommandeRepository;
import org.example.lead_service.repository.LeadRepository;
import org.example.lead_service.service.LeadServiceImpl;
import org.example.lead_service.client.StockClient;
import org.example.lead_service.client.AbonnementClient;
import org.example.lead_service.client.UserClient;
import org.example.lead_service.dto.StockProductDTO;
import org.example.lead_service.dto.StockInventoryDTO;
import org.example.lead_service.dto.ExternalOrderImportRequest;
import org.example.lead_service.event.OrderEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.example.common.security.TenantContext;

@ExtendWith(MockitoExtension.class)
class LeadServiceImplTest {

    @Mock
    private LeadRepository leadRepository;
    @Mock
    private CommandeRepository commandeRepository;
    @Mock
    private LeadMapper leadMapper;
    @Mock
    private CommandeMapper commandeMapper;
    @Mock
    private StockClient stockClient;
    @Mock
    private AbonnementClient abonnementClient;
    @Mock
    private UserClient userClient;
    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private LeadServiceImpl leadService;

    private Lead mockLead;
    private CoordonneesClient mockCoords;

    @BeforeEach
    void setUp() {
        TenantContext.setEnterpriseId(7L);
        TenantContext.setUserId(42L);
        mockCoords = CoordonneesClient.builder()
                .nomComplet("John Doe")
                .telephone("+212600000000")
                .adresseLivraison("1 Test Street")
                .ville("Tétouan")
                .build();

        // Utilisation d'un ArrayList modifiable pour l'historique afin d'éviter les UnsupportedOperationException
        mockLead = Lead.builder()
                .idLead(1L)
                .statutLead(StatutLead.NEW_LEAD)
                .ordrePriorite(OrdrePriorite.HIGH)
                .infosClient(mockCoords)
                .boutiqueId(10L)
                .agentId(42L)
                .enterpriseId(7L)
                .historiqueInteractions(new ArrayList<>())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void assignerAgent_DevraitMettreAJourAgentId() {
        // Arrange
        when(leadRepository.findByIdLeadAndEnterpriseId(1L, 7L)).thenReturn(Optional.of(mockLead));
        when(leadRepository.save(any(Lead.class))).thenReturn(mockLead);
        when(leadMapper.toDto(any(Lead.class))).thenReturn(new LeadDTO());
        when(userClient.getStaffMember(42L))
                .thenReturn(new UserClient.UserSummary(42L, "ROLE_CSM", true));

        // Act
        leadService.assignerAgent(1L, 42L);

        // Assert
        assertEquals(42L, mockLead.getAgentId());
        verify(leadRepository, times(1)).save(mockLead);
    }

    @Test
    void enregistrerInteraction_DevraitMettreAJourStatutEtAjouterNote() {
        // Arrange
        when(leadRepository.findByIdLeadAndEnterpriseId(1L, 7L)).thenReturn(Optional.of(mockLead));

        // Simule le comportement de la sauvegarde : renvoie le lead fourni en entrée
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> {
            Lead leadToSave = invocation.getArgument(0);
            // On simule l'attribution d'un ID par la BDD sur la note ajoutée pour passer le filtre du stream
            if (!leadToSave.getHistoriqueInteractions().isEmpty()) {
                leadToSave.getHistoriqueInteractions().get(0).setIdHistorique(100L);
            }
            return leadToSave;
        });

        // Modifié pour correspondre à ton LeadMapper unique (qui prend une NoteInteraction)
        when(leadMapper.toDto(any(NoteInteraction.class))).thenReturn(new NoteInteractionDTO());

        // Act
        NoteInteractionDTO result = leadService.enregistrerInteraction(
                1L,
                TypeInteraction.APPEL_TEL,
                "Client intéressé, rappel planifié",
                StatutLead.SCHEDULED_RECALL
        );

        // Assert
        assertNotNull(result);
        assertEquals(StatutLead.SCHEDULED_RECALL, mockLead.getStatutLead());
        assertEquals(1, mockLead.getHistoriqueInteractions().size());
        assertEquals("NEW_LEAD", mockLead.getHistoriqueInteractions().get(0).getAncienStatut());
        assertEquals("SCHEDULED_RECALL", mockLead.getHistoriqueInteractions().get(0).getNouveauStatut());
        verify(leadRepository, times(1)).save(mockLead);
    }

    @Test
    void convertirEnCommande_DevraitCreerCommandeEtChangerStatutEnConverted() {
        // Arrange
        mockLead.setStatutLead(StatutLead.IN_PROGRESS);
        when(leadRepository.findByIdLeadAndEnterpriseId(1L, 7L)).thenReturn(Optional.of(mockLead));
        when(leadRepository.findDetailedByIdLeadAndEnterpriseId(1L, 7L)).thenReturn(Optional.of(mockLead));
        when(leadRepository.save(any(Lead.class))).thenReturn(mockLead);
        when(commandeMapper.toDto(any(Commande.class))).thenReturn(new CommandeDTO());
        when(commandeRepository.findByReferenceAndLeadEnterpriseId(anyString(), eq(7L)))
                .thenReturn(Optional.empty());
        when(abonnementClient.currentEntitlement())
                .thenReturn(new AbonnementClient.Entitlement(true, 100, null));
        when(stockClient.obtenirBoutique(10L)).thenReturn(new Object());
        when(stockClient.obtenirProduit(anyLong())).thenReturn(StockProductDTO.builder()
                .prixVente(500.0)
                .build());
        StockInventoryDTO inventory = new StockInventoryDTO();
        inventory.setQuantiteDisponible(20);
        when(stockClient.obtenirInventaire(eq(10L), anyLong())).thenReturn(inventory);

        // Préparation du DTO de requête avec les articles simulés
        CreationCommandeRequest request = new CreationCommandeRequest();
        request.setIdempotencyKey("test-order-1");
        request.setStockLocationId(10L);

        CreationCommandeRequest.ItemRequest item1 = new CreationCommandeRequest.ItemRequest();
        item1.setProductId(102L);
        item1.setQuantity(2);

        CreationCommandeRequest.ItemRequest item2 = new CreationCommandeRequest.ItemRequest();
        item2.setProductId(205L);
        item2.setQuantity(1);

        request.setItems(List.of(item1, item2));

        // Act
        CommandeDTO result = leadService.convertirEnCommande(1L, request);

        // Assert
        assertNotNull(result);
        assertEquals(StatutLead.CONVERTED, mockLead.getStatutLead());
        assertNotNull(mockLead.getCommande());

        // Vérifications de la logique d'encapsulation de Commande
        Commande commandeGeneree = mockLead.getCommande();
        assertEquals(1500.00, commandeGeneree.getTotalPrix());
        assertEquals(2, commandeGeneree.getLignesCommande().size());
        assertEquals(StatutCommande.CONFIRMEE, commandeGeneree.getStatutCommande());

        // On s'assure que le lead entier a été sauvegardé (ce qui propage l'enregistrement de la commande)
        verify(leadRepository, times(1)).save(mockLead);
        verify(stockClient, times(2)).reserverStock(eq(10L), anyLong(), any(StockClient.ReservationRequest.class));
    }

    @Test
    void convertirEnCommande_ReusesImportedOrderWithoutRepickingProductsOrDoubleReserving() {
        mockLead.setStatutLead(StatutLead.IN_PROGRESS);
        mockLead.setSource(LeadSource.SHOPIFY);
        mockLead.setBoutiqueId(10L);
        Commande imported = Commande.builder()
                .lead(mockLead)
                .reference("EXT-shopify-900")
                .statutCommande(StatutCommande.EN_ATTENTE)
                .statutPaiement(StatutPaiementCommande.PAID)
                .stockLocationId(10L)
                .stockReservationReference("EXT-shopify-900")
                .totalPrix(100.0)
                .infosClient(mockLead.getInfosClient())
                .build();
        imported.ajouterLigne(102L, 2, 50.0);
        mockLead.setCommande(imported);

        when(leadRepository.findByIdLeadAndEnterpriseId(1L, 7L)).thenReturn(Optional.of(mockLead));
        when(leadRepository.findDetailedByIdLeadAndEnterpriseId(1L, 7L)).thenReturn(Optional.of(mockLead));
        when(leadRepository.save(any(Lead.class))).thenReturn(mockLead);
        when(commandeMapper.toDto(any(Commande.class))).thenReturn(new CommandeDTO());

        CreationCommandeRequest request = new CreationCommandeRequest();
        request.setIdempotencyKey("confirm-imported-1");
        request.setStockLocationId(10L);
        request.setItems(List.of());

        CommandeDTO result = leadService.convertirEnCommande(1L, request);

        assertNotNull(result);
        assertEquals(StatutLead.CONVERTED, mockLead.getStatutLead());
        assertEquals(StatutCommande.CONFIRMEE, mockLead.getCommande().getStatutCommande());
        assertEquals("EXT-shopify-900", mockLead.getCommande().getReference());
        assertEquals(1, mockLead.getCommande().getLignesCommande().size());
        verify(stockClient, never()).reserverStock(anyLong(), anyLong(), any());
        verifyNoInteractions(abonnementClient);
    }

    @Test
    void importExternalOrder_UsesProviderTotalAndCreatesUnassignedNewLeadWithPendingOrder() {
        when(commandeRepository.findByReferenceAndLeadEnterpriseId(anyString(), eq(7L))).thenReturn(Optional.empty());
        when(abonnementClient.currentEntitlement()).thenReturn(new AbonnementClient.Entitlement(true, 100, null));
        when(stockClient.obtenirBoutique(10L)).thenReturn(new Object());
        when(stockClient.obtenirProduit(102L)).thenReturn(StockProductDTO.builder().idProduit(102L).build());
        StockInventoryDTO inventory = new StockInventoryDTO();
        inventory.setQuantiteDisponible(20);
        when(stockClient.obtenirInventaire(10L, 102L)).thenReturn(inventory);
        when(leadRepository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandeMapper.toDto(any(Commande.class))).thenReturn(new CommandeDTO());

        ExternalOrderImportRequest request = new ExternalOrderImportRequest(
                "SHOPIFY", "external-900", "#900", 10L,
                new ExternalOrderImportRequest.Customer("Jane Doe", "jane@example.com", "+212600000001", "2 Test Street", "Rabat"),
                "PAID", "MAD", new BigDecimal("125.50"),
                List.of(new ExternalOrderImportRequest.Line(102L, 2, new BigDecimal("50.00"))));

        leadService.importExternalOrder(request);

        var leadCaptor = org.mockito.ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(leadCaptor.capture());
        Lead imported = leadCaptor.getValue();
        assertEquals(LeadSource.SHOPIFY, imported.getSource());
        assertEquals(StatutLead.NEW_LEAD, imported.getStatutLead());
        assertNull(imported.getAgentId());
        assertEquals(StatutCommande.EN_ATTENTE, imported.getCommande().getStatutCommande());
        assertEquals(StatutPaiementCommande.PAID, imported.getCommande().getStatutPaiement());
        assertEquals(125.50, imported.getCommande().getTotalPrix());
        assertEquals(1, imported.getCommande().getLignesCommande().size());
        assertTrue(imported.getCommande().getReference().startsWith("EXT-"));
        verify(stockClient).reserverStock(eq(10L), eq(102L), any(StockClient.ReservationRequest.class));
    }

    @Test
    void importExternalOrder_IsIdempotentBeforeSubscriptionAndStockCalls() {
        Commande existing = Commande.builder().reference("existing").build();
        CommandeDTO expected = new CommandeDTO();
        when(commandeRepository.findByReferenceAndLeadEnterpriseId(anyString(), eq(7L))).thenReturn(Optional.of(existing));
        when(commandeMapper.toDto(existing)).thenReturn(expected);

        ExternalOrderImportRequest request = new ExternalOrderImportRequest(
                "WOOCOMMERCE", "external-42", "42", 10L,
                new ExternalOrderImportRequest.Customer("Jane Doe", null, null, "2 Test Street", "Rabat"),
                "UNPAID", "MAD", new BigDecimal("50.00"),
                List.of(new ExternalOrderImportRequest.Line(102L, 1, new BigDecimal("50.00"))));

        assertSame(expected, leadService.importExternalOrder(request));
        verifyNoInteractions(abonnementClient, stockClient);
    }
}
