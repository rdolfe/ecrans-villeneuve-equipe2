package com.ecrans.vente.application.service;

import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.ConsulterPlan;
import com.ecrans.vente.application.port.in.PlanDeSalle;
import com.ecrans.vente.application.port.in.PlanDeSalle.PlaceDuPlan;
import com.ecrans.vente.application.port.out.Seances;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import com.ecrans.vente.domaine.seance.Seance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Bonus 9.2 · cas d'usage en lecture : rien n'est modifié, rien n'est enregistré.
 * C'est le domaine qui dit si une place est libre : un blocage expiré est libre, même avant le passage du planificateur.
 */
public class ConsulterPlanService implements ConsulterPlan {

    private final Seances seances;
    private final Clock horloge;

    public ConsulterPlanService(Seances seances, Clock horloge) {
        this.seances = seances;
        this.horloge = horloge;
    }

    @Override
    public PlanDeSalle executer(SeanceId id) {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        Seance seance = seances.parId(id).orElseThrow(() -> new SeanceInconnue(id));
        boolean venteOuverte = seance.venteOuverteA(maintenant);
        Set<PlaceId> libres = seance.placesLibresA(maintenant);
        List<PlaceDuPlan> plan = seance.places().keySet().stream()
                .map(place -> new PlaceDuPlan(place, venteOuverte && libres.contains(place)))
                .toList();
        return new PlanDeSalle(id, plan);
    }
}
