# Modèle des relations — système cible

Comment Siamois relie ses entités entre elles : quelle famille de relation pour quel besoin, et la
liste complète des tables de relation du système final (existantes, à créer, à supprimer).

## 1. Principe : trois familles, pas une table unique

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
| **C. Configurable** | Toute autre relation, voulue par la plateforme (stratigraphie) ou par une institution, simple ou avec attributs | Une **définition de relation** (configuration) + une table de liens `relation_link` avec une vraie FK par type d'entité (voir §4) |

L'ancienne famille « qualifiée » (stratigraphie) et l'ancienne famille « champ additionnel de type
référence » sont **fusionnées dans la famille C** : la relation stratigraphique n'est qu'une définition
complexe fournie par défaut.

Côté lecture, le choix est neutre : l'API décrit chaque relation par sa requête
(`RelationField` / `RelationFieldRepository`), et toutes les valeurs multiples sont servies de la même
façon (`MultiValue` : aperçu + total, liste complète à `GET /api/v1/{collection}/{id}/fields/{fieldId}/values`).

### « Hiérarchique » : une propriété de la relation, pas de la ligne

Le caractère hiérarchique est porté par la **table** (famille A) ou par la **définition** (famille C),
jamais par un drapeau sur chaque ligne. Une relation hiérarchique implique :

- source et cible **du même type d'entité**, et un seul type cible : dès que la cible inclut un autre type,
  l'option est désactivée ;
- **pas de cycle**, vérifié à l'écriture (requête récursive) ;
- polyhiérarchie autorisée (plusieurs parents), sauf règle de cardinalité contraire (le contenant n'a qu'un parent) ;
- affichage en arbre, parcours des ancêtres / descendants.

« Hiérarchique » n'est pas « sans cycle » : une relation d'ordre (stratigraphie) doit être acyclique sans
être une hiérarchie (voir règles, §4.6).

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
| `study_document` | document ↔ étude | créée (côté `Document.studies`) ; `specimen_study_document` n'est plus mappée, sa table reste orpheline en base |
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

## 4. Famille C — relations configurables

> **Statut : conception arrêtée (14/10/2026), non implémentée.** Les points encore ouverts sont en §4.9.

Une relation configurable relie deux types d'entités quelconques, sans migration. Elle est décrite par une
**définition de relation** (objet de configuration), et ses liens sont stockés dans une table commune.

### 4.1 Définition de relation

Une définition porte :

- les **types d'entités source et cible** autorisés ;
- le **mode** : *simple* (un lien sans attribut) ou *complexe* (le lien porte un formulaire) ;
- le **caractère hiérarchique** (relation simple seulement, §1) ;
- le **miroir** (§4.3) ;
- ses **côtés** : un concept par côté (§4.2).

### 4.2 Les côtés sont des concepts

Chaque côté d'une définition référence un **concept de thésaurus**, comme les champs système sont
déjà identifiés par des concepts. Le nom du champ affiché sur la fiche est le libellé du concept (donc traduit).

- La relation d'ordre stratigraphique a deux côtés : les concepts **antérieur** et **postérieur**.
- Le synchrone a un seul côté : le concept **synchrone**, qui est son propre miroir.
- La hiérarchie des UE a deux côtés : **parent** et **enfant**.

Ces concepts sont créés par migration dans le **thésaurus système**, en lecture seule pour les institutions :
un concept qui disparaît ou change d'identité casserait les listes et l'écran graphe (§4.8). Une relation
d'institution utilise des concepts de son propre thésaurus, avec le même mécanisme.

Le couplage des deux côtés (antérieur ↔ postérieur) est porté par **la définition**, pas déduit des liens
entre concepts du thésaurus (`concept_related`) : une modification du thésaurus ne doit pas casser le miroir.

### 4.3 Miroir

Un lien est stocké **une seule fois** ; l'autre côté en est une vue, jamais une seconde ligne.

| Miroir | Effet | Exemple |
|---|---|---|
| aucun | le lien n'apparaît que sur la fiche d'origine | — |
| soi-même (symétrique) | un seul champ, qui est son propre miroir ; types source et cible identiques | synchrone |
| autre champ | deux champs, un par côté ; libellé et emplacement réglés par type cible via la configuration de formulaire | antérieur / postérieur |

- Le miroir est **en lecture seule par défaut** (droits : éditer depuis la fiche cible modifie des données
  d'un autre périmètre). L'édition peut être autorisée par option.
- Orientation : chaque champ appartient à un côté, donc le champ où l'on saisit donne le sens du lien.
  Pour l'ordre : source = postérieur, cible = antérieur. Pour une relation symétrique, la ligne est ordonnée
  par identifiant (contrainte d'unicité) pour éviter (A,B) et (B,A). « Soi-même » est refusé si les types
  source et cible diffèrent.

### 4.4 Relation complexe : attributs et types

Le lien d'une relation complexe porte un **formulaire configurable**, avec le même moteur de champs que les
fiches. Deux sortes de champs :

- **champs partagés** : champs ordinaires du lien (incertitude, commentaire, champ de vocabulaire…),
  identiques vus des deux côtés ; un « type » unique, indépendant du côté, n'est que l'un d'eux ;
- **type par côté** : propriété d'un champ miroir. Chaque côté d'une relation à miroir *autre champ* peut
  avoir son propre champ type, avec son propre vocabulaire. Le lien stocke alors deux valeurs de type.
  Un miroir « soi-même » n'a qu'un seul côté, donc un seul type.

Les deux types sont reliés par une **correspondance, concept par concept, suggérée ou imposée** : choisir
*recoupe* d'un côté préremplit (suggéré) ou force (imposé) *est recoupé par* de l'autre. Pour la
stratigraphie, la correspondance est imposée ; pour une relation à rôles indépendants, elle est suggérée ou
absente. Si elle n'est que suggérée, les deux valeurs peuvent diverger : c'est voulu.

Le moteur de règles de champs reçoit à cette occasion un mécanisme générique : « quand le champ X vaut v,
préremplir ou imposer le champ Y à w ».

### 4.5 Stockage

Une table commune `relation_link`, avec **une FK nullable par type d'entité, à la source et à la cible**
(contrainte : exactement une renseignée de chaque côté), les colonnes de type par côté, et la définition.
Les vraies FK sont conservées ; le lien est une entité mappée en `@OneToMany` (et non `@ManyToMany`), ce qui
évite le piège d'Hibernate qui supprime puis recrée les lignes d'une collection partagée. Des index partiels
par paire de types compensent la table creuse.

**Les deux champs miroir ne sont pas des réponses de champ.** Ce sont deux lectures de la même ligne :
le champ du côté source filtre sur la colonne source, celui du côté cible sur la colonne cible (même mécanisme
que les champs inverses système, via `RelationField` / `RelationFieldRepository`). Les extrémités ne passent
donc pas par `custom_field_answer_*`. Les types par côté sont deux colonnes de la ligne. Les attributs partagés
du formulaire du lien sont des réponses ordinaires : `form_config_answer` reçoit une colonne
`fk_relation_link_id`, avec la même contrainte d'unicité `(fk_relation_link_id, fk_form_config_id)` que les
autres types d'entité, soit un seul jeu de réponses par lien, partagé par les deux côtés. Conséquence : le
tri et le filtre génériques de `FieldQueryService`, qui lisent les réponses, ne couvrent pas ces relations ;
il faut écrire l'équivalent (filtrer par « a un lien vers X », trier par identifiant de l'entité liée).

Tables ajoutées : `relation_definition`, `relation_definition_kind` (types source et cible autorisés),
`relation_link`, `relation_type_mapping` (correspondance entre types des deux côtés, suggérée ou imposée),
`relation_rule` et sa liaison aux définitions. Le formulaire du lien est une configuration de formulaire
rattachée à la définition (à vérifier : comment `FormConfig` se rattache à un type d'entité).

Les relations simples vers des concepts et des personnes peuvent rester dans les tables de réponses
`custom_field_answer_*_answers` existantes ; la bascule de ces tables vers `relation_link` est à trancher (§4.9).

Hiérarchies système (`*_hierarchy`, `container.parent`) : inchangées (famille A), lues par le même
mécanisme de `RelationFieldRepository`. Une relation **configurée** déclarée hiérarchique, elle, stocke ses
liens dans `relation_link` (un seul type cible, requête récursive pour le cycle et le parcours, index sur
(définition, source) et (définition, cible)). La vue en arbre lit donc deux sources, derrière une couche
d'accès commune ; une UE peut avoir son axe système et un axe configuré, la vue choisit son axe.

### 4.6 Règles de relation

Petit catalogue de types de règles (pas de langage d'expressions), objets de configuration à part qui
référencent une ou plusieurs définitions :

| Règle | Exemple |
|---|---|
| cardinalité par paire | deux UE n'ont qu'un lien entre elles, par définition ou toutes définitions confondues |
| cardinalité par entité | un seul parent ; au plus N liens synchrones |
| **pas de cycle** | A postérieur à B postérieur à A |
| exclusion entre définitions | une paire ne peut pas être à la fois « asynchrone » et « synchrone » |

Chaque règle a une **gravité** : bloquante ou avertissement. Vérification dans le service d'écriture
(pas de contrainte SQL possible entre définitions) ; un verrou sur la paire dans la transaction si les
écritures concurrentes comptent. Les liens existants qui violent une règle ajoutée après coup ne sont pas
supprimés : ils sont signalés sur la fiche et listés par un audit. Contrôle **direct seulement** : la
transitivité (A avant B, B avant C, A synchrone de C) est hors périmètre.

### 4.7 Relation stratigraphique : définitions fournies par défaut

`stratigraphic_relationship` devient deux définitions prédéfinies UE ↔ UE, non supprimables, dont seuls
les libellés et le vocabulaire sont configurables :

| Définition | Miroir | Champs sur la fiche UE | Vocabulaire des types |
|---|---|---|---|
| Ordre (asynchrone) | autre champ | **Antérieur** (côté cible) et **Postérieur** (côté source) | « recoupe » / « est recoupé par », « recouvre » / « est recouvert par »… ; correspondance imposée |
| Synchrone | soi-même | **Synchrone** | « égal à », « contemporain de », « se lie à » |

Le type est facultatif : le champ où l'on saisit donne déjà l'ordre. Un concept applicable aux deux (« s'appuie
sur ») figure dans les deux vocabulaires. L'ordre porte la règle « pas de cycle ». Les anciennes colonnes
`concept_direction`, `asynchronous`, `uncertain` sont reprises ainsi : le concept avec sa direction va dans le
type du côté actif et son réciproque dans l'autre ; « incertain » devient un champ du formulaire du lien ;
`asynchronous` est à clarifier (§4.9). La migration est faite **en dernier**, pour que la définition
prédéfinie serve de cas de test ; d'ici là l'ancienne table continue de fonctionner (champs `-328` à `-330`, un par côté, voir §4.8 bis).

### 4.8 Affichage

**Tables.** Catégorie de table « Relation », avec **un type de table par définition** (les colonnes du formulaire
du lien diffèrent). Une table contient : les deux extrémités, le type, les champs du lien. Un même composant
React sert deux usages : la **liste autonome** (les deux extrémités) et la **liste intégrée à une fiche**
(ancrée sur l'entité courante, n'affichant que l'extrémité opposée). Un lien se modifie dans un dialogue ou
un panneau latéral, pas dans un nouveau panneau principal.

**Libellé de ligne.** Le type du côté regardé s'affiche devant l'entité liée : « recoupe UE 12 » sur une
fiche, « est recoupé par UE 7 » sur l'autre. Option de colonne : automatique (défaut), colonne séparée,
préfixe de l'entité, masqué (liste déjà filtrée par côté). Le filtre sur un type porte sur la paire de concepts
corrélés, avec les deux libellés affichés. Feuilles d'export : une forme centrée sur le lien (une ligne par
lien, forme active) et une forme centrée sur l'entité (colonne « Relations » concaténée, libellés du côté
de la ligne) ; le module d'export gagne un `EntityKind` « Relation ».

**Écran graphe d'UE (plus tard).** Écran propre aux UE, centré sur une UE, profondeur 1 : parent en haut,
enfant en bas, antérieur et postérieur de part et d'autre, synchrone, et une zone « reste » bornée (compteur
et lien vers la liste). Les zones sont identifiées par les concepts système (§4.2) ; lecture unifiée des liens
d'une UE par union sur les deux côtés. Il se distingue de la matrice de Harris (graphe d'un site entier, en
couches, qui s'appuie sur l'absence de cycle de l'ordre).

### 4.8 bis Graphe des relations d'une UE (livré sur le modèle actuel)

L'onglet « Graphe » de la fiche UE dessine l'UE au centre : parentes en haut, enfants en bas, relations
stratigraphiques à droite (postérieures au-dessus, synchrones à la hauteur de l'UE, antérieures en dessous),
« autres relations » à gauche (vide : réservé aux relations configurables). Il lit **un seul contrat** :
`GET /api/v1/recording-units/{id}/relation-graph`, des zones `parents`, `children`, `anterior`, `synchronous`,
`posterior`, `others`, chacune un aperçu borné (`values`, `total`, `complete`). Le contrat ne cite ni table ni
identifiant de champ : le back lit chaque zone par `RelationFieldService`, donc **à la bascule sur `relation_link`
seul le branchement de `RelationField` change** (la zone `others` se remplira alors des relations configurables).

L'écriture de la stratigraphie existe, sur l'ancienne table, pour que le graphe et le champ « Relations
stratigraphiques » de la fiche soient éditables dès maintenant : `POST`, `PUT` et `DELETE` sur
`/api/v1/recording-units/{id}/stratigraphic-relationships` (position, type facultatif, incertain ; doublon, cycle
et unités de deux opérations refusés). Ces endpoints et `RecordingUnitStratigraphyWriteService` sont **à remplacer**
par `/relation-links` à la bascule ; le front (`components/relationLinks`) n'en dépend que par `api.ts`.
Convention posée : le type est lu de l'UE de l'URL vers l'autre UE (`concept_direction` vrai quand l'UE de l'URL
est la première du lien) ; à confirmer avec le point ouvert sur `concept_direction` ci-dessous.

**Trois champs, un par côté.** La fiche UE porte maintenant trois champs système (`-328` Antérieur, `-329` Postérieur,
`-330` Synchrone, qui remplacent `-327`), chacun lu par son propre `RelationField` (`RECORDING_UNIT_ANTERIOR`,
`_POSTERIOR`, `_SYNCHRONOUS`) : c'est exactement la forme « un champ par côté » du §4.7, déjà servie sur l'ancienne
table. Chaque champ est aussi un champ de vocabulaire (`CustomFieldConceptFromFieldCode`) : sa liste de types est celle
de son field code (`SIARU.STRATI.ANTERIOR`, `.POSTERIOR`, `.SYNCHRONOUS`, lus à travers `SIARU.STRATI` tant que le
thésaurus ne les définit pas), ou la branche ou la collection que le projet lui impose, comme tout champ de vocabulaire
(tiroir de configuration, `GET /organizations/{id}/concepts?fieldId=`). La migration Liquibase `2026.10.16-0` reporte la
configuration de `-327` (actif, obligatoire, groupe, position, largeur) sur les trois nouveaux champs.

**Liste.** La même fiche a une vue en liste des relations (`GET /recording-units/{id}/relations`, §api-changes) : une
ligne par relation, avec le type de relation (parent, enfant, antérieur, postérieur, synchrone) comme colonne
filtrable. Comme le graphe, elle lit des `RelationField` : elle ne change pas à la bascule sur `relation_link`.

### 4.9 Points ouverts

- Sens exact de l'ancienne colonne `asynchronous` (ordre inconnu ?) et nom définitif de la définition d'ordre
  (« Asynchrone » ou « Antériorité »).
- Comment les concepts système sont référencés (identifiant externe stable ou identifiant de base) : reprendre
  la convention des champs système, dont `-328` à `-330`.
- Une relation complexe peut-elle être hiérarchique ? (retenu : non, mais la règle « pas de cycle » existe.)
- Zone « reste » du graphe : UE seulement (recommandé) ou aussi mobilier, phases, documents.
- Liste autonome des liens dès le départ, ou listes intégrées seulement (recommandé).
- Les relations simples vers concepts et personnes restent-elles dans les tables de réponses ou passent-elles
  dans `relation_link` ?
- Droits d'édition du miroir et visibilité d'un lien dont la cible est hors du périmètre de l'utilisateur.
- Vocabulaire des définitions créées par une institution : qui peut créer une définition (institution ou
  administrateur de la plateforme).
- Reportés : détection des contradictions entre définitions, hiérarchies à types mixtes, colonnes avancées
  empruntées à l'entité liée.

## 5. Tables de relation hors données archéologiques

Pour mémoire — vocabulaires, droits et configuration, non concernés par ce modèle :

| Table | Relie |
|---|---|
| `concept_hierarchy` | concept parent → concept enfant (hiérarchique, reprise du thésaurus source) |
| `concept_related` | concept ↔ concepts associés |
| `collection_concept` | collection ↔ concepts |
| `profile_permission` | profil ↔ permissions |
| `person_profile_assignment` | personne ↔ profil, dans un périmètre (table porteuse de données) |
| `field_form_config` / `field_form_config_concept` | configuration de formulaire ↔ champs / concepts autorisés |

## 6. Chantiers qui en découlent

1. ~~**Liens de documents**~~ **Fait (01–02/10/2026).** Voir §3 : table de liaison par type, projet obligatoire,
   `ru_study_document` retiré de `DocumentRepository`. Reste à faire avec la table d'études unique et la
   table des mouvements : leurs documents.
2. ~~**Table d'études unique**~~ **Fait (14/10/2026), côté backend et API.** `study` (entité `Study`, tables de
   liaison `study_recording_unit` et `study_specimen`, `study_document`) remplace `specimen_study`, supprimée
   sans reprise de données car inutilisée. Trois types par défaut, dont « Traitement par défaut » qui porte les
   colonnes de la feuille 8 du référentiel SDA (voir `StudyDefaults`). Reste : le front React, les onglets
   « études » des fiches UE et mobilier, la liste d'organisation et la feuille `traitement` de l'export.
3. **Mouvements** : décider si un mouvement concerne un seul mobilier (colonne actuelle) ou plusieurs
   (`movement_specimen`).
4. **Relations configurables** (famille C, §4) : définition de relation, `relation_link`, concepts système
   (antérieur, postérieur, synchrone, parent, enfant), miroir, type par côté et correspondance, règles de
   relation, catégorie de table « Relation » ; en dernier, migration de `stratigraphic_relationship`.
5. **Thésaurus** : les concepts `TBL_*_Document` restent (une table de liaison par type) ; ajouter
   `TBL_concept` et `TBL_vocabulaire`.
