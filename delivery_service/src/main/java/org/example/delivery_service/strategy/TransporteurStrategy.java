package org.example.delivery_service.strategy;

import org.example.delivery_service.entity.Livraison;
import org.example.delivery_service.entity.TypeTransporteur;

public interface TransporteurStrategy {
    TypeTransporteur getType();
    void executerLivraison(Livraison livraison);
    String recupererStatutActuel(String trackingNum);
}