package org.example.abonnement_service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.abonnement_service.entity.Abonnement;
import org.example.abonnement_service.entity.StatutAbonnement;
import org.example.abonnement_service.event.AbonnementEventProducer;
import org.example.abonnement_service.repository.AbonnementRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpirationScheduler {

    private final AbonnementRepository abonnementRepository;
    private final AbonnementEventProducer eventProducer;

    /**
     * Checks active subscriptions daily at 01:00 AM for expiration.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void checkExpirations() {
        log.info("Running automated subscription expiration check...");
        List<Abonnement> activeSubscriptions = abonnementRepository.findAll().stream()
                .filter(a -> a.getStatut() == StatutAbonnement.ACTIF)
                .toList();

        LocalDate today = LocalDate.now();
        for (Abonnement sub : activeSubscriptions) {
            if (today.isAfter(sub.getDateFin())) {
                sub.setStatut(StatutAbonnement.EXPIRE);
                abonnementRepository.save(sub);
                log.info("Subscription ID {} has expired.", sub.getIdAbonnement());

                eventProducer.sendSubscriptionNotification(
                        sub.getEnterpriseId(),
                        "user" + sub.getUserId() + "@intelliops.local",
                        "Alerte : Votre abonnement a expiré",
                        "Votre abonnement au plan " + sub.getPlanAbonnement().getNomPlan() + " a expiré le " + sub.getDateFin() + ". Veuillez le renouveler."
                );
            }
        }
    }
}
