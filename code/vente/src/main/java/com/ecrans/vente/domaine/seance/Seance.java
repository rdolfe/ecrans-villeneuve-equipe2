package com.ecrans.vente.domaine.seance;

import com.ecrans.vente.domaine.commun.CommandeId;
import com.ecrans.vente.domaine.commun.PlaceId;
import com.ecrans.vente.domaine.commun.SeanceId;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Agrégat « Séance » du contexte Vente : le plan de salle d'une séance, à vendre.
 *
 * Attention au langage : pour Julien (Programmation), une séance est un film dans une salle à une heure.
 * Ici, une séance est un ensemble de fauteuils, libres, bloqués ou vendus. Pas de film, pas de durée.
 *
 * Invariants protégés : B1 (une place, une vente), B2 (blocage de 10 minutes), B4 (vente fermée à H+15).
 */
public class Seance {

    public static final Duration DUREE_BLOCAGE = Duration.ofMinutes(10);
    public static final Duration FERMETURE_APRES_DEBUT = Duration.ofMinutes(15);

    private final SeanceId id;
    private final LocalDateTime debut;
    private final Map<PlaceId, EtatPlace> places;
    /** Verrou optimiste : lu au chargement, vérifié à l'enregistrement par l'adapter. */
    private final long version;
    /** Bonus B10 : la Programmation a annulé (ou déplacé) la séance. */
    private boolean annulee;

    private Seance(SeanceId id, LocalDateTime debut, Map<PlaceId, EtatPlace> places, long version, boolean annulee) {
        this.id = Objects.requireNonNull(id);
        this.debut = Objects.requireNonNull(debut);
        this.places = new TreeMap<>(places);
        this.version = version;
        this.annulee = annulee;
    }

    /** Une séance publiée par la Programmation : toutes les places sont libres. */
    public static Seance ouverte(SeanceId id, LocalDateTime debut, Collection<PlaceId> plan) {
        Map<PlaceId, EtatPlace> etats = new TreeMap<>();
        plan.forEach(p -> etats.put(p, EtatPlace.libre()));
        return new Seance(id, debut, etats, 0, false);
    }

    /** Pour les adapters de persistance uniquement : recharger une séance enregistrée. */
    public static Seance reconstituer(SeanceId id, LocalDateTime debut, Map<PlaceId, EtatPlace> places, long version) {
        return reconstituer(id, debut, places, version, false);
    }

    /** Pour les adapters de persistance uniquement : recharger une séance enregistrée, annulée ou non. */
    public static Seance reconstituer(SeanceId id, LocalDateTime debut, Map<PlaceId, EtatPlace> places, long version,
                                      boolean annulee) {
        return new Seance(id, debut, places, version, annulee);
    }

    /**
     * Bloque des places pour une commande, pendant {@link #DUREE_BLOCAGE}.
     * Tout ou rien : si une seule place est prise, aucune n'est bloquée.
     *
     * @return l'heure de fin du blocage
     * @throws VenteFermee          si la vente est fermée (B4)
     * @throws PlaceInconnue        si une place n'existe pas dans la salle
     * @throws PlacesIndisponibles  si une place est déjà bloquée (blocage en cours) ou vendue (B1)
     */
    public LocalDateTime bloquer(Set<PlaceId> demandees, CommandeId commande, LocalDateTime maintenant) {
        if (!venteOuverteA(maintenant)) {
            throw new VenteFermee(id);                                    // B4
        }
        Set<PlaceId> dejaPrises = new TreeSet<>();
        for (PlaceId place : demandees) {
            if (!etat(place).estLibreA(maintenant)) {                     // etat(...) lève PlaceInconnue
                dejaPrises.add(place);
            }
        }
        if (!dejaPrises.isEmpty()) {
            throw new PlacesIndisponibles(dejaPrises);                    // B1, en listant TOUTES les places prises
        }
        // Toutes les vérifications sont passées : on peut modifier (tout ou rien).
        LocalDateTime jusqua = maintenant.plus(DUREE_BLOCAGE);            // B2
        demandees.forEach(place -> places.put(place, EtatPlace.bloquee(commande, jusqua)));
        return jusqua;
    }

    /**
     * Vend les places d'une commande qui vient d'être payée.
     * Chaque place doit être bloquée par cette commande… ou libre de nouveau (paiement tardif, voir B13).
     *
     * @throws PlacesIndisponibles si une place a été prise par quelqu'un d'autre entre-temps
     */
    public void vendre(CommandeId commande, Set<PlaceId> placesDeLaCommande, LocalDateTime maintenant) {
        if (annulee) {
            throw new VenteFermee(id);                                    // bonus B10 : on ne vend plus rien
        }
        Set<PlaceId> prisesParUnAutre = new TreeSet<>();
        for (PlaceId place : placesDeLaCommande) {
            EtatPlace etat = etat(place);                                 // lève PlaceInconnue
            boolean vendable = etat.estBloqueePar(commande)               // même si le blocage a expiré (B13)
                    || etat.estLibreA(maintenant);
            if (!vendable) {
                prisesParUnAutre.add(place);
            }
        }
        if (!prisesParUnAutre.isEmpty()) {
            throw new PlacesIndisponibles(prisesParUnAutre);              // B1
        }
        placesDeLaCommande.forEach(place -> places.put(place, EtatPlace.vendue(commande)));
    }

    /**
     * B2 : libère toutes les places dont le blocage a expiré.
     *
     * @return les commandes dont les places ont été libérées (pour les faire expirer)
     */
    public Set<CommandeId> libererBlocagesExpires(LocalDateTime maintenant) {
        Set<CommandeId> concernees = new LinkedHashSet<>();               // chaque commande une seule fois
        for (Map.Entry<PlaceId, EtatPlace> entree : places.entrySet()) {
            EtatPlace etat = entree.getValue();
            if (etat.blocageExpireA(maintenant)) {                        // les places vendues ne bougent pas
                concernees.add(etat.commande());
                entree.setValue(EtatPlace.libre());
            }
        }
        return concernees;
    }

    /**
     * Bonus B10 : la Programmation a annulé la séance. Plus aucune place ne se bloque ni ne se vend.
     *
     * @return faux si la séance était déjà annulée : la seconde annulation ne doit rien produire
     */
    public boolean annuler() {
        if (annulee) {
            return false;
        }
        annulee = true;
        return true;
    }

    /** B4 : on vend jusqu'à 15 minutes après l'heure de début, incluse. Jamais si la séance est annulée (B10). */
    public boolean venteOuverteA(LocalDateTime maintenant) {
        return !annulee && !maintenant.isAfter(debut.plus(FERMETURE_APRES_DEBUT));
    }

    public EtatPlace etat(PlaceId place) {
        EtatPlace etat = places.get(place);
        if (etat == null) throw new PlaceInconnue(id, place);
        return etat;
    }

    public Set<PlaceId> placesLibresA(LocalDateTime maintenant) {
        Set<PlaceId> libres = new TreeSet<>();
        places.forEach((p, e) -> { if (e.estLibreA(maintenant)) libres.add(p); });
        return libres;
    }

    public SeanceId id() { return id; }
    public LocalDateTime debut() { return debut; }
    public long version() { return version; }
    public boolean estAnnulee() { return annulee; }
    /** Copie : l'agrégat ne laisse personne modifier ses places de l'extérieur. */
    public Map<PlaceId, EtatPlace> places() { return new TreeMap<>(places); }

}
