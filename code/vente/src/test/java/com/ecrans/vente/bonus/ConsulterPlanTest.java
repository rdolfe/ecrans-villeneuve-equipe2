package com.ecrans.vente.bonus;

import com.ecrans.vente.adapter.out.memoire.SeancesEnMemoire;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.PlanDeSalle;
import com.ecrans.vente.application.port.in.PlanDeSalle.PlaceDuPlan;
import com.ecrans.vente.application.service.ConsulterPlanService;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import com.ecrans.vente.domaine.seance.Seance;
import com.ecrans.vente.support.HorlogeReglable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.ecrans.vente.support.Donnees.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bonus 9.2 · consulter le plan de salle : un cas d'usage en lecture, qui laisse le domaine dire ce qui est libre. */
class ConsulterPlanTest {

    private SeancesEnMemoire seances;
    private HorlogeReglable horloge;
    private ConsulterPlanService service;

    @BeforeEach
    void preparer() {
        seances = new SeancesEnMemoire();
        horloge = HorlogeReglable.a("20:05");
        Seance seance = seanceS42();
        seance.bloquer(places("G7"), C1, le12Novembre("20:02"));      // bloquée jusqu'à 20 h 12
        seance.bloquer(places("G8"), C2, le12Novembre("20:02"));
        seance.vendre(C2, places("G8"), le12Novembre("20:03"));       // vendue
        seances.enregistrer(seance);
        service = new ConsulterPlanService(seances, horloge);
    }

    private boolean libre(PlanDeSalle plan, String place) {
        return plan.places().stream().filter(p -> p.place().equals(PlaceId.de(place))).findFirst()
                .map(PlaceDuPlan::libre).orElseThrow();
    }

    @Test
    void renvoie_toutes_les_places_de_la_salle() {
        assertThat(service.executer(S42).places()).hasSize(20);
    }

    @Test
    void les_places_bloquees_ou_vendues_ne_sont_pas_libres() {
        PlanDeSalle plan = service.executer(S42);

        assertThat(libre(plan, "G7")).isFalse();
        assertThat(libre(plan, "G8")).isFalse();
        assertThat(libre(plan, "F1")).isTrue();
    }

    @Test
    void un_blocage_expire_redevient_libre_sans_attendre_le_planificateur_B2() {
        horloge.reglerA("20:12");

        assertThat(libre(service.executer(S42), "G7")).isTrue();
    }

    @Test
    void ne_modifie_rien() {
        long version = seances.parId(S42).orElseThrow().version();

        service.executer(S42);

        assertThat(seances.parId(S42).orElseThrow().version()).isEqualTo(version);
    }

    @Test
    void apres_la_fermeture_de_la_vente_plus_rien_n_est_libre_B4() {
        horloge.reglerA("20:46");

        assertThat(service.executer(S42).places().stream().noneMatch(PlaceDuPlan::libre)).isTrue();
    }

    @Test
    void une_seance_inconnue_est_signalee() {
        assertThatThrownBy(() -> service.executer(new SeanceId("S99"))).isInstanceOf(SeanceInconnue.class);
    }
}
