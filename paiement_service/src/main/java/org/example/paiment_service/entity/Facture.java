package org.example.paiment_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "factures")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Facture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numeroFactureUnique;

    private String cheminFichierPdf;

    private LocalDateTime dateEmission;

    @OneToOne(mappedBy = "facture")
    @ToString.Exclude
    private TransactionPaiement transactionPaiement;

    @PrePersist
    protected void onCreate() {
        this.dateEmission = LocalDateTime.now();
    }

    public void genererPdf() {
        // Logique de génération PDF (ex: via iText ou JasperReports)
        this.cheminFichierPdf = "/storage/invoices/" + this.numeroFactureUnique + ".pdf";
    }

    public void envoyerParEmail() {
        // Logique d'envoi de mail avec la facture en pièce jointe
    }
}