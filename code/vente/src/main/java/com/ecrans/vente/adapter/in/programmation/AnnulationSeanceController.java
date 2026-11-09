package com.ecrans.vente.adapter.in.programmation;

import com.ecrans.vente.application.port.in.AnnulerSeance;
import com.ecrans.vente.domaine.commun.SeanceId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bonus 9.4 · simule l'événement SeanceAnnulee de la Programmation, faute de bus de messages.
 * En production : un écouteur du broker, branché sur le même port AnnulerSeance.
 */
@RestController
public class AnnulationSeanceController {

    private final AnnulerSeance annulerSeance;

    public AnnulationSeanceController(AnnulerSeance annulerSeance) {
        this.annulerSeance = annulerSeance;
    }

    @PostMapping("/programmation/seances/{seance}/annulation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void seanceAnnulee(@PathVariable("seance") String seance) {
        annulerSeance.executer(new SeanceId(seance));
    }
}
