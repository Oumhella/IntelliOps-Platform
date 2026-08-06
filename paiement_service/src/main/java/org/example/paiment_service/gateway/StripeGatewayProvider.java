package org.example.paiment_service.gateway;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.example.paiment_service.entity.ModePaiement;
import org.example.paiment_service.entity.StatutPaiement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StripeGatewayProvider implements PaymentGatewayProvider {

    private final String currency;

    public StripeGatewayProvider(
            @Value("${stripe.api-key:}") String apiKey,
            @Value("${stripe.currency:mad}") String currency) {
        Stripe.apiKey = apiKey;
        this.currency = currency;
    }

    @Override
    public ModePaiement getSupportedMode() {
        return ModePaiement.CREDIT_CARD;
    }

    @Override
    public PreparedPayment preparerPaiement(BigDecimal montant, String idempotencyKey) {
        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(toMinorUnits(montant))
                    .setCurrency(currency)
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build())
                    .build();
            RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
            PaymentIntent intent = PaymentIntent.create(params, options);
            return toPreparedPayment(intent);
        } catch (StripeException exception) {
            throw new IllegalStateException("Stripe could not prepare the secure checkout: " + exception.getMessage(), exception);
        }
    }

    @Override
    public PreparedPayment recupererPaiementPrepare(String providerTransactionId) {
        try {
            return toPreparedPayment(PaymentIntent.retrieve(providerTransactionId));
        } catch (StripeException exception) {
            throw new IllegalStateException("Stripe could not restore the secure checkout: " + exception.getMessage(), exception);
        }
    }

    @Override
    public PaymentResult verifierPaiement(String providerTransactionId) {
        try {
            return toPaymentResult(PaymentIntent.retrieve(providerTransactionId));
        } catch (StripeException exception) {
            throw new IllegalStateException("Stripe could not verify the payment: " + exception.getMessage(), exception);
        }
    }

    @Override
    public boolean traiterRemboursement(String providerTransactionId, BigDecimal montant, String idempotencyKey) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(providerTransactionId)
                    .setAmount(toMinorUnits(montant))
                    .build();
            RequestOptions options = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();
            Refund refund = Refund.create(params, options);
            return "succeeded".equalsIgnoreCase(refund.getStatus());
        } catch (StripeException exception) {
            throw new IllegalStateException("Stripe rejected the refund: " + exception.getMessage(), exception);
        }
    }

    private long toMinorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    }

    private BigDecimal fromMinorUnits(Long amount) {
        if (amount == null) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(amount).movePointLeft(2).setScale(2);
    }

    private PreparedPayment toPreparedPayment(PaymentIntent intent) {
        return new PreparedPayment(intent.getId(), intent.getClientSecret(), mapStatus(intent.getStatus()),
                fromMinorUnits(intent.getAmount()), intent.getCurrency());
    }

    private PaymentResult toPaymentResult(PaymentIntent intent) {
        return new PaymentResult(intent.getId(), mapStatus(intent.getStatus()),
                fromMinorUnits(intent.getAmount()), intent.getCurrency());
    }

    private StatutPaiement mapStatus(String stripeStatus) {
        if ("succeeded".equalsIgnoreCase(stripeStatus)) return StatutPaiement.COMPLETED;
        if ("requires_capture".equalsIgnoreCase(stripeStatus)) return StatutPaiement.AUTHORIZED;
        if ("canceled".equalsIgnoreCase(stripeStatus)) return StatutPaiement.CANCELLED;
        if ("processing".equalsIgnoreCase(stripeStatus)) return StatutPaiement.PENDING;
        if (stripeStatus != null && stripeStatus.startsWith("requires_")) return StatutPaiement.PENDING;
        return StatutPaiement.FAILED;
    }
}
