package com.ecrans.vente.application.service;

import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.EnregistrerVentePartenaire;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.application.port.out.Commandes;
import com.ecrans.vente.application.port.out.ConflitDeVersion;
import com.ecrans.vente.application.port.out.GrilleTarifaire;
import com.ecrans.vente.application.port.out.PublicateurEvenements;
import com.ecrans.vente.application.port.out.Seances;
import com.ecrans.vente.domaine.commande.Billet;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commande.Tarif;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.evenement.VentePartenaireEnregistree;
import com.ecrans.vente.domaine.seance.Seance;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Étape 8 · B11 : un partenaire envoie une vente déjà payée chez lui, en un seul appel.
 * Elle suit exactement les mêmes règles que nos ventes (B1 à B4), sans blocage de 10 minutes ni PayVia :
 * on bloque puis on vend aussitôt, dans la même « transaction ».
 */
public class EnregistrerVentePartenaireService implements EnregistrerVentePartenaire {

    private static final int ESSAIS_MAX = 3;

    private final Seances seances;
    private final Commandes commandes;
    private final GrilleTarifaire grille;
    private final PublicateurEvenements publicateur;
    private final Clock horloge;

    public EnregistrerVentePartenaireService(Seances seances, Commandes commandes, GrilleTarifaire grille,
                                             PublicateurEvenements publicateur, Clock horloge) {
        this.seances = seances;
        this.commandes = commandes;
        this.grille = grille;
        this.publicateur = publicateur;
        this.horloge = horloge;
    }

    @Override
    public CommandeId executer(VentePartenaire vente) {
        LocalDateTime maintenant = LocalDateTime.now(horloge);
        CommandeId id = CommandeId.nouvelle();
        // Les billets portent le prix de NOTRE grille : c'est la base du reversement (grille − 8 %, B11).
        List<Billet> billets = vente.billets().stream().map(b -> billetAuPrixDeLaGrille(vente, b)).toList();

        for (int essai = 1; ; essai++) {
            Commande commande = Commande.creer(id, vente.seance(), billets, maintenant);  // B3
            Seance seance = seances.parId(vente.seance()).orElseThrow(() -> new SeanceInconnue(vente.seance()));
            seance.bloquer(commande.places(), id, maintenant);                          // B4, B1, tout ou rien
            seance.vendre(id, commande.places(), maintenant);                           // déjà payée : vendue aussitôt
            commande.payer(maintenant);
            try {
                seances.enregistrer(seance);
            } catch (ConflitDeVersion conflit) {
                if (essai >= ESSAIS_MAX) {
                    throw conflit;
                }
                continue;                                                               // ADR-0002 : on rejoue
            }
            commandes.enregistrer(commande);

            commande.retirerEvenements().forEach(publicateur::publier);
            publicateur.publier(new VentePartenaireEnregistree(id, vente.partenaire(), vente.referencePartenaire(),
                    vente.montantPaye(), maintenant));
            return id;
        }
    }

    private Billet billetAuPrixDeLaGrille(VentePartenaire vente, DemandeBillet b) {
        return new Billet(b.place(), new Tarif(b.categorie(), grille.prix(vente.seance(), b.categorie())));
    }
}
