package com.ecrans.vente.adapter.in.web;

import com.ecrans.vente.application.port.in.ConsulterPlan;
import com.ecrans.vente.application.port.in.PlanDeSalle;
import com.ecrans.vente.domaine.commun.SeanceId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Bonus 9.2 · GET /seances/{seance}/places : le plan de salle, pour que la page grise les places prises. */
@RestController
public class PlanController {

    private final ConsulterPlan consulterPlan;

    public PlanController(ConsulterPlan consulterPlan) {
        this.consulterPlan = consulterPlan;
    }

    @GetMapping("/seances/{seance}/places")
    public PlanReponse plan(@PathVariable("seance") String seance) {
        PlanDeSalle plan = consulterPlan.executer(new SeanceId(seance));
        return new PlanReponse(plan.seance().valeur(), plan.places().stream()
                .map(p -> new PlanReponse.PlaceReponse(p.place().toString(), p.libre()))
                .toList());
    }

    public record PlanReponse(String seance, List<PlaceReponse> places) {
        public record PlaceReponse(String place, boolean libre) {
        }
    }
}
