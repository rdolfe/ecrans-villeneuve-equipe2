package com.ecrans.vente.config;

import com.ecrans.vente.adapter.in.payvia.WebhookPayVia;
import com.ecrans.vente.adapter.in.sortiesflash.AclSortiesFlash;
import com.ecrans.vente.adapter.in.sortiesflash.CorrespondanceSeances;
import com.ecrans.vente.application.port.in.AnnulerSeance;
import com.ecrans.vente.application.port.in.ConfirmerPaiement;
import com.ecrans.vente.application.port.in.ConsulterPlan;
import com.ecrans.vente.application.port.in.EnregistrerVentePartenaire;
import com.ecrans.vente.application.port.in.ExpirerBlocages;
import com.ecrans.vente.application.port.in.ReserverDesPlaces;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import com.ecrans.vente.domaine.seance.Seance;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Spring : publie comme beans les ports de l'hexagone assemblé par ApplicationVente.
 * Les contrôleurs reçoivent des PORTS dans leur constructeur, jamais des services ni des adapters.
 * Le domaine et l'application ne contiennent aucune annotation Spring.
 */
@Configuration
public class VenteConfiguration {

    static final SeanceId SEANCE_DE_DEMONSTRATION = new SeanceId("S42");

    @Bean
    Clock horloge() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ApplicationVente applicationVente(Clock horloge) {
        return new ApplicationVente(horloge);
    }

    @Bean
    ReserverDesPlaces reserverDesPlaces(ApplicationVente app) {
        return app.reserverDesPlaces;
    }

    @Bean
    ConfirmerPaiement confirmerPaiement(ApplicationVente app) {
        return app.confirmerPaiement;
    }

    @Bean
    ExpirerBlocages expirerBlocages(ApplicationVente app) {
        return app.expirerBlocages;
    }

    @Bean
    WebhookPayVia webhookPayVia(ApplicationVente app) {
        return app.webhookPayVia;
    }

    /** Étape 8 : le cas d'usage des ventes partenaires. */
    @Bean
    EnregistrerVentePartenaire enregistrerVentePartenaire(ApplicationVente app) {
        return app.enregistrerVentePartenaire;
    }

    /** Étape 8 : la table identifiant de séance SortiesFlash → SeanceId (en production, alimentée par la Programmation). */
    @Bean
    CorrespondanceSeances correspondanceSeances() {
        return new CorrespondanceSeances();
    }

    @Bean
    AclSortiesFlash aclSortiesFlash(CorrespondanceSeances correspondance) {
        return new AclSortiesFlash(correspondance);
    }

    /** Bonus 9.2 */
    @Bean
    ConsulterPlan consulterPlan(ApplicationVente app) {
        return app.consulterPlan;
    }

    /** Bonus 9.4 */
    @Bean
    AnnulerSeance annulerSeance(ApplicationVente app) {
        return app.annulerSeance;
    }

    /**
     * La Programmation n'existe pas encore : on publie à la main la séance S42,
     * demain à 20 h 30, salle 3 (rangs A à H, fauteuils 1 à 12), et sa correspondance chez SortiesFlash.
     */
    @Bean
    CommandLineRunner seanceDeDemonstration(ApplicationVente app, CorrespondanceSeances correspondance, Clock horloge) {
        return args -> {
            List<PlaceId> plan = new ArrayList<>();
            for (char rang = 'A'; rang <= 'H'; rang++) {
                for (int n = 1; n <= 12; n++) plan.add(new PlaceId(rang, n));
            }
            LocalDate demain = LocalDate.now(horloge).plusDays(1);
            app.seances.enregistrer(Seance.ouverte(SEANCE_DE_DEMONSTRATION, demain.atTime(20, 30), plan));
            correspondance.associer("VIL-" + demain.format(DateTimeFormatter.BASIC_ISO_DATE) + "-2030-S3",
                    SEANCE_DE_DEMONSTRATION);
        };
    }
}
