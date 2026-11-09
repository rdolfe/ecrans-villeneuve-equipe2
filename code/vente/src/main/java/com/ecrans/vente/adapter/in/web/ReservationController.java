package com.ecrans.vente.adapter.in.web;

import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.DemandeReservation;
import com.ecrans.vente.application.port.in.ReserverDesPlaces;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adapter primaire REST (étape 7) : traduit le JSON en DemandeReservation, appelle le PORT, traduit la réponse.
 * Aucune règle ici : les refus viennent du domaine et sont traduits en HTTP par GestionErreurs.
 */
@RestController
public class ReservationController {

    private final ReserverDesPlaces reserverDesPlaces;

    public ReservationController(ReserverDesPlaces reserverDesPlaces) {
        this.reserverDesPlaces = reserverDesPlaces;
    }

    @PostMapping("/seances/{seance}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationReponse reserver(@PathVariable("seance") String seance, @RequestBody ReservationRequete requete) {
        DemandeReservation demande = new DemandeReservation(new SeanceId(seance), traduire(requete));
        return ReservationReponse.de(reserverDesPlaces.executer(demande));
    }

    /** JSON → langage du domaine. Une place « 7G » ou une catégorie inconnue lèvent IllegalArgumentException (400). */
    private static List<DemandeBillet> traduire(ReservationRequete requete) {
        if (requete == null || requete.billets() == null) {
            return List.of();                                  // le domaine refusera : CommandeVide (422)
        }
        return requete.billets().stream()
                .map(b -> {
                    if (b == null || b.categorie() == null) {
                        throw new IllegalArgumentException("Chaque billet doit avoir une place et une catégorie");
                    }
                    return new DemandeBillet(PlaceId.de(b.place()), Categorie.valueOf(b.categorie()));
                })
                .toList();
    }
}
