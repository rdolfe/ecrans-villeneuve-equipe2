package com.ecrans.vente.adapter.in.sortiesflash;

import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Anti-corruption layer entrante (étape 8) : traduit le langage de SortiesFlash (show_ref, seats, ADULT…)
 * dans le nôtre (SeanceId, PlaceId, Categorie, Montant). Java pur : testable sans Spring.
 * Si SortiesFlash change un code, seule cette classe bouge ; CinéMalin aura sa propre ACL, à côté.
 */
public class AclSortiesFlash {

    public static final String PARTENAIRE = "SortiesFlash";

    private static final Map<String, Categorie> TYPES = Map.of(
            "ADULT", Categorie.PLEIN,
            "STUDENT", Categorie.REDUIT,
            "CHILD", Categorie.ENFANT);

    private final CorrespondanceSeances correspondance;

    public AclSortiesFlash(CorrespondanceSeances correspondance) {
        this.correspondance = correspondance;
    }

    /**
     * @throws SeanceInconnue           si show_ref ne correspond à aucune de nos séances (404)
     * @throws IllegalArgumentException si le format est invalide : type inconnu, autant de types que de places… (400)
     */
    public VentePartenaire traduire(CommandeSortiesFlash commande) {
        exiger(commande != null, "corps de requête absent");
        exiger(commande.show_ref() != null && !commande.show_ref().isBlank(), "show_ref manquant");
        exiger(commande.partner_order_id() != null && !commande.partner_order_id().isBlank(), "partner_order_id manquant");
        exiger(commande.seats() != null && commande.ticket_types() != null, "seats et ticket_types sont obligatoires");
        exiger(commande.seats().size() == commande.ticket_types().size(),
                commande.seats().size() + " places pour " + commande.ticket_types().size() + " types de billet");
        exiger(commande.paid_amount_cents() != null, "paid_amount_cents manquant");

        SeanceId seance = correspondance.seance(commande.show_ref())
                .orElseThrow(() -> new SeanceInconnue(new SeanceId(commande.show_ref())));

        List<DemandeBillet> billets = new ArrayList<>();
        for (int i = 0; i < commande.seats().size(); i++) {
            billets.add(new DemandeBillet(PlaceId.de(commande.seats().get(i)), categorie(commande.ticket_types().get(i))));
        }
        return new VentePartenaire(PARTENAIRE, commande.partner_order_id(), seance, billets,
                Montant.centimes(commande.paid_amount_cents()));       // conservé : CNC et reversement (B11)
    }

    private static Categorie categorie(String typeSortiesFlash) {
        Categorie categorie = typeSortiesFlash == null ? null : TYPES.get(typeSortiesFlash);
        exiger(categorie != null, "type de billet SortiesFlash inconnu : " + typeSortiesFlash);
        return categorie;
    }

    private static void exiger(boolean condition, String probleme) {
        if (!condition) {
            throw new IllegalArgumentException("Vente SortiesFlash invalide : " + probleme);
        }
    }
}
