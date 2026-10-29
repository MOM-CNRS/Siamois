# Ajouts à faire dans th337 (Siamois DEMO)

État au 2026-10-29. Chaque ajout est à faire **à la main dans OpenTheso** ; ceux qui sont marqués « script » sont aussi déclarés dans
`generate_thesaurus_socle.py` (bloc final, avant « DÉFINITIONS DU RÉFÉRENTIEL », pour ne pas décaler les ids existants). Le RDF du dépôt n'a
pas été régénéré. Une génération de contrôle (hors dépôt) donne 945 concepts : les 940 existants, inchangés, plus les 5 types ci-dessous.

## 1. Types d'UE du référentiel national à ajouter (script + OpenTheso)

Sous le champ **Type** de l'enregistrement (`SIARU.TYPE`, 1172). Ce sont les 5 valeurs de `type_UE` (th335) que th337 ne reprenait pas.

| Libellé FR | Libellé EN (proposé) | Définition (reprise du référentiel) | Rapprochement (`skos:exactMatch`, th326) |
|---|---|---|---|
| Entité spatiale | Spatial entity | Unité d'enregistrement définie par son extension spatiale. | `…?idc=rn0208&idt=th326` |
| Entité graphique | Graphic entity | Unité d'enregistrement d'un relevé, notamment en art rupestre (EG). | `…?idc=rn0209&idt=th326` |
| Unité graphique | Graphic unit | Élément graphique élémentaire composant une entité graphique (UG). | `…?idc=rn0210&idt=th326` |
| Entité taphonomique | Taphonomic entity | Unité d'enregistrement décrivant un processus post-dépositionnel (ET). | `…?idc=rn0211&idt=th326` |
| Phase | Phase | Unité d'enregistrement regroupant des UE contemporaines ou relevant d'un même état d'occupation. | `…?idc=rn0216&idt=th326` |

Le script ne les rattache à aucun autre champ : ni nature, ni agent, ni liste d'interprétation (comme UF, Sondage, Zone…).
Dans th335 les rapprochements sont `rn0208`, `rn0209`, `rn0210`, `rn0211` et `rn0216`.

## 2. Interprétation d'UE (`SIARU.INTERPRETATION`, 1183) — OpenTheso seulement

| Libellé FR | Lien au type | Raison |
|---|---|---|
| Terre végétale | US (1199) | 4 UE de Chartres (1000, 1001, 1002, 1010), des humus et terres à jardin. Proche de « Pédogénèse, humus », qui reste |
| Filin | US (1199) | 3 UE de Chartres (1032, 1034, 1037) : câble métallique SNCF sous protection béton, et un regard en briques |

Le lien « type → valeur » est un `skos:related` entre le type et la valeur, qui fait apparaître la valeur dans la liste du type.

## 3. Désignation du mobilier (`SIAS.INTERPRETATION`) — OpenTheso seulement

| Libellé FR | Lien au type | Remarque |
|---|---|---|
| Os animal | Faune (1429) | « Os isolé » existe mais appartient au VAB (restes humains) |
| Coquille d'huître | Faune (1429) | libellés alternatifs possibles : « coquille », « huître » |

## 4. Libellé alternatif à décider

| Concept | Libellé alternatif | Statut |
|---|---|---|
| Fait (1200) | Entité archéologique | **déjà dans le script**, à ajouter à la main **si on garde « Entité archéologique »** dans le classeur Chartres. Ce libellé n'existe pas dans le référentiel (th335 ne connaît que « fait » et « entité archéologique structurée »). L'alternative est de remplacer « Entité archéologique » par « Fait » dans le classeur : dans ce cas, ne rien ajouter et retirer la ligne du script |

## 5. Déjà fait, pour mémoire (ne pas refaire)

- Projet par défaut (`default-project/definition.json`) : les 7 nouveaux types d'UE sont ajoutés avec les ids de l'export OpenTheso du 2026-10-29
  (4291339 à 4291345) et les préfixes EA, ENSA, ESP, EG, UG, ET, PH. Ils n'ont pas de liste dédiée (pas de collection par type).

- Les 11 champs ajoutés dans th337 (géométries, identifiants du document et de l'étude…) : ids 4291327 à 4291337, voir `champs-hors-socle.md`.
- Le libellé « Entité archéologique structurée » est déjà un libellé alternatif d'Ensemble (1201).
- Nature, mobilier et phase du classeur Chartres : remplacés par des valeurs qui existent déjà dans th337 (voir `import-chartres.md`).
- Relations stratigraphiques : importées sans type, aucun ajout nécessaire.

## 6. Ce qui bloque l'import Chartres tant que ce n'est pas fait

| Libellé du classeur | Lignes | Ajout |
|---|---|---|
| Entité archéologique | 13 | § 4 (ou le remplacer par « Fait » dans le classeur) |
| Terre végétale | 4 | § 2 |
| Filin | 3 | § 2 |
| os animal | 1 | § 3 |
| coquille d'huître | 1 | § 3 |

Les 5 types du § 1 ne bloquent pas l'import Chartres : il ne les utilise pas.
