package com.ecrans.vente.etape8;

import com.ecrans.vente.adapter.out.evenement.PublicateurEnMemoire;
import com.ecrans.vente.adapter.out.memoire.CommandesEnMemoire;
import com.ecrans.vente.adapter.out.memoire.SeancesEnMemoire;
import com.ecrans.vente.adapter.out.tarification.GrilleTarifaireFixe;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.application.service.EnregistrerVentePartenaireService;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commande.Commande;
import com.ecrans.vente.domaine.commande.StatutCommande;
import com.ecrans.vente.domaine.commande.TropDePlaces;
import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;
import com.ecrans.vente.domaine.evenement.CommandePayee;
import com.ecrans.vente.domaine.evenement.VentePartenaireEnregistree;
import com.ecrans.vente.domaine.seance.PlacesIndisponibles;
import com.ecrans.vente.domaine.seance.Seance;
import com.ecrans.vente.domaine.seance.StatutPlace;
import com.ecrans.vente.domaine.seance.VenteFermee;
import com.ecrans.vente.support.HorlogeReglable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.ecrans.vente.support.Donnees.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Étape 8 · le cas d'usage « Enregistrer une vente partenaire » : mêmes règles que nos ventes (B1 à B4), sans PayVia. */
class EnregistrerVentePartenaireTest {

    private SeancesEnMemoire seances;
    private CommandesEnMemoire commandes;
    private PublicateurEnMemoire publicateur;
    private HorlogeReglable horloge;
    private EnregistrerVentePartenaireService service;

    @BeforeEach
    void preparer() {
        seances = new SeancesEnMemoire();
        commandes = new CommandesEnMemoire();
        publicateur = new PublicateurEnMemoire();
        horloge = HorlogeReglable.a("19:00");
        seances.enregistrer(seanceS42());
        service = new EnregistrerVentePartenaireService(seances, commandes, new GrilleTarifaireFixe(), publicateur, horloge);
    }

    private static VentePartenaire vente(SeanceId seance, String... placesEtCategories) {
        List<DemandeBillet> billets = new ArrayList<>();
        for (int i = 0; i < placesEtCategories.length; i += 2) {
            billets.add(new DemandeBillet(PlaceId.de(placesEtCategories[i]), Categorie.valueOf(placesEtCategories[i + 1])));
        }
        return new VentePartenaire("SortiesFlash", "SF-77812", seance, billets, Montant.centimes(1840));
    }

    @Test
    void les_places_sont_vendues_aussitot_sans_blocage() {
        service.executer(vente(S42, "G7", "PLEIN", "G8", "REDUIT"));

        Seance seance = seances.parId(S42).orElseThrow();
        assertThat(seance.etat(PlaceId.de("G7")).statut()).isEqualTo(StatutPlace.VENDUE);
        assertThat(seance.etat(PlaceId.de("G8")).statut()).isEqualTo(StatutPlace.VENDUE);
    }

    @Test
    void la_commande_est_payee_au_prix_de_notre_grille() {
        CommandeId id = service.executer(vente(S42, "G7", "PLEIN", "G8", "REDUIT"));

        Commande commande = commandes.parId(id).orElseThrow();
        assertThat(commande.statut()).isEqualTo(StatutCommande.PAYEE);
        assertThat(commande.total()).isEqualTo(Montant.centimes(2050));      // 11,50 € + 9,00 €, base du reversement
    }

    @Test
    void le_montant_paye_chez_le_partenaire_est_publie_B11() {
        CommandeId id = service.executer(vente(S42, "G7", "PLEIN", "G8", "REDUIT"));

        assertThat(publicateur.publies()).hasSize(2);
        assertThat(publicateur.publies().get(0)).isInstanceOf(CommandePayee.class);
        assertThat(publicateur.publies().get(1)).isEqualTo(new VentePartenaireEnregistree(id, "SortiesFlash",
                "SF-77812", Montant.centimes(1840), le12Novembre("19:00")));
    }

    @Test
    void une_place_prise_fait_refuser_toute_la_vente_B1_B11() {
        service.executer(vente(S42, "G7", "PLEIN"));

        assertThatThrownBy(() -> service.executer(vente(S42, "G7", "PLEIN", "G8", "ENFANT")))
                .isInstanceOf(PlacesIndisponibles.class);
        assertThat(seances.parId(S42).orElseThrow().etat(PlaceId.de("G8")).statut()).isEqualTo(StatutPlace.LIBRE);
        assertThat(publicateur.publies()).hasSize(2);                         // seulement la première vente
    }

    @Test
    void plus_de_10_places_sont_refusees_B3() {
        assertThatThrownBy(() -> service.executer(vente(S42, "F1", "PLEIN", "F2", "PLEIN", "F3", "PLEIN", "F4", "PLEIN",
                "F5", "PLEIN", "F6", "PLEIN", "F7", "PLEIN", "F8", "PLEIN", "F9", "PLEIN", "F10", "PLEIN", "G1", "PLEIN")))
                .isInstanceOf(TropDePlaces.class);
        assertThat(commandes.nombre()).isEqualTo(0);
    }

    @Test
    void la_vente_partenaire_ferme_aussi_15_minutes_apres_le_debut_B4() {
        horloge.reglerA("20:46");

        assertThatThrownBy(() -> service.executer(vente(S42, "G7", "PLEIN"))).isInstanceOf(VenteFermee.class);
    }

    @Test
    void une_seance_inconnue_est_signalee() {
        assertThatThrownBy(() -> service.executer(vente(new SeanceId("S99"), "G7", "PLEIN")))
                .isInstanceOf(SeanceInconnue.class);
    }
}
