package org.example.delivery_service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LivreurStrategy implements TransporteurStrategy {

    @Override
    public TypeTransporteur getType() {
        return TypeTransporteur.LIVREUR_INTERNE;
    }

    @Override
    public void executerLivraison(Livraison livraison) {
        log.info("Pushing task for delivery #{} to Internal Driver App ID: {}",
                livraison.getIdLivraison(), livraison.getExternalLivreurId());

        // Dispatch via WebSocket / Internal Queue to Driver Mobile App
        livraison.mettreAJourStatut(StatutLivraison.EN_COURS);
    }

    @Override
    public String recupererStatutActuel(String trackingNum) {
        return StatutLivraison.EN_COURS.name();
    }
}