# Les Écrans de Villeneuve — Architecture de la billetterie

Projet final du module **Concevoir une application numérique** (B3).

**Équipe 2** : Sofia Selloum · Rodolphe Fanneau de la Horie · Credo Olouwachegoun

**Dépôt de l'équipe** : https://github.com/rdolfe/ecrans-villeneuve-equipe2 — version rendue : tag **`v1.0`**

## Équipe

| Membre | Étapes codées | Sections du DAT dont il ou elle est référent·e | Partie présentée à l'oral |
|---|---|---|---|
| Sofia Selloum | 1 (en binôme), 3, 8 | 1, 2, 3, 4 | Le problème, le domaine et la context map |
| Rodolphe Fanneau de la Horie | 1 (en binôme), 4, 6, 7 | 5, 6, 10 | La grille, l'hexagone de la Vente, réserver et payer |
| Credo Olouwachegoun | 2, 5, bonus | 7, 8, 9, 11 | La démonstration, la décision 3 (ADR-0002), l'hypothèse H3 |

## Jalons

- [x] **J4, fin de la phase 1** : glossaire, context map et grille dans le DAT
- [x] **J4, fin de la phase 2** : tests verts pour les étapes 1 à 5, recherches de l'étape 6 sans résultat
- [x] **J4, fin de la phase 3** : `3-composants-vente` et `4-sequence-reserver-payer`
- [x] **Avant le J5** : étapes 7 et 8 (Spring Boot, ventes partenaires), `ApiVenteTest` et `VentePartenaireApiTest` verts, Swagger, DAT rédigé
- [x] **Bonus** : B13, B14 et concurrence activés ; 9.1 page de réservation ; 9.2 plan de salle ; 9.4 annulation d'une séance
- [ ] **J5, finalisation** : DAT exporté en PDF dans `docs/`, version taguée `v1.0` et poussée
- [ ] **Rendu sur Moodle** : le lien de ce dépôt

## Lancer

Tout se lance depuis `code/vente/` (JDK 21, Maven inclus dans IntelliJ).

| Quoi | IntelliJ | Ligne de commande |
|---|---|---|
| Tous les tests | Maven → vente → Lifecycle → **test** | `mvn test` |
| L'application | Run sur `VenteApplication` | `mvn spring-boot:run` |
| Swagger | http://127.0.0.1:8080/swagger-ui.html | |
| Page de réservation (bonus 9.1) | http://127.0.0.1:8080/ | |

Au démarrage, la séance de démonstration **S42** (demain, 20 h 30, rangs A à H, fauteuils 1 à 12) est publiée ;
chez SortiesFlash elle s'appelle `VIL-<date de demain>-2030-S3`.

### Parcours de démonstration (Swagger ou page web)

1. `POST /seances/S42/reservations` avec `{"billets": [{"place": "A1", "categorie": "PLEIN"}, {"place": "A2", "categorie": "ENFANT"}]}` → **201**, total 18,00 €.
2. La même requête une seconde fois → **409** : B1, les places sont bloquées.
3. `POST /webhooks/payvia` avec `{"event": "payment.succeeded", "reference": "<numéro de commande>"}` → **204** : la commande est payée, les places vendues.
4. `GET /seances/S42/places` → A1 et A2 ne sont plus libres.
5. `POST /partner/v2/orders` au format SortiesFlash (annexe B) → **201** `{"status": "CONFIRMED"}`.
6. `POST /programmation/seances/S42/annulation` → **204** ; toute nouvelle réservation → **422** (vente fermée).

## Ce qui a été fait

| Étape | Contenu | Preuve |
|---|---|---|
| 1-2 | Agrégats `Seance` (B1, B2, B4, tout ou rien) et `Commande` (B3, B6, cycle de vie) | `SeanceTest` (13), `CommandeTest` (11) |
| 3-5 | `ReserverDesPlacesService`, `ConfirmerPaiementService`, `ExpirerBlocagesService` | étapes 3 à 5 (16), `ScenarioSoireeDuJeudiTest` (2) |
| 6 | `ReserverDesPlacesService` dépendait de l'adapter `GrilleTarifaireFixe` → passe par le port `GrilleTarifaire` | `etape6/ArchitectureTest` (5) rejoue les recherches à chaque build |
| 7 | Spring Boot : `VenteConfiguration`, `ReservationController`, `WebhookPayViaController`, `ExpirationPlanifiee`, `GestionErreurs` | `etape7/ApiVenteTest` (6) |
| 8 | `EnregistrerVentePartenaire` + ACL SortiesFlash (`adapter/in/sortiesflash/`) | `etape8/VentePartenaireApiTest` (5), `AclSortiesFlashTest` (7), `EnregistrerVentePartenaireTest` (7) |
| bonus | B14 (notification en double), B13 (paiement tardif), concurrence (ré-essai sur `ConflitDeVersion`) | `bonus/` (7) |
| bonus 9.1 | Page de réservation `src/main/resources/static/index.html` | démonstration |
| bonus 9.2 | `ConsulterPlan` + `GET /seances/{seance}/places` | `ConsulterPlanTest` (6) |
| bonus 9.4 | `AnnulerSeance` (B10) + `POST /programmation/seances/{seance}/annulation` | `AnnulerSeanceTest` (5) |

**90 tests** au total : 79 en Java pur (sans Spring ni base) et 11 tests HTTP. Les tests fournis sont inchangés ;
seules les lignes `@Disabled` des bonus ont été retirées, comme le demande le sujet.

## Organisation du dépôt

```
ecrans-villeneuve/
├── README.md                              ← ce fichier
├── code/vente/                            ← l'hexagone « Vente de places »
│   ├── pom.xml                            ← Spring Boot 3.5 (étape 7)
│   ├── fournis/                           ← les tests des étapes 7 et 8, tels que fournis
│   └── src/
│       ├── main/java/com/ecrans/vente/
│       │   ├── VenteApplication.java
│       │   ├── domaine/                   ← agrégats, value objects, événements : Java pur
│       │   ├── application/               ← ports (in, out), services, exceptions
│       │   ├── adapter/in/                ← web, payvia, sortiesflash, planification, programmation
│       │   ├── adapter/out/               ← mémoire, tarification, paiement, événements
│       │   └── config/                    ← ApplicationVente (composition root), VenteConfiguration
│       ├── main/resources/static/         ← page de réservation (bonus 9.1)
│       └── test/java/com/ecrans/vente/    ← etape1 … etape8, bonus, support, scénario
└── docs/
    ├── DAT-Ecrans-de-Villeneuve.docx
    ├── DAT-Ecrans-de-Villeneuve.pdf
    └── diagrammes/                        ← chaque diagramme : source .drawio + export .png
```

## Conventions

- Les noms du code (`Seance`, `Commande`, `ReserverDesPlaces`…) sont ceux du glossaire du DAT et des diagrammes.
- Le domaine et l'application ne contiennent aucune annotation Spring ; l'heure vient toujours de la `Clock` injectée.
- Le DAT est un fichier Word : une seule personne le modifie à la fois. Un diagramme = une personne à la fois.
