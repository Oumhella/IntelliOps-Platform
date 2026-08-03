package org.example.delivery_service.service;

import org.example.common.exception.ResourceNotFoundException;
import org.example.delivery_service.dto.request.ExpedierLivraisonRequest;
import org.example.delivery_service.dto.request.UpdateStatutRequest;
import org.example.delivery_service.dto.response.LivraisonResponse;
import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
import org.example.delivery_service.event.LivraisonEventProducer;
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
import org.example.common.dto.PageResponse;
import org.example.common.security.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class LivraisonServiceImpl implements LivraisonService {

    private final LivraisonRepository livraisonRepository;
    private final TransporteurStrategyFactory strategyFactory;
    private final LivraisonMapper livraisonMapper;
    private final LivraisonEventProducer eventProducer;

    @Override
    @Transactional
    public LivraisonResponse expedierLivraison(ExpedierLivraisonRequest request) {
        Long enterpriseId = TenantContext.requireEnterpriseId();
        if (livraisonRepository.existsByReferenceCommandeIdAndEnterpriseId(
                request.getReferenceCommandeId(), enterpriseId)) {
            throw new IllegalArgumentException("Shipment already exists for order ID: " + request.getReferenceCommandeId());
        }

        Livraison livraison = Livraison.builder()
                .enterpriseId(enterpriseId)
                .referenceCommandeId(request.getReferenceCommandeId())
                .codeSuiviTracking("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .statutLivraison(StatutLivraison.EN_PREPARATION)
                .typeTransporteur(request.getTypeTransporteur())
                .nomSociete(request.getNomSociete())
                .endpointApiUrl(request.getEndpointApiUrl())
                .externalLivreurId(request.getExternalLivreurId())
                .montantACollecterCoD(request.getMontantACollecterCoD())
                .clientEmail(request.getClientEmail())
                .shippingDate(LocalDateTime.now())
                .build();

        // Strategy Execution
        TransporteurStrategy strategy = strategyFactory.getStrategy(request.getTypeTransporteur());
        strategy.executerLivraison(livraison);

        Livraison saved = livraisonRepository.save(livraison);
        String emailSubject = "Expédition de votre commande #" + saved.getReferenceCommandeId();
        String emailBody = String.format(
                "Bonjour, votre commande #%d a été expédiée via %s ! Code de suivi: %s",
                saved.getReferenceCommandeId(),
                saved.getTypeTransporteur(),
                saved.getCodeSuiviTracking()
        );

        if (saved.getClientEmail() != null && !saved.getClientEmail().isBlank()) {
            eventProducer.sendNotificationEvent(saved.getClientEmail(), emailSubject, emailBody);
        }

        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getById(Long id) {
        return livraisonMapper.toResponse(findDelivery(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LivraisonResponse> search(
            StatutLivraison statut, TypeTransporteur transporteur, int page, int size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "shippingDate"));
        return PageResponse.from(
                livraisonRepository.search(
                        TenantContext.requireEnterpriseId(), statut, transporteur, pageable),
                livraisonMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getByTrackingNumber(String trackingNum) {
        Livraison livraison = livraisonRepository.findByCodeSuiviTrackingAndEnterpriseId(
                        trackingNum, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found with tracking code: " + trackingNum));
        return livraisonMapper.toResponse(livraison);
    }

    @Override
    @Transactional(readOnly = true)
    public LivraisonResponse getByCommandeId(Long commandeId) {
        Livraison livraison = livraisonRepository.findByReferenceCommandeIdAndEnterpriseId(
                        commandeId, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found for order ID: " + commandeId));
        return livraisonMapper.toResponse(livraison);
    }

    @Override
    @Transactional
    public LivraisonResponse mettreAJourStatut(Long id, UpdateStatutRequest request) {
        Livraison livraison = findDelivery(id);

        livraison.mettreAJourStatut(request.getStatut());
        Livraison saved = livraisonRepository.save(livraison);
        return livraisonMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LivraisonResponse confirmerReception(Long id) {
        Livraison livraison = findDelivery(id);

        livraison.mettreAJourStatut(StatutLivraison.LIVREE);
        Livraison saved = livraisonRepository.save(livraison);

        if (saved.getClientEmail() != null && !saved.getClientEmail().isBlank()) {
            eventProducer.sendNotificationEvent(
                    saved.getClientEmail(),
                    "Commande Livrée !",
                    "Votre commande #" + saved.getReferenceCommandeId() + " a bien été livrée. Merci !"
            );
        }

        return livraisonMapper.toResponse(saved);
    }

    private Livraison findDelivery(Long id) {
        return livraisonRepository.findByIdLivraisonAndEnterpriseId(id, TenantContext.requireEnterpriseId())
                .orElseThrow(() -> new ResourceNotFoundException("Livraison not found with ID: " + id));
    }
}
