package org.example.paiment_service.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.example.paiment_service.entity.Facture;
import org.example.paiment_service.entity.TransactionPaiement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name:invoices-erp}")
    private String bucketName;

    public String genererEtStockerFacturePdf(Facture facture, TransactionPaiement transaction) {
        try {
            // 1. Génération du PDF en MÉMOIRE (ByteArrayOutputStream)
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, out);

            document.open();
// En-tête du document
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, Color.BLUE);
            Paragraph title = new Paragraph("FACTURE " + facture.getNumeroFactureUnique(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Informations Métadonnées
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.BLACK);

            document.add(new Paragraph("Date d'émission : " +
                    facture.getDateEmission().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), normalFont));
            document.add(new Paragraph("Référence Commande / Source : #" + transaction.getReferenceSourceId(), normalFont));
            document.add(new Paragraph("Contexte : " + transaction.getTypeContexte(), normalFont));
            document.add(new Paragraph("Mode de Règlement : " + transaction.getMode(), normalFont));
            document.add(new Paragraph(" ", normalFont)); // Espace

            // Tableau Récapitulatif Financier
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            // En-tête Tableau
            PdfPCell c1 = new PdfPCell(new Phrase("Description", boldFont));
            c1.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(c1);

            PdfPCell c2 = new PdfPCell(new Phrase("Montant Total", boldFont));
            c2.setBackgroundColor(Color.LIGHT_GRAY);
            table.addCell(c2);

            // Contenu
            table.addCell(new Phrase("Règlement transaction contextuel (" + transaction.getTypeContexte() + ")", normalFont));
            table.addCell(new Phrase(String.format("%.2f DH", transaction.getMontant()), normalFont));

            document.add(table);

            // Pied de page / Statut
            Paragraph status = new Paragraph("Statut du Paiement : " + transaction.getStatut(), boldFont);
            status.setSpacingBefore(15);
            document.add(status);            document.add(new Paragraph("FACTURE " + facture.getNumeroFactureUnique()));
            document.close();

            byte[] pdfBytes = out.toByteArray();
            String objectName = "factures/" + facture.getNumeroFactureUnique() + ".pdf";

            // 2. Upload direct du flux binaire dans MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(pdfBytes), pdfBytes.length, -1)
                            .contentType("application/pdf")
                            .build()
            );

            // 3. On retourne l'identifiant de l'objet MinIO au lieu du chemin disque local
            return objectName;

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'upload de la facture sur MinIO", e);
        }
    }
    public String obtenirUrlTelechargementTemporaire(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(15, TimeUnit.MINUTES) // URL valide 15 minutes
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération de l'URL MinIO", e);
        }
    }
}