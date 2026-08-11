package org.example.delivery_service.strategy;

import lombok.extern.slf4j.Slf4j;
import org.example.delivery_service.entity.Livraison;
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
        if (livraison.getLivreurId() == null) {
            throw new IllegalArgumentException("An internal courier must be assigned");
        }
        log.info("Delivery {} assigned to internal courier {} and awaiting acceptance",
                livraison.getCodeSuiviTracking(), livraison.getLivreurId());
    }

    @Override
    public String recupererStatutActuel(String trackingNum) {
        throw new UnsupportedOperationException("Internal courier status is updated by the assigned courier");
    }
}
