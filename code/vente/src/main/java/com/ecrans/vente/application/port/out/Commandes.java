package com.ecrans.vente.application.port.out;

import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.SeanceId;

import java.util.List;
import java.util.Optional;

/** Port secondaire : la collection des commandes. */
public interface Commandes {
    Optional<Commande> parId(CommandeId id);

    void enregistrer(Commande commande);

    /** Bonus 9.4 : toutes les commandes d'une séance, pour réagir à son annulation (B10). */
    List<Commande> deLaSeance(SeanceId seance);
}
