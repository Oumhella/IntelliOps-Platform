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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private LeadServiceImpl leadService;

    private Lead mockLead;
    private CoordonneesClient mockCoords;

    @BeforeEach
    void setUp() {
        mockCoords = CoordonneesClient.builder()
                .nomComplet("John Doe")
                .telephone("+212600000000")
                .ville("Tétouan")
                .build();

        // Utilisation d'un ArrayList modifiable pour l'historique afin d'éviter les UnsupportedOperationException
        mockLead = Lead.builder()
                .idLead(1L)
                .statutLead(StatutLead.NEW_LEAD)
                .ordrePriorite(OrdrePriorite.HIGH)
                .infosClient(mockCoords)
                .boutiqueId(10L)
                .historiqueInteractions(new ArrayList<>())
                .build();
    }

    @Test
    void assignerAgent_DevraitMettreAJourAgentId() {
        // Arrange
        when(leadRepository.findById(1L)).thenReturn(Optional.of(mockLead));
        when(leadRepository.save(any(Lead.class))).thenReturn(mockLead);
        when(leadMapper.toDto(any(Lead.class))).thenReturn(new LeadDTO());

        // Act
        leadService.assignerAgent(1L, 42L);

        // Assert
        assertEquals(42L, mockLead.getAgentId());
        verify(leadRepository, times(1)).save(mockLead);
    }

    @Test
    void enregistrerInteraction_DevraitMettreAJourStatutEtAjouterNote() {
        // Arrange
        when(leadRepository.findById(1L)).thenReturn(Optional.of(mockLead));

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
        when(leadRepository.findById(1L)).thenReturn(Optional.of(mockLead));
        when(leadRepository.save(any(Lead.class))).thenReturn(mockLead);
        when(commandeMapper.toDto(any(Commande.class))).thenReturn(new CommandeDTO());

        // Préparation du DTO de requête avec les articles simulés
        CreationCommandeRequest request = new CreationCommandeRequest();
        request.setTotalAmount(1500.00);

        CreationCommandeRequest.ItemRequest item1 = new CreationCommandeRequest.ItemRequest();
        item1.setProductId(102L);
        item1.setQuantity(2);
        item1.setUnitPrice(500.00);

        CreationCommandeRequest.ItemRequest item2 = new CreationCommandeRequest.ItemRequest();
        item2.setProductId(205L);
        item2.setQuantity(1);
        item2.setUnitPrice(500.00);

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
        assertEquals(StatutCommande.EN_ATTENTE, commandeGeneree.getStatutCommande());

        // On s'assure que le lead entier a été sauvegardé (ce qui propage l'enregistrement de la commande)
        verify(leadRepository, times(1)).save(mockLead);
    }
}