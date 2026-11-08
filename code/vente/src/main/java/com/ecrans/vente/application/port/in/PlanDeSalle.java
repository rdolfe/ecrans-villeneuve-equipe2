package com.ecrans.vente.application.port.in;

import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;

import java.util.List;

/** Ce que renvoie ConsulterPlan : chaque fauteuil de la séance et s'il peut être choisi maintenant. */
public record PlanDeSalle(SeanceId seance, List<PlaceDuPlan> places) {

    public PlanDeSalle {
        places = List.copyOf(places);
    }

    public record PlaceDuPlan(PlaceId place, boolean libre) {
    }
}
