package com.ecrans.vente.etape6;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Étape 6, automatisée : les recherches « Find in Files » du sujet, rejouées à chaque build.
 * Sans elles, la dépendance ReserverDesPlacesService → GrilleTarifaireFixe compilait et tous les tests
 * restaient verts : un test de comportement ne voit pas le SENS des dépendances. Ce test, si.
 * (Java pur, sans dépendance : ArchUnit ferait la même chose sur le bytecode.)
 */
class ArchitectureTest {

    private static final Path RACINE = trouverRacine();

    @Test
    void le_domaine_ne_depend_que_de_java() {
        assertThat(lignesContenant("domaine", ".application.", ".adapter.", ".config.", "import org.springframework"))
                .isEmpty();
    }

    @Test
    void l_application_ne_connait_ni_les_adapters_ni_la_config_ni_spring() {
        assertThat(lignesContenant("application", ".adapter.", ".config.", "import org.springframework")).isEmpty();
    }

    @Test
    void l_heure_vient_toujours_de_la_clock_injectee() {
        List<String> trouvees = new ArrayList<>();
        trouvees.addAll(lignesContenant("domaine", ".now()"));
        trouvees.addAll(lignesContenant("application", ".now()"));
        trouvees.addAll(lignesContenant("adapter", ".now()"));
        assertThat(trouvees).isEmpty();
    }

    @Test
    void les_adapters_primaires_ne_connaissent_que_les_ports_pas_les_services() {
        assertThat(lignesContenant("adapter", ".application.service.")).isEmpty();
    }

    @Test
    void le_langage_de_sortiesflash_reste_enferme_dans_son_acl() {
        List<String> fuites = new ArrayList<>();
        for (Path fichier : fichiersJava(RACINE)) {
            if (fichier.toString().replace('\\', '/').contains("/adapter/in/sortiesflash/")) continue;
            fuites.addAll(lignes(fichier, "show_ref", "ticket_types", "partner_order_id", "\"ADULT\"", "\"STUDENT\""));
        }
        assertThat(fuites).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------

    private static List<String> lignesContenant(String dossier, String... motifs) {
        List<String> trouvees = new ArrayList<>();
        for (Path fichier : fichiersJava(RACINE.resolve(dossier))) {
            trouvees.addAll(lignes(fichier, motifs));
        }
        return trouvees;
    }

    private static List<String> lignes(Path fichier, String... motifs) {
        List<String> trouvees = new ArrayList<>();
        try {
            List<String> contenu = Files.readAllLines(fichier);
            for (int i = 0; i < contenu.size(); i++) {
                String ligne = contenu.get(i).trim();
                if (ligne.startsWith("*") || ligne.startsWith("//") || ligne.startsWith("/*")) continue; // commentaires
                for (String motif : motifs) {
                    if (ligne.contains(motif)) {
                        trouvees.add(RACINE.relativize(fichier) + ":" + (i + 1) + "  " + ligne);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return trouvees;
    }

    private static List<Path> fichiersJava(Path dossier) {
        try (Stream<Path> s = Files.walk(dossier)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Maven et IntelliJ lancent les tests depuis code/vente ; on accepte aussi la racine du dépôt. */
    private static Path trouverRacine() {
        for (String candidat : List.of("src/main/java/com/ecrans/vente", "code/vente/src/main/java/com/ecrans/vente")) {
            Path p = Path.of(candidat);
            if (Files.isDirectory(p)) return p;
        }
        throw new IllegalStateException("Sources introuvables depuis " + Path.of("").toAbsolutePath());
    }
}
