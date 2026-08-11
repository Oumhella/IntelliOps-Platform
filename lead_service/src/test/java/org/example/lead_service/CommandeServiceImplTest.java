package org.example.lead_service;

import org.example.lead_service.entity.Commande;
import org.example.lead_service.entity.StatutCommande;
import org.example.lead_service.entity.Lead;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.mapper.CommandeMapper;
import org.example.lead_service.repository.CommandeRepository;
import org.example.lead_service.service.CommandeServiceImpl;
import org.example.lead_service.client.StockClient;
import org.example.lead_service.dto.AddOrderLineRequest;
import org.example.lead_service.dto.StockInventoryDTO;
import org.example.lead_service.dto.StockProductDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.example.common.security.TenantContext;

@ExtendWith(MockitoExtension.class)
class CommandeServiceImplTest {

    @Mock
    private CommandeRepository commandeRepository;
    @Mock
    private CommandeMapper commandeMapper;
    @Mock
    private StockClient stockClient;

    @InjectMocks
    private CommandeServiceImpl commandeService;

    @BeforeEach
    void setUp() {
        TenantContext.setEnterpriseId(7L);
        TenantContext.setUserId(42L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ajouterProduitACommande_DevraitAjouterLigneEtRecalculerTotal() {
        // Arrange
        Commande commande = Commande.builder()
                .idCommande(1L)
                .reference("CMD-123")
                .statutCommande(StatutCommande.EN_ATTENTE)
                .lignesCommande(new ArrayList<>())
                .totalPrix(0.0)
                .stockLocationId(10L)
                .stockReservationReference("CMD-TEST")
                .lead(Lead.builder().agentId(42L).enterpriseId(7L).build())
                .build();

        when(commandeRepository.findByIdCommandeAndLeadEnterpriseId(1L, 7L))
                .thenReturn(Optional.of(commande));
        when(commandeRepository.save(any(Commande.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandeMapper.toDto(any(Commande.class))).thenReturn(new CommandeDTO());
        when(stockClient.obtenirProduit(anyLong())).thenAnswer(invocation -> StockProductDTO.builder()
                .idProduit(invocation.getArgument(0))
                .prixVente(invocation.<Long>getArgument(0).equals(101L) ? 150.0 : 50.0)
                .build());
        StockInventoryDTO inventory = new StockInventoryDTO();
        inventory.setQuantiteDisponible(20);
        when(stockClient.obtenirInventaire(eq(10L), anyLong())).thenReturn(inventory);

        // Act & Assert 1 : Premier produit (quantité 2 x 150.0 = 300.0)
        commandeService.ajouterProduitACommande(1L, new AddOrderLineRequest(101L, 2));
        assertEquals(300.0, commande.getTotalPrix());
        assertEquals(1, commande.getLignesCommande().size());

        // Act & Assert 2 : Deuxième produit (quantité 1 x 50.0 = +50.0 -> total 350.0)
        commandeService.ajouterProduitACommande(1L, new AddOrderLineRequest(102L, 1));
        assertEquals(350.0, commande.getTotalPrix());
        assertEquals(2, commande.getLignesCommande().size());

        verify(commandeRepository, times(2)).save(commande);
    }

    @Test
    void changerStatutCommande_RejectsSkippedLifecycleStep() {
        Commande commande = Commande.builder()
                .idCommande(1L)
                .statutCommande(StatutCommande.EN_ATTENTE)
                .lead(Lead.builder().agentId(42L).enterpriseId(7L).build())
                .build();
        when(commandeRepository.findByIdCommandeAndLeadEnterpriseId(1L, 7L))
                .thenReturn(Optional.of(commande));

        assertThrows(IllegalStateException.class,
                () -> commandeService.changerStatutCommande(1L, StatutCommande.EXPEDIEE));
        verify(commandeRepository, never()).save(any());
    }
}
