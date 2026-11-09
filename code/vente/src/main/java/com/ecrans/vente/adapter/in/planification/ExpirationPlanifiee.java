package com.ecrans.vente.adapter.in.planification;

import com.ecrans.vente.application.port.in.ExpirerBlocages;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Étape 7.5 · adapter primaire « horloge » : réveille le cas d'usage ExpirerBlocages chaque minute (B2).
 * Il ne décide rien : c'est le service de l'étape 5 qui libère les places et fait expirer les commandes.
 */
@Component
public class ExpirationPlanifiee {

    private final ExpirerBlocages expirerBlocages;

    public ExpirationPlanifiee(ExpirerBlocages expirerBlocages) {
        this.expirerBlocages = expirerBlocages;
    }

    @Scheduled(fixedRate = 60_000)
    public void chaqueMinute() {
        expirerBlocages.executer();
    }
}
