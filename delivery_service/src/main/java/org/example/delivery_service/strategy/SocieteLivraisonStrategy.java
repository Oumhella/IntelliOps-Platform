package org.example.delivery_service.strategy;
import lombok.extern.slf4j.Slf4j;
import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.StatutLivraison;
import org.example.delivery_service.entity.TypeTransporteur;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SocieteLivraisonStrategy implements TransporteurStrategy{

    @Override
    public TypeTransporteur getType() {
        return TypeTransporteur.SOCIETE_LIVRAISON;
    }

    @Override
    public void executerLivraison(Livraison livraison) {
        log.info("Sending payload to external shipping carrier '{}' via endpoint: {}",
                livraison.getNomSociete(), livraison.getEndpointApiUrl());

        // HTTP REST Call or SDK integration to DHL/Aramex goes here
        livraison.mettreAJourStatut(StatutLivraison.CHEZ_TRANSPORTEUR);
    }

    @Override
    public String recupererStatutActuel(String trackingNum) {
        // Query Carrier Remote API
        return StatutLivraison.CHEZ_TRANSPORTEUR.name();
    }
}
