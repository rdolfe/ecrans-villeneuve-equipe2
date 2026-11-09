package com.ecrans.vente.application.service;

import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.AnnulerSeance;
import com.ecrans.vente.application.port.out.Commandes;
import com.ecrans.vente.application.port.out.ConflitDeVersion;
import com.ecrans.vente.application.port.out.PublicateurEvenements;
import com.ecrans.vente.application.port.out.Seances;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commande.StatutCommande;
import com.ecrans.vente.domaine.commun.SeanceId;
import com.ecrans.vente.domaine.seance.Seance;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Bonus 9.4 · B10 : le cinéma annule (ou déplace) une séance déjà vendue.
 * La Vente ferme la séance, puis :
 * <ul>
 *     <li>chaque commande PAYEE passe A_REMBOURSER → événement PaiementARembourser
 *         (le contexte Paiement rembourse par PayVia, la Notification prévient le client) ;</li>
 *     <li>chaque commande EN_ATTENTE_PAIEMENT expire → ses places ne seront jamais vendues.</li>
 * </ul>
 * Idempotent : une seconde annulation de la même séance ne rembourse personne une seconde fois.
 * Vente partenaire : même événement ; c'est le partenaire qui rembourse son client et le reversement
 * mensuel qui est corrigé (hypothèse H1 du DAT).
 */
public class AnnulerSeanceService implements AnnulerSeance {

    private static final int ESSAIS_MAX = 3;

    private final Seances seances;
    private final Commandes commandes;
    private final PublicateurEvenements publicateur;
    private final Clock horloge;

    public AnnulerSeanceService(Seances seances, Commandes commandes, PublicateurEvenements publicateur,
                                Clock horloge) {
        this.seances = seances;
        this.commandes = commandes;
        this.publicateur = publicateur;
        this.horloge = horloge;
    }

    @Override
    public void executer(SeanceId id) {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        if (!fermerLaSeance(id)) {
            return;                                                  // déjà annulée : rien à refaire
        }
        for (Commande commande : commandes.deLaSeance(id)) {
            if (commande.statut() == StatutCommande.PAYEE) {
                commande.aRembourser(maintenant);                    // B10 : remboursement automatique
            } else if (commande.statut() == StatutCommande.EN_ATTENTE_PAIEMENT) {
                commande.expirer(maintenant);                        // un paiement tardif sera remboursé (B13)
            } else {
                continue;                                            // expirée ou déjà à rembourser : rien
            }
            commandes.enregistrer(commande);
            commande.retirerEvenements().forEach(publicateur::publier);
        }
    }

    /** @return faux si la séance était déjà annulée */
    private boolean fermerLaSeance(SeanceId id) {
        for (int essai = 1; ; essai++) {
            Seance seance = seances.parId(id).orElseThrow(() -> new SeanceInconnue(id));
            if (!seance.annuler()) {
                return false;
            }
            try {
                seances.enregistrer(seance);
                return true;
            } catch (ConflitDeVersion conflit) {
                if (essai >= ESSAIS_MAX) {
                    throw conflit;
                }
            }
        }
    }
}
