package org.example.delivery_service.service;

import org.example.common.exception.ResourceNotFoundException;
import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.UpdateStatutRequest;
import org.example.delivery_service.dto.response.LivraisonResponse;
import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
import org.example.delivery_service.mapper.LivraisonMapper;
import org.example.delivery_service.repository.LivraisonRepository;
import org.example.delivery_service.service.LivraisonService;
import org.example.delivery_service.strategy.TransporteurStrategy;
import org.example.delivery_service.strategy.TransporteurStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LivraisonServiceImpl implements LivraisonService {

    private final LivraisonRepository livraisonRepository;
    private final TransporteurStrategyFactory strategyFactory;
    private final LivraisonMapper livraisonMapper;

    @Override
    @Transactional
    public LivraisonResponse expedierLivraison(ExpedierLivraisonRequest request) {
        if (livraisonRepository.existsByReferenceCommandeId(request.getReferenceCommandeId())) {
            throw new IllegalArgumentException("Shipment already exists for order ID: " + request.getReferenceCommandeId());
        }

        Livraison livraison = Livraison.builder()
                .referenceCommandeId(request.getReferenceCommandeId())
                .codeSuiviTracking("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .statutLivraison(StatutLivraison.EN_PREPARATION)
                .typeTransporteur(request.getTypeTransporteur())
                .nomSociete(request.getNomSociete())
                .endpointApiUrl(request.getEndpointApiUrl())
                .externalLivreurId(request.getExternalLivreurId())
                .montantACollecterCoD(request.getMontantACollecterCoD())
                .shippingDate(LocalDateTime.now())
                .build();

        // Strategy Execution
        TransporteurStrategy strategy = strategyFactory.getStrategy(request.getTypeTransporteur());
        strategy.executerLivraison(livraison);

        Livraison saved = livraisonRepository.save(livraison);
        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getByTrackingNumber(String trackingNum) {
        Livraison livraison = livraisonRepository.findByCodeSuiviTracking(trackingNum)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found with tracking code: " + trackingNum));
        return livraisonMapper.toResponse(livraison);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getByCommandeId(Long commandeId) {
        Livraison livraison = livraisonRepository.findByReferenceCommandeId(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found for order ID: " + commandeId));
        return livraisonMapper.toResponse(livraison);
    }

    @Override
    @Transactional
    public LivraisonResponse mettreAJourStatut(Long id, UpdateStatutRequest request) {
        Livraison livraison = livraisonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found with ID: " + id));

        livraison.mettreAJourStatut(request.getStatut());
        Livraison saved = livraisonRepository.save(livraison);
        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LivraisonResponse confirmerReception(Long id) {
        Livraison livraison = livraisonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found with ID: " + id));

        livraison.mettreAJourStatut(StatutLivraison.LIVREE);
        Livraison saved = livraisonRepository.save(livraison);
        return livraisonMapper.toResponse(saved);
    }
}