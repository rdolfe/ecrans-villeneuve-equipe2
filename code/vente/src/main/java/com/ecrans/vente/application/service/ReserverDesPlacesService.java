package com.ecrans.vente.application.service;

import com.ecrans.vente.adapter.out.tarification.GrilleTarifaireFixe;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.DemandeReservation;
import com.ecrans.vente.application.port.in.Reservation;
import com.ecrans.vente.application.port.in.ReserverDesPlaces;
import com.ecrans.vente.application.port.out.Commandes;
import com.ecrans.vente.application.port.out.ConflitDeVersion;
import com.ecrans.vente.application.port.out.Paiements;
import com.ecrans.vente.application.port.out.Seances;
import com.ecrans.vente.domaine.commande.Billet;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commande.Tarif;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.seance.Seance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Orchestration seulement : charger, faire décider le domaine, enregistrer, puis appeler le PSP.
 *
 * Choix d'agrégats (ADR-0002, option B) : Seance et Commande sont écrits dans la même transaction.
 * L'appel au PSP est fait APRÈS : on ne garde pas une transaction ouverte pendant un appel réseau.
 */
public class ReserverDesPlacesService implements ReserverDesPlaces {

    /** Bonus : nombre d'essais quand une autre vente a modifié la séance en même temps. */
    static final int ESSAIS_MAX = 3;

    private final Seances seances;
    private final Commandes commandes;
    private final GrilleTarifaireFixe grille;
    private final Paiements paiements;
    private final Clock horloge;

    public ReserverDesPlacesService(Seances seances, Commandes commandes, GrilleTarifaireFixe grille,
                                    Paiements paiements, Clock horloge) {
        this.seances = seances;
        this.commandes = commandes;
        this.grille = grille;
        this.paiements = paiements;
        this.horloge = horloge;
    }

    @Override
    public Reservation executer(DemandeReservation demande) {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        CommandeId id = CommandeId.nouvelle();
        List<Billet> billets = demande.billets().stream()
                .map(b -> billetAuPrixDeLaGrille(demande, b))          // B6 : le prix est figé maintenant
                .toList();

        Commande commande = bloquerEtEnregistrer(demande, id, billets, maintenant);

        // PayVia n'est appelé que pour une commande qui existe, et hors de la « transaction ».
        String urlPaiement = paiements.demanderPaiement(id, commande.total());
        return new Reservation(id, commande.total(), urlPaiement, commande.expireA());
    }

    private Billet billetAuPrixDeLaGrille(DemandeReservation demande, DemandeBillet b) {
        return new Billet(b.place(), new Tarif(b.categorie(), grille.prix(demande.seance(), b.categorie())));
    }

    /**
     * Toutes les décisions sont prises avant le premier enregistrement : une commande refusée
     * (11 places, place en double…) ne laisse aucune place bloquée.
     * Verrou optimiste (ADR-0002) : si une autre vente a enregistré la séance entre notre lecture et notre
     * écriture, on recharge et on rejoue, jusqu'à {@link #ESSAIS_MAX} fois.
     */
    private Commande bloquerEtEnregistrer(DemandeReservation demande, CommandeId id, List<Billet> billets,
                                          LocalDateTime maintenant) {
        Set<PlaceId> places = billets.stream().map(Billet::place).collect(Collectors.toCollection(TreeSet::new));
        for (int essai = 1; ; essai++) {
            Seance seance = seances.parId(demande.seance())
                    .orElseThrow(() -> new SeanceInconnue(demande.seance()));
            LocalDateTime finDuBlocage = seance.bloquer(places, id, maintenant);   // B1, B2, B4
            Commande commande = Commande.creer(id, demande.seance(), billets, finDuBlocage); // B3
            try {
                seances.enregistrer(seance);
            } catch (ConflitDeVersion conflit) {
                if (essai >= ESSAIS_MAX) {
                    throw conflit;
                }
                continue;                                                  // on recharge la séance et on rejoue
            }
            commandes.enregistrer(commande);
            return commande;
        }
    }
}
