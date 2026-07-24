package org.example.paiment_service.entity;

public enum StatutPaiement {
    PENDING,               // Paiement créé mais non finalisé
    AWAITING_COLLECTION,   // Pour le Cash On Delivery
    AUTHORIZED,            // Carte validée mais fonds non encore capturés
    COMPLETED,             // Encaissé avec succès
    FAILED,                // Échec (fonds insuffisants, rejet)
    CANCELLED,             // Annulé avant capture
    REFUNDED,              // Remboursé entièrement
    PARTIALLY_REFUNDED     // Remboursé partiellement
}