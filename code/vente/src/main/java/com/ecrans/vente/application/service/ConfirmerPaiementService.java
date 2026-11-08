package com.ecrans.vente.application.service;

import com.ecrans.vente.application.exception.CommandeInconnue;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.ConfirmerPaiement;
import com.ecrans.vente.application.port.out.Commandes;
import com.ecrans.vente.application.port.out.ConflitDeVersion;
import com.ecrans.vente.application.port.out.PublicateurEvenements;
import com.ecrans.vente.application.port.out.Seances;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.seance.PlacesIndisponibles;
import com.ecrans.vente.domaine.seance.Seance;
import com.ecrans.vente.domaine.seance.VenteFermee;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Le paiement est confirmé : la commande est payée ET ses places sont vendues, dans la même transaction.
 * C'est l'entorse consciente à la règle 4 de Vernon (ADR-0002) : « payée ⇒ vendue » doit être immédiat,
 * sinon le blocage pourrait expirer et la place être revendue entre les deux.
 *
 * Bonus traités ici :
 * <ul>
 *     <li>B14 : la même notification arrive deux fois → la seconde ne produit rien ;</li>
 *     <li>B13 : le paiement arrive après la fin du blocage → on vend si les places sont libres,
 *         sinon la commande passe A_REMBOURSER (événement PaiementARembourser) ;</li>
 *     <li>B10 : le paiement arrive après l'annulation de la séance → remboursement, pas de vente.</li>
 * </ul>
 */
public class ConfirmerPaiementService implements ConfirmerPaiement {

    private static final int ESSAIS_MAX = 3;

    private final Seances seances;
    private final Commandes commandes;
    private final PublicateurEvenements publicateur;
    private final Clock horloge;

    public ConfirmerPaiementService(Seances seances, Commandes commandes, PublicateurEvenements publicateur,
                                    Clock horloge) {
        this.seances = seances;
        this.commandes = commandes;
        this.publicateur = publicateur;
        this.horloge = horloge;
    }

    @Override
    public void executer(CommandeId id) {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        for (int essai = 1; ; essai++) {
            try {
                confirmer(id, maintenant);
                return;
            } catch (ConflitDeVersion conflit) {
                // Une autre vente a modifié la séance entre-temps : on recharge et on rejoue.
                // Si cela échoue encore, l'exception remonte et PayVia renverra sa notification (annexe A).
                if (essai >= ESSAIS_MAX) {
                    throw conflit;
                }
            }
        }
    }

    private void confirmer(CommandeId id, LocalDateTime maintenant) {
        Commande commande = commandes.parId(id).orElseThrow(() -> new CommandeInconnue(id));
        if (commande.paiementDejaTraite()) {
            return;                                                        // B14 : déjà payée ou déjà à rembourser
        }
        Seance seance = seances.parId(commande.seance())
                .orElseThrow(() -> new SeanceInconnue(commande.seance()));

        try {
            seance.vendre(id, commande.places(), maintenant);              // le domaine décide (B1, B13)
        } catch (PlacesIndisponibles | VenteFermee placesPerdues) {
            // B13 / B10 : le client a payé mais ses places ne peuvent plus lui être vendues.
            // La séance n'est pas modifiée (vendre est « tout ou rien ») : seule la commande change.
            commande.aRembourser(maintenant);
            commandes.enregistrer(commande);
            commande.retirerEvenements().forEach(publicateur::publier);
            return;
        }
        commande.payer(maintenant);

        seances.enregistrer(seance);                                       // même transaction (ADR-0002)
        commandes.enregistrer(commande);
        commande.retirerEvenements().forEach(publicateur::publier);        // publiés APRÈS l'enregistrement
    }
}
