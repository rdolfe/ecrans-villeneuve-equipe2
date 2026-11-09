package com.ecrans.vente.etape8;

import com.ecrans.vente.adapter.in.sortiesflash.AclSortiesFlash;
import com.ecrans.vente.adapter.in.sortiesflash.CommandeSortiesFlash;
import com.ecrans.vente.adapter.in.sortiesflash.CorrespondanceSeances;
import com.ecrans.vente.application.exception.SeanceInconnue;
import com.ecrans.vente.application.port.in.DemandeBillet;
import com.ecrans.vente.application.port.in.VentePartenaire;
import com.ecrans.vente.domaine.commande.Categorie;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.commun.PlaceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ecrans.vente.support.Donnees.S42;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Étape 8 · l'ACL traduit le format de SortiesFlash (annexe B) dans notre langage. Sans Spring. */
class AclSortiesFlashTest {

    private static final String SHOW_REF = "VIL-20261112-2030-S3";
    private AclSortiesFlash acl;

    @BeforeEach
    void preparer() {
        CorrespondanceSeances correspondance = new CorrespondanceSeances();
        correspondance.associer(SHOW_REF, S42);
        acl = new AclSortiesFlash(correspondance);
    }

    private static CommandeSortiesFlash commande(String showRef, List<String> seats, List<String> types) {
        return new CommandeSortiesFlash(showRef, seats, types, "SF-77812", 1840L);
    }

    @Test
    void traduit_l_exemple_de_l_annexe_B() {
        VentePartenaire vente = acl.traduire(commande(SHOW_REF, List.of("G7", "G8"), List.of("ADULT", "STUDENT")));

        assertThat(vente.partenaire()).isEqualTo("SortiesFlash");
        assertThat(vente.referencePartenaire()).isEqualTo("SF-77812");
        assertThat(vente.seance()).isEqualTo(S42);
        assertThat(vente.billets()).containsExactly(
                new DemandeBillet(PlaceId.de("G7"), Categorie.PLEIN),
                new DemandeBillet(PlaceId.de("G8"), Categorie.REDUIT));
    }

    @Test
    void conserve_le_montant_paye_chez_le_partenaire_B11() {
        VentePartenaire vente = acl.traduire(commande(SHOW_REF, List.of("G7", "G8"), List.of("ADULT", "STUDENT")));

        assertThat(vente.montantPaye()).isEqualTo(Montant.centimes(1840));
    }

    @Test
    void CHILD_devient_le_tarif_enfant() {
        VentePartenaire vente = acl.traduire(commande(SHOW_REF, List.of("F1"), List.of("CHILD")));

        assertThat(vente.billets()).singleElement().isEqualTo(new DemandeBillet(PlaceId.de("F1"), Categorie.ENFANT));
    }

    @Test
    void un_show_ref_inconnu_est_une_seance_inconnue() {
        assertThatThrownBy(() -> acl.traduire(commande("VIL-19990101-2030-S3", List.of("G7"), List.of("ADULT"))))
                .isInstanceOf(SeanceInconnue.class);
    }

    @Test
    void un_type_de_billet_inconnu_est_refuse() {
        assertThatThrownBy(() -> acl.traduire(commande(SHOW_REF, List.of("G7"), List.of("VIP"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VIP");
    }

    @Test
    void il_faut_autant_de_types_que_de_places() {
        assertThatThrownBy(() -> acl.traduire(commande(SHOW_REF, List.of("G7", "G8"), List.of("ADULT"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void un_montant_absent_est_refuse() {
        assertThatThrownBy(() -> acl.traduire(new CommandeSortiesFlash(SHOW_REF, List.of("G7"), List.of("ADULT"),
                "SF-1", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
