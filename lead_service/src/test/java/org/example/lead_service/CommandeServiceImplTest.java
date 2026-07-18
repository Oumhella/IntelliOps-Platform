package org.example.lead_service;

import org.example.lead_service.entity.Commande;
import org.example.lead_service.entity.StatutCommande;
import org.example.lead_service.dto.CommandeDTO;
import org.example.lead_service.mapper.CommandeMapper;
import org.example.lead_service.repository.CommandeRepository;
import org.example.lead_service.service.CommandeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandeServiceImplTest {

    @Mock
    private CommandeRepository commandeRepository;
    @Mock
    private CommandeMapper commandeMapper;

    @InjectMocks
    private CommandeServiceImpl commandeService;

    @Test
    void ajouterProduitACommande_DevraitAjouterLigneEtRecalculerTotal() {
        // Arrange
        Commande commande = Commande.builder()
                .idCommande(1L)
                .reference("CMD-123")
                .statutCommande(StatutCommande.EN_ATTENTE)
                .lignesCommande(new ArrayList<>())
                .totalPrix(0.0)
                .build();

        when(commandeRepository.findById(1L)).thenReturn(Optional.of(commande));
        when(commandeRepository.save(any(Commande.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandeMapper.toDto(any(Commande.class))).thenReturn(new CommandeDTO());

        // Act & Assert 1 : Premier produit (quantité 2 x 150.0 = 300.0)
        commandeService.ajouterProduitACommande(1L, 101L, 2, 150.0);
        assertEquals(300.0, commande.getTotalPrix());
        assertEquals(1, commande.getLignesCommande().size());

        // Act & Assert 2 : Deuxième produit (quantité 1 x 50.0 = +50.0 -> total 350.0)
        commandeService.ajouterProduitACommande(1L, 102L, 1, 50.0);
        assertEquals(350.0, commande.getTotalPrix());
        assertEquals(2, commande.getLignesCommande().size());

        verify(commandeRepository, times(2)).save(commande);
    }
}