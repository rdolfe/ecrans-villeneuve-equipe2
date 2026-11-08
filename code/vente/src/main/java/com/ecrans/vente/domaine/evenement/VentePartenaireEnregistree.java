package com.ecrans.vente.domaine.evenement;

import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.Montant;

import java.time.LocalDateTime;

/**
 * B11 : une vente partenaire a été enregistrée. Porte le montant réellement payé par le spectateur chez le partenaire :
 * la déclaration CNC (3.4) et le reversement mensuel (tarif de la grille − 8 %) le liront ici.
 */
public record VentePartenaireEnregistree(CommandeId commande, String partenaire, String referencePartenaire,
                                         Montant montantPaye, LocalDateTime survenuLe) implements EvenementVente {
}
