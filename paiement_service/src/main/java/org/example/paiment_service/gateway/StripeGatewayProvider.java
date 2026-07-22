package org.example.paiment_service.gateway;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import org.example.paiment_service.entity.ModePaiement;
import org.example.paiment_service.gateway.PaymentGatewayProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StripeGatewayProvider implements PaymentGatewayProvider {

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${stripe.currency:usd}")
    private String currency;

    @PostConstruct
    public void init() {
        // Initialisation globale de la clé API Stripe au démarrage du bean
        Stripe.apiKey = stripeApiKey;
    }

    @Override
    public ModePaiement getSupportedMode() {
        return ModePaiement.CREDIT_CARD;
    }

    @Override
    public boolean traiterPaiement(double montant, String tokenCarte) {
        try {
            // Stripe gère les montants en centimes (ex: 10.00$ -> 1000)
            long amountInCents = Math.round(montant * 100);

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setPaymentMethod(tokenCarte) // Token envoyé par le frontend (Stripe Elements)
                    .setConfirm(true) // Confirme et débite la carte immédiatement
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    )
                    .build();

            PaymentIntent intent = PaymentIntent.create(params);
            return "succeeded".equalsIgnoreCase(intent.getStatus());

        } catch (StripeException e) {
            // En production, loguer l'erreur avec un Logger SLF4J
            System.err.println("Échec du paiement Stripe : " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean traiterRemboursement(String paymentIntentId, double montant) {
        try {
            long amountInCents = Math.round(montant * 100);

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setAmount(amountInCents)
                    .build();

            Refund refund = Refund.create(params);
            return "succeeded".equalsIgnoreCase(refund.getStatus());

        } catch (StripeException e) {
            System.err.println("Échec du remboursement Stripe : " + e.getMessage());
            return false;
        }
    }
}