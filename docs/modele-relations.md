# Modèle des relations — système cible

Comment Siamois relie ses entités entre elles : quelle famille de relation pour quel besoin, et la
liste complète des tables de relation du système final (existantes, à créer, à supprimer).

## 1. Principe : quatre familles, pas une table unique

Réunir toutes les relations dans une seule table polymorphe `relation(source_type, source_id,
target_type, target_id)` a été écarté :

- **pas de vraie clé étrangère** possible vers plusieurs tables : l'intégrité (orphelins, cascade)
  reposerait sur des triggers ou sur le code, alors que l'architecture annonce l'inverse ;
- **JPA** ne sait pas mapper une relation polymorphe : `@ManyToMany`, et avec lui le tri/filtre
  générique des listes (`FieldQueryService`, qui lit le métamodèle JPA), ne fonctionneraient plus ;
- **une hiérarchie a des règles** (pas de cycle, même type des deux côtés, parcours récursif) simples
  dans une table dédiée, à coups de triggers dans une table générique.

À la place, chaque relation relève d'une famille, choisie selon ce qu'elle est :

| Famille | Quand | Stockage |
|---|---|---|
| **A. Structurelle** | Relation voulue par le modèle de données, identique pour tous | Une table dédiée par relation, vraies FK |
| **B. Document** | Lien N–N d'un document vers une entité principale ; un projet de rattachement obligatoire | Une table de liaison par type d'entité (le document la possède), vraies FK ; le projet est une colonne du document |
| **C. Qualifiée** | Relation porteuse d'un type (concept), d'un sens, d'une incertitude | Une table par paire de types, vraies FK, concept de relation |
| **D. Configurable** | Relation voulue par une institution (champ additionnel de type référence) | Tables de réponses `custom_field_answer_*_answers`, vraies FK |

Côté lecture, le choix est neutre : l'API décrit chaque relation par sa requête
(`RelationField` / `RelationFieldRepository`), et toutes les valeurs multiples sont servies de la même
façon (`MultiValue` : aperçu + total, liste complète à `GET /api/v1/{collection}/{id}/fields/{fieldId}/values`).

### « Hiérarchique » : une propriété du type de relation, pas de la ligne

Une ligne n'est pas hiérarchique par hasard : c'est la nature de la relation qui l'est. Le caractère
hiérarchique est donc porté par la **table** (famille A) ou par la **définition du champ** (famille D),
jamais par un drapeau sur chaque ligne. Une relation hiérarchique implique :

- source et cible **du même type** ;
- **pas de cycle**, vérifié à l'écriture (requête récursive) ;
- polyhiérarchie autorisée (plusieurs parents) ;
- affichage en arbre, parcours des ancêtres / descendants.

## 2. Famille A — relations structurelles (tables dédiées)

### Hiérarchies

| Table | Relie | Colonnes | Statut |
|---|---|---|---|
| `recording_unit_hierarchy` | UE parent → UE enfant | `fk_parent_id`, `fk_child_id` | existe |
| `specimen_hierarchy` | mobilier parent → mobilier enfant (ex. objet issu d'un lot) | `fk_parent_id`, `fk_child_id` | existe |
| `action_hierarchy` | projet parent → projet enfant | `fk_parent_id`, `fk_child_id` | existe |
| `spatial_hierarchy` | lieu parent → lieu enfant | `fk_parent_id`, `fk_child_id` | existe |
| `container` (colonne `parent`) | contenant parent → contenant enfant (hiérarchie simple, un seul parent) | FK `parent` dans la table | existe |

### Liens non hiérarchiques entre entités principales

| Table | Relie | Cardinalité | Statut |
|---|---|---|---|
| `recording_unit_phase` | UE ↔ phases | N–N | existe |
| `specimen_phase` | mobilier ↔ phases | N–N | existe |
| `specimen_container` | mobilier ↔ contenants | N–N | existe |
| `action_unit_spatial_context` | projet ↔ lieux (contexte spatial, en plus du lieu principal) | N–N | existe |
| `specimen.fk_recording_unit_id` | mobilier → son UE | N–1 (colonne, pas de table) | existe |
| `specimen_movement.fk_specimen_id` | mouvement → mobilier | N–1 (colonne) | existe — **à revoir si un mouvement concerne plusieurs mobiliers** (lot envoyé en étude) : table `movement_specimen` N–N |

### Liens vers des personnes

| Table | Relie | Statut |
|---|---|---|
| `recording_unit_contributors` | UE ↔ personnes (contributeurs) | existe |
| `specimen_authors` | mobilier ↔ personnes (auteurs) | existe |
| `specimen_collectors` | mobilier ↔ personnes (collecteurs) | existe |

### Liens vers des concepts (valeurs contrôlées multiples des champs système)

| Table | Relie | Statut |
|---|---|---|
| `action_unit_period` | projet ↔ concepts (périodes) | existe |
| `action_unit_subject` | projet ↔ concepts (sujets) | existe |
| `phase_period` | phase ↔ concepts (périodes) | existe |
| `phase_keyword` | phase ↔ concepts (mots-clés) | existe |
| `specimen_material` | mobilier ↔ concepts (matériaux) | existe |
| `specimen_material_class` | mobilier ↔ concepts (classes de matériau) | existe |

### Autres

| Table | Relie | Statut |
|---|---|---|
| `specimen_group_attribution` | mobilier ↔ groupes (lots) | existe |
| `recording_unit_on_the_fly_fields` | UE ↔ champs ajoutés à la volée | existe |

## 3. Famille B — documents : une table de liaison par type d'entité

Un document appartient à **un seul projet** (colonne `siamois_document.fk_action_unit_id`, obligatoire pour
l'application, unique avec l'identifiant : `uk_document_project_identifier`) et peut être lié à **plusieurs
entités** du projet, de **types différents** (N–N, facultatif). Chaque type d'entité a sa propre table de
liaison, avec deux vraies FK ; c'est le **document** qui porte la collection (`@ManyToMany`, `@NotAudited`).

| Table | Relie | Statut |
|---|---|---|
| `recording_unit_document` | document ↔ UE | existe (contrainte d'unicité sur le seul document retirée) |
| `specimen_document` | document ↔ mobilier | existe (idem) |
| `spatial_unit_document` | document ↔ lieu | existe (idem) |
| `phase_document` | document ↔ phase | créée |
| `container_document` | document ↔ contenant | créée |
| `specimen_study_document` | document ↔ étude de mobilier | inchangée (hors périmètre) |
| `action_unit_document` | document ↔ projet | **supprimée** : remplacée par la colonne de rattachement ; la migration a repris ses liens |

**Pourquoi pas une table unique `document_link`** (une colonne FK par type, une seule renseignée) — l'option
d'abord retenue ici :

- avec une collection JPA par type sur une même table, Hibernate **supprime puis recrée** toutes les lignes du
  document quand l'une des collections change : les liens des autres types seraient perdus ;
- le tri et le filtre génériques des listes (`FieldQueryService`) lisent le métamodèle JPA : ils ont besoin
  d'un `@ManyToMany` par type, donc d'une table par type ;
- un nouveau type d'entité coûte une table et une collection, ce qui reste léger pour un modèle qui n'en a
  que cinq.

Le projet n'est pas un lien : c'est le rattachement du document, qui détermine l'organisation, les droits
(`PROJECT_EDIT_DOCUMENTS`…) et la configuration de la table (catégorie = type de la table configurable
`DOCUMENT`).

**Migration** (`changelog/versions/2026.10.01-0.xml`) : le projet d'un document existant vient, dans cet ordre,
de `action_unit_document`, de l'UE liée, de l'UE du mobilier lié, ou du seul projet dont le contexte spatial
contient le lieu lié. Les documents restés sans projet gardent `NULL` : ils apparaissent dans les listes de
l'organisation (lecture seule, fiche introuvable) et sont à rattacher à la main. L'identifiant des documents
existants est `DOC-<id>`.

Documents pour les mouvements : jamais créés. `ru_study_document`, que `DocumentRepository` citait mais qui
n'existait pas en base, a été retiré du code.

## 4. Famille C — relations qualifiées

Relation entre deux entités **du même type**, dont le type vient d'un concept de vocabulaire, avec un
sens et une incertitude. Une table par type d'entité (vraies FK), pas une table polymorphe.

| Table | Relie | Qualificatifs | Statut |
|---|---|---|---|
| `stratigraphic_relationship` | UE ↔ UE | concept de la relation, `concept_direction`, `asynchronous`, `uncertain` | existe (exposée comme champ `-327`, lecture seule) |
| `specimen_relationship` | mobilier ↔ mobilier (« remonte avec », « identique à »…) | concept, sens, incertitude | éventuelle, sur le même modèle, si le besoin apparaît |

Si une relation qualifiée doit se comporter comme une hiérarchie, c'est son **concept** qui le déclare
(propriété dans le vocabulaire), et l'écriture applique alors les règles de la §1.

## 5. Famille D — relations configurables (champs additionnels)

Une institution peut relier deux entités par un **champ additionnel de type référence**, sans migration.
La réponse est rattachée à son entité par `form_config_answer` (une FK par type d'entité), et pointe vers
ses cibles par une table de liaison typée, avec vraies FK :

| Table de réponses | Cible | Statut |
|---|---|---|
| `custom_field_answer_concept_answers` | concepts | existe |
| `custom_field_answer_person_answers` | personnes | existe |
| `custom_field_answer_spatial_unit_answers` | lieux | existe |
| `custom_field_answer_action_unit_answers` | projets | existe |
| `custom_field_answer_recording_unit_answers` | UE | **à créer** |
| `custom_field_answer_specimen_answers` | mobilier | **à créer** |
| `custom_field_answer_phase_answers` | phases | **à créer** |
| `custom_field_answer_container_answers` | contenants | **à créer** |
| `custom_field_answer_document_answers` | documents | à créer si besoin (les documents sont déjà liés par leurs tables de liaison) |

Avec ces tables, le tri / filtre générique (`FieldQueryService`) et l'API (`MultiValue`, endpoint `values`)
fonctionnent sans code spécifique.

### Définition du champ (`custom_field`)

| Propriété | Rôle |
|---|---|
| type cible | l'entité référencée (answer type `SELECT_ONE_*` / `SELECT_MULTIPLE_*`) |
| **hiérarchique** (booléen) | impose : cible du même type que le propriétaire, pas de cycle, affichage en arbre |
| **libellé inverse** (ex. « contient » pour « inclus dans ») | affichage en lecture seule du lien chez la cible (« référencé par »), comme les champs inverses système (mobilier d'une UE, UE d'une phase) |

### Garde-fous

- **Pas de hiérarchie parallèle** : un champ hiérarchique dont la cible a déjà une hiérarchie système
  (UE, mobilier, projet, lieu) est refusé — ou alors c'est un autre axe, avec un libellé clairement distinct
  (« fait partie de l'ensemble »).
- **Pas de qualificatif** : une relation qui a besoin d'un type, d'un sens ou d'une incertitude n'est pas
  un champ additionnel, c'est une relation qualifiée (famille C).
- **Volume** : l'endpoint `values` relit aujourd'hui un champ additionnel en entier pour le paginer en
  mémoire. Si des champs pointent vers des centaines de cibles, passer à une requête paginée sur la table
  de réponses (même approche que `RelationFieldRepository`).

## 6. Tables de relation hors données archéologiques

Pour mémoire — vocabulaires, droits et configuration, non concernés par ce modèle :

| Table | Relie |
|---|---|
| `concept_hierarchy` | concept parent → concept enfant (hiérarchique, reprise du thésaurus source) |
| `concept_related` | concept ↔ concepts associés |
| `collection_concept` | collection ↔ concepts |
| `profile_permission` | profil ↔ permissions |
| `person_profile_assignment` | personne ↔ profil, dans un périmètre (table porteuse de données) |
| `field_form_config` / `field_form_config_concept` | configuration de formulaire ↔ champs / concepts autorisés |

## 7. Chantiers qui en découlent

1. ~~**Liens de documents**~~ **Fait (01–02/10/2026).** Voir §3 : table de liaison par type, projet obligatoire,
   `ru_study_document` retiré de `DocumentRepository`. Reste à faire avec la table d'études unique et la
   table des mouvements : leurs documents.
2. **Table d'études unique** (`study`) regroupant `specimen_study` et les études d'UE — rattachée à son UE
   ou son mobilier (colonnes FK exclusives, comme `form_config_answer`, ou tables de liaison si une étude porte
   sur plusieurs entités : à décider).
3. **Mouvements** : décider si un mouvement concerne un seul mobilier (colonne actuelle) ou plusieurs
   (`movement_specimen`).
4. **Champs additionnels** : tables de réponses vers UE, mobilier, phase, contenant ; propriétés
   « hiérarchique » et « libellé inverse » sur `custom_field` ; contrôle de cycle ; affichage inverse.
5. **Thésaurus** : les concepts `TBL_*_Document` restent (une table de liaison par type) ; ajouter
   `TBL_concept` et `TBL_vocabulaire`.
