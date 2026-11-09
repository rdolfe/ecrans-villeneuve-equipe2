package com.ecrans.vente.bonus;

import com.ecrans.vente.application.port.in.Reservation;
import com.ecrans.vente.config.ApplicationVente;
import com.ecrans.vente.domaine.commande.StatutCommande;
import com.ecrans.vente.domaine.commun.Montant;
import com.ecrans.vente.domaine.evenement.CommandeExpiree;
import com.ecrans.vente.domaine.evenement.EvenementVente;
import com.ecrans.vente.domaine.evenement.PaiementARembourser;
import com.ecrans.vente.domaine.seance.VenteFermee;
import com.ecrans.vente.support.HorlogeReglable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ecrans.vente.support.Donnees.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bonus 9.4 · B10 : la Programmation annule la séance S42 alors qu'une commande est payée et qu'une autre attend
 * son paiement. La Vente réagit seule ; Paiement et Notification réagiront à PaiementARembourser.
 */
class AnnulerSeanceTest {

    private HorlogeReglable horloge;
    private ApplicationVente app;
    private Reservation payee;
    private Reservation enAttente;

    @BeforeEach
    void preparer() {
        horloge = HorlogeReglable.a("19:00");
        app = new ApplicationVente(horloge);
        app.seances.enregistrer(seanceS42());
        payee = app.reserverDesPlaces.executer(demande("G7", "PLEIN", "G8", "REDUIT"));
        app.confirmerPaiement.executer(payee.commande());                  // publie CommandePayee
        enAttente = app.reserverDesPlaces.executer(demande("F1", "PLEIN"));
        horloge.reglerA("19:05");
    }

    private List<EvenementVente> evenementsApresLePaiement() {
        List<EvenementVente> tous = app.publicateur.publies();
        return tous.subList(1, tous.size());
    }

    @Test
    void les_commandes_payees_sont_a_rembourser_B10() {
        app.annulerSeance.executer(S42);

        assertThat(app.commandes.parId(payee.commande()).orElseThrow().statut()).isEqualTo(StatutCommande.A_REMBOURSER);
        assertThat(evenementsApresLePaiement()).contains(
                new PaiementARembourser(payee.commande(), Montant.centimes(2050), le12Novembre("19:05")));
    }

    @Test
    void les_commandes_en_attente_expirent() {
        app.annulerSeance.executer(S42);

        assertThat(app.commandes.parId(enAttente.commande()).orElseThrow().statut()).isEqualTo(StatutCommande.EXPIREE);
        assertThat(evenementsApresLePaiement()).contains(
                new CommandeExpiree(enAttente.commande(), S42, le12Novembre("19:05")));
    }

    @Test
    void une_seance_annulee_ne_se_vend_plus() {
        app.annulerSeance.executer(S42);

        assertThatThrownBy(() -> app.reserverDesPlaces.executer(demande("F5", "PLEIN"))).isInstanceOf(VenteFermee.class);
    }

    @Test
    void la_meme_annulation_recue_deux_fois_ne_rembourse_personne_deux_fois() {
        app.annulerSeance.executer(S42);
        int apresLaPremiere = app.publicateur.publies().size();

        app.annulerSeance.executer(S42);

        assertThat(app.publicateur.publies()).hasSize(apresLaPremiere);
    }

    @Test
    void un_paiement_recu_apres_l_annulation_est_rembourse_pas_vendu() {
        app.annulerSeance.executer(S42);
        horloge.reglerA("19:06");

        app.confirmerPaiement.executer(enAttente.commande());

        assertThat(app.commandes.parId(enAttente.commande()).orElseThrow().statut()).isEqualTo(StatutCommande.A_REMBOURSER);
        assertThat(app.publicateur.publies().get(app.publicateur.publies().size() - 1)).isEqualTo(
                new PaiementARembourser(enAttente.commande(), Montant.centimes(1150), le12Novembre("19:06")));
    }
}
