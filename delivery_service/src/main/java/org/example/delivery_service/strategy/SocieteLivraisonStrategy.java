package org.example.delivery_service.strategy;
import lombok.extern.slf4j.Slf4j;
import org.example.delivery_service.entity.Livraison;
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
        if (livraison.getNomSociete() == null || livraison.getNomSociete().isBlank()) {
            throw new IllegalArgumentException("External delivery company name is required");
        }
        log.info("External delivery {} recorded for company '{}' in manual-dispatch mode",
                livraison.getCodeSuiviTracking(), livraison.getNomSociete());
    }

    @Override
    public String recupererStatutActuel(String trackingNum) {
        throw new UnsupportedOperationException("No automated external carrier adapter is configured");
    }
}
