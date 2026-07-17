package org.example.lead_service;

import org.example.lead_service.dto.CommandeDTO;
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

        mockLead = Lead.builder()
                .idLead(1L)
                .statutLead(StatutLead.NEW_LEAD)
                .ordrePriorite(OrdrePriorite.HIGH)
                .infosClient(mockCoords)
                .boutiqueId(10L)
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
        when(leadMapper.toDto(any(NoteInteraction.class))).thenReturn(new NoteInteractionDTO());
        // Act
        leadService.enregistrerInteraction(
                1L,
                TypeInteraction.APPEL_TEL,
                "Client intéressé, rappel planifié",
                StatutLead.SCHEDULED_RECALL
        );

        // Assert
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
        when(commandeRepository.save(any(Commande.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commandeMapper.toDto(any(Commande.class))).thenReturn(new CommandeDTO());

        // Act
        CommandeDTO result = leadService.convertirEnCommande(1L);

        // Assert
        assertNotNull(result);
        assertEquals(StatutLead.CONVERTED, mockLead.getStatutLead());
        assertNotNull(mockLead.getCommande());
        verify(commandeRepository, times(1)).save(any(Commande.class));
    }
}