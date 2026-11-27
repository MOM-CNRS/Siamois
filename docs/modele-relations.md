# Modèle des relations — système cible

Comment Siamois relie ses entités entre elles, et ce que deviennent les tables de relation actuelles.

> **Statut : conception révisée le 26/11/2026 (modèle minimal, identité globale et règles par projet), non
> implémentée.** Elle remplace la version du
> 25/11/2026 (pivot d'entités, prédicats, catalogue de règles) et celle du 14/10/2026 (trois familles, une FK par type
> d'entité dans `relation_link`). La branche `feat/relation-link`, qui implémentait la version du 14/10, n'est pas en
> production : on n'en reprend que des morceaux de code. Points ouverts en §10.

## 1. Principe

Objectif de fond : un cœur générique, avec l'archéologie comme module, pour pouvoir sortir Siamois de
l'archéologie. Le modèle reste volontairement simple :

- **un champ est soit un attribut, soit une relation** ;
- une **définition de relation** donne l'**identité** d'une relation : ses deux champs, un par côté, et ce qui fixe
  le sens des liens enregistrés. Elle est globale (§3.2) ;
- ses **règles** (types acceptés, boucles, choix unique, exclusions, miroirs, formulaire du lien) sont des valeurs
  par défaut copiées dans chaque projet, comme la configuration des fiches, et modifiables par projet (§3.3) ;
- une **table de liens unique**, `relation_link`, **polymorphe** : chaque extrémité est désignée par son type et son
  identifiant (§3.5).

Pas de table pivot d'entités. Elle ne servirait qu'à donner de vraies clés étrangères aux liens ; l'intégrité est tenue
autrement (§3.5). Les types non archéologiques partageront plus tard **une seule** table « autre », que le lien
polymorphe couvre directement. Si un pivot devient utile, la migration de `relation_link` sera mécanique (deux
colonnes remplies par jointure sur le type et l'identifiant).

Restent **hors** de ce mécanisme : les liens vers des concepts et des personnes, les tables hiérarchiques actuelles
(migrées plus tard, §7) et les tables de configuration (§9).

## 2. Quatre niveaux de configuration

| Niveau | Porte | Portée |
|---|---|---|
| le champ (`custom_field`) | **une face** : libellé, type « relation » | global |
| la **définition de relation** | **l'identité** : ses deux champs, simple ou complexe, symétrique, hiérarchique, champ qualificatif | **globale, une par paire de champs, fixe** |
| les **règles de la relation** | types acceptés de chaque côté, interdire les boucles, choix unique ou multiple de chaque côté, exclusions, paires miroir, formulaire du lien | **défaut de la plateforme, puis institution, puis projet**, comme les fiches |
| la configuration de formulaire (`field_form_config`) | **une face dans un formulaire** : actif, obligatoire, position, largeur | par institution ou projet, et par type |

**Pourquoi ce découpage.** Ce qui est dans la définition fixe le sens des liens enregistrés : les liens pointent vers
elle, une relation symétrique range ses paires, le qualificatif est stocké sur le lien. Le changer d'un projet à
l'autre rendrait les liens incomparables entre projets. Le reste ne fait que contrôler la saisie. Comme **un lien relie
toujours deux fiches du même projet** (§3.5), chaque lien n'obéit qu'aux règles de son projet : des règles différentes
d'un projet à l'autre ne se contredisent jamais.

Les règles ne peuvent pas vivre dans `field_form_config`, qui varie par type : les liens d'une relation obéissent aux
mêmes règles dans un projet, quel que soit le formulaire où on les saisit.

## 3. Champs relation et définitions

### 3.1 Attribut ou relation

Un champ de formulaire est soit un **attribut** (texte, nombre, concept…), soit une **relation** vers d'autres fiches.
Une relation est **simple** (un lien sans information en plus, hiérarchique ou non) ou **complexe** (chaque lien a son
propre formulaire : qualificatif, dates, commentaire…).

### 3.2 La définition : l'identité, globale

```
relation_definition (id, fk_source_field_id, fk_target_field_id,
                     kind (simple/complexe), is_symmetric, is_hierarchical,
                     fk_qualifier_field_id)
```

| Option | Effet |
|---|---|
| deux champs | un par côté ; une relation symétrique désigne le même champ des deux côtés. Un champ n'appartient qu'à une définition |
| simple ou complexe | une relation complexe a un qualificatif et, plus tard, un formulaire du lien |
| symétrique | la paire est rangée avant l'insertion (§3.5) |
| hiérarchique | la relation range les fiches en arbre ; force « interdire les boucles » dans toutes ses règles |
| champ qualificatif | pour une relation complexe : le champ de concept qui qualifie le lien, et dont la valeur est stockée sur le lien (§3.5) |

Ces options ne changent plus une fois que la définition a des liens.

### 3.3 Les règles : par défaut, puis par institution et par projet

```
relation_rule               (id, fk_relation_definition_id, fk_institution_id, fk_action_unit_id,
                             no_cycle, source_unique, target_unique)
relation_rule_kind          (fk_relation_rule_id, side, kind, fk_subtype_concept_id)
relation_rule_mirror_concept(fk_relation_rule_id, fk_concept_id, fk_mirror_concept_id)
relation_rule_exclusion     (fk_relation_rule_id, fk_excluded_definition_id)
```

| Règle | Effet |
|---|---|
| types acceptés de chaque côté | type d'entité et sous-type facultatif (UE/US, UE/Fait…), plusieurs par côté. Sans type, toute fiche (comme un pointeur non typé de Heurist) |
| choix unique ou multiple | réglé **de chaque côté** : un projet a une commune (unique), une commune a plusieurs projets (multiple) |
| interdire les boucles | pas de boucle dans les liens de la définition, dans le projet (§4.1) |
| exclusive avec | d'autres définitions : une même paire ne peut pas être liée par les deux (§4.2) |
| paires miroir | quel concept est le miroir de quel autre (§3.4) |

**Portée.** Un jeu de règles est rattaché à une définition et à une portée :

- **défaut de la plateforme** (institution et projet vides) : fourni par le système ou un module, pour les
  relations système ;
- **institution** (projet vide) ;
- **projet**.

Pour un lien, on lit les règles **du projet, sinon de son institution, sinon le défaut de la plateforme**. Un jeu de
règles est complet : on ne fusionne pas deux niveaux. Modifier les règles dans un projet qui n'a pas les siennes en
crée une copie, puis la modifie.

**Modèles de projet.** C'est le mécanisme des fiches (`ProjectCopyTemplateApplier`) : créer un projet depuis un projet
existant copie aussi ses règles ; le modèle « par défaut » ne copie rien, le projet lit les règles de son institution
ou le défaut de la plateforme. Les projets existants, qui n'ont pas de règles, lisent donc le défaut sans migration.

**Droits.** Les règles d'un projet ou d'une institution se modifient avec les droits de configuration actuels du projet
ou de l'institution. Le défaut de la plateforme se modifie par l'administrateur de la plateforme, et touche les projets
qui n'ont pas leurs propres règles. La définition elle-même ne se modifie plus une fois qu'elle a des liens.

Un champ s'utilise sur plusieurs types : le même champ « Postérieur à » est placé dans le formulaire des US et dans
celui des faits. Les types acceptés et les formulaires où le champ est placé doivent concorder : placer un champ sur
le formulaire d'un type non accepté déclenche un avertissement, et la configuration propose d'ajouter ce type.

**Parcours** : on crée et on modifie une définition **depuis la configuration d'un champ**. Dans le formulaire d'un
type, on ajoute un champ de type « relation » ; son côté accepte le type courant (et d'autres si on veut) ; on choisit
les types de l'autre côté, les options, et le champ de l'autre côté (existant ou créé). Ensuite, les deux champs mènent
à la même définition. L'écran de configuration est hors de cette itération : le backend expose ce parcours en API.

**Plus tard, si besoin** : des champs différents selon le type d'un même côté (« Postérieur (fait) »). Il suffira de
remplacer les deux champs de la définition par une table (définition, côté, type, champ) ; les liens pointent vers la
définition et ne changent pas.

### 3.4 Qualificatif et miroir

Sur une relation complexe, un attribut peut **qualifier** le lien (« recoupe ») ; sa valeur vue de l'autre fiche est
son **miroir** (« est recoupé par »).

- Le qualificatif est un champ de concept, désigné par la définition. Son vocabulaire vient d'une **branche** du
  thésaurus ; celui du miroir vient d'une **autre branche**. Le choix des listes suit le mécanisme actuel des
  vocabulaires de projet.
- La valeur est **stockée sur le lien** (`fk_qualifier_concept_id`), pas dans une réponse de formulaire : elle a le
  même sens dans tous les projets, elle est historisée avec le lien, et les listes et le graphe la lisent sans jointure.
- `relation_rule_mirror_concept` déclare quel concept est le miroir de quel autre. Une institution qui a son propre
  vocabulaire déclare ses propres paires.
- **On ne stocke que le qualificatif** ; le miroir s'en déduit toujours, donc les deux ne peuvent pas diverger.
- **Sans correspondance déclarée, le miroir est le même concept** (comme le terme inverse par défaut de Heurist).
- Choisir le miroir depuis la fiche d'en face (« est recoupé par ») enregistre son correspondant (« recoupe »).

### 3.5 Les liens

```
relation_link (id, fk_relation_definition_id,
               fk_action_unit_id,               -- le projet des deux fiches : il dit quelles règles le lien suit
               source_kind, source_id, target_kind, target_id,
               fk_qualifier_concept_id,         -- relation complexe : le qualificatif, vu de la source
               uncertain,                       -- NOT NULL, faux par défaut
               simple)                          -- copie de la définition, pour l'index d'unicité
```

- Le lien pointe vers la **définition**, pas vers un champ : les deux champs lisent les mêmes lignes, chacun par son
  côté. Le champ de l'autre côté est le « miroir » de la relation.
- **Polymorphe** : une extrémité est un couple (type, identifiant). Le service d'écriture vérifie que la fiche existe ;
  un **trigger AFTER DELETE** sur chaque table métier supprime les liens de la fiche supprimée (fonction unique dans
  `changelog/pgplsql/`, posée sur chaque table).
- **Toute relation peut être incertaine** (`uncertain` obligatoire).
- **Pas de doublon dans une définition** pour une relation simple. Un index partiel ne peut pas lire la définition :
  `relation_link` porte donc une copie `simple` (renseignée à l'écriture depuis la définition), et un **index unique
  partiel** (définition, source_kind, source_id, target_kind, target_id) `WHERE simple` garantit l'unicité en base,
  y compris entre deux écritures simultanées. Deux définitions différentes peuvent relier la même paire, sauf exclusion
  (§4.2). Une relation **complexe** accepte plusieurs liens entre les deux mêmes fiches, comme Heurist.
- Relation symétrique : la paire est rangée (type, identifiant) croissant **avant l'insertion**, donc (A, B) et (B, A)
  donnent la même ligne et l'index ci-dessus attrape aussi le doublon inversé.
- **Plus tard**, les autres attributs d'un lien complexe (durée, commentaire) : un formulaire du lien configuré par
  portée comme les fiches, et des réponses ordinaires via une colonne `fk_relation_link_id` ajoutée à
  `form_config_answer`. Ce sont des ajouts de colonnes, sans migration des liens.
- `relation_link` est **audité** (Envers).
- **Même projet, implicitement** : les deux fiches appartiennent au même projet. Le projet de chaque extrémité est
  résolu **par type**, en un seul endroit (`RelationEndpointResolver.resolveProjectId`) : UE, mobilier, phase,
  contenant, étude et document portent `fk_action_unit_id` ; un projet est son propre projet. Le lien garde ce projet
  (`fk_action_unit_id`) : c'est lui qui désigne les règles à suivre, et qui permet de vérifier une restriction de
  règles contre les liens des projets concernés sans jointure sur chaque table métier. Les lieux, qui ne
  dépendent pas d'un projet, ne sont pas acceptés pour l'instant (cas à traiter plus tard).
- **Lecture** : un champ relation se lit comme les champs relation actuels (aperçu et total par fiche dans les listes,
  page des valeurs, filtre « a un lien vers » et tri par nombre de liens). Chaque côté lit les mêmes liens ; le
  qualificatif vu de la cible est son miroir dans les règles du projet du lien.
- Un lien vers une fiche que l'utilisateur ne peut pas ouvrir reste visible ; l'ouvrir donne l'erreur que gère déjà le
  système de permissions.

### 3.6 Exemple : la stratigraphie en trois champs

```yaml
definition: ordre                        # identité, globale
  champ_source: "Postérieur à"          # placé sur les formulaires US et Fait
  champ_cible:  "Antérieur à"           # placé sur les formulaires US et Fait
  relation: complexe
  qualificatif: "Relation stratigraphique"
regles: ordre, défaut de la plateforme   # copiables et modifiables par institution ou projet
  types_source: [UE/US, UE/Fait]
  types_cible:  [UE/US, UE/Fait]
  interdire_les_boucles: true
  exclusive_avec: [synchronie]
  qualificatif:                          # branche « relation vers une unité antérieure »
    vocabulaire: [comble, coupe, recouvre, s'appuie sur]
    miroir:                              # branche « relation vers une unité postérieure »
      comble: est comblée par
      coupe: est coupée par
      recouvre: est recouverte par
      s'appuie sur: est appuyée par
      sur: sous
definition: synchronie
  champ_source: "Synchrone"
  champ_cible:  "Synchrone"              # symétrique
  relation: complexe                     # qualificatif facultatif : équivalent à, contemporain de…
regles: synchronie, défaut de la plateforme
  types_source: [UE/US, UE/Fait]
  types_cible:  [UE/US, UE/Fait]
  exclusive_avec: [ordre]
```

Sur l'UE 12, champ « Postérieur à », on ajoute l'UE 5 avec le qualificatif « recouvre » : une ligne (ordre, UE 12 →
UE 5, qualificatif = recouvre, incertain = non). La fiche de l'UE 12 affiche « recouvre UE 5 » ; celle de l'UE 5,
dans « Antérieur à », « est recouverte par UE 12 ».

Dans l'application actuelle, ces champs s'appellent autrement : « Postérieur à » est le champ système `-328`
« Antérieur » (sur une UE, les UE qui lui sont antérieures) et « Antérieur à » le `-329` « Postérieur ». Le qualificatif
est facultatif : un lien d'ordre sans qualificatif dit seulement « postérieur à » (docs/reprise-stratigraphie.md).

L'ordre se modélise comme **une seule relation avec qualificatif**, pas comme plusieurs relations « Recoupe »,
« Couvre »… : c'est ce qui permet au contrôle de boucle de voir toute la stratigraphie.

## 4. Contrôles

Principes communs : vérification dans le service d'écriture (donc aussi pour l'import et l'API) ; un lien incertain
qui participe à une violation la transforme en avertissement ; le sélecteur de saisie ne propose que les cibles
compatibles ; une définition ne peut pas être modifiée d'une façon que ses liens existants violeraient (§4.4).

| Règle | Protection |
|---|---|
| doublon (relation simple) | index unique partiel + contrôle applicatif |
| choix unique, exclusion, boucle | contrôle applicatif sous verrou du projet |
| même projet, fiche existante, types acceptés | résolution des deux extrémités par le service |

### 4.1 Interdire les boucles

Sur les liens d'une définition. Pour ajouter A → B, on vérifie qu'aucun chemin ne mène déjà de B à A :

```sql
-- Peut-on ajouter (:source_kind, :source_id) → (:target_kind, :target_id) dans la définition :def ?
WITH RECURSIVE atteint(kind, id) AS (
    SELECT :target_kind, :target_id
  UNION                              -- chaque fiche n'est visitée qu'une fois
    SELECT l.target_kind, l.target_id
    FROM atteint a
    JOIN relation_link l ON l.source_kind = a.kind AND l.source_id = a.id
    WHERE l.fk_relation_definition_id = :def
)
SELECT EXISTS (SELECT 1 FROM atteint WHERE kind = :source_kind AND id = :source_id);
```

- Exemple : A antérieur à B, B antérieur à C ; « C antérieur à A » est refusé, avec le chemin : « C est postérieur à
  B, et B est postérieur à A ; C ne peut donc pas être antérieur à A. »
- Bloquant si tous les liens de la boucle sont certains, avertissement sinon.
- Verrou applicatif `pg_advisory_xact_lock` sur le projet le temps de vérifier et d'écrire.
- Index (type, identifiant, définition) de chaque extrémité : le même sert au trigger de suppression (tous les liens
  d'une fiche) et aux lectures et au contrôle de boucle d'une définition.
- Les tables hiérarchiques actuelles gardent leur contrôle (`HierarchyCycleGuard`) tant qu'elles ne sont pas migrées.
- **Plus tard** : le synchrone comme équivalence (A synchrone de B, B < C, C < A n'est pas vu par le contrôle minimal).
  L'ajouter ne demandera ni migration ni changement de `relation_link`.

### 4.2 Exclusion

Une même paire ne peut pas être liée, dans un sens ou dans l'autre, par deux définitions déclarées exclusives (l'ordre
et la synchronie). Contrôle direct, par une requête sur la paire.

### 4.3 Types acceptés et choix unique

- Type et sous-type de chaque extrémité vérifiés à l'écriture, et revérifiés quand le sous-type d'une fiche change.
- Choix unique d'un côté : au plus un lien de ce côté pour une fiche. Le minimum relève du champ obligatoire.

### 4.4 Modifier ou supprimer une définition ou ses règles

- **Supprimer une définition qui a des liens est refusé.** Il faut d'abord supprimer ses liens, par une action explicite.
  Ses deux champs forment avec elle un tout : on ne supprime pas l'un des deux en laissant la définition incomplète.
- **L'identité d'une définition qui a des liens ne change pas** (simple ou complexe, symétrique, hiérarchique,
  qualificatif).
- **Restreindre une règle est refusé si des liens existants la violent**, parmi les liens des projets qui lisent ce jeu
  de règles (le projet ; ou les projets de l'institution sans règles propres ; ou tous les projets sans règles propres,
  pour le défaut de la plateforme) : retirer un type accepté encore utilisé,
  passer un côté en choix unique alors que des fiches ont plusieurs liens, cocher « interdire les boucles » sur des
  liens qui en contiennent, déclarer une exclusion entre deux définitions qui relient déjà la même paire. Le refus
  liste les liens en cause. Élargir une option (ajouter un type, passer en choix multiple) est toujours possible.

## 5. Comparer des champs différents

Le modèle ne cherche pas à rendre comparables deux façons de saisir la même chose. Dans les vues en liste, le champ
est une colonne ; un futur mécanisme de **fusion de colonnes** permettra de déclarer qu'un champ A correspond à la
valeur B de la colonne C. Les déductions (relations redondantes, héritage, séquences, dates) se feront hors de
SIAMOIS, à partir de l'export des relations.

## 6. Stratigraphie et reprise de l'existant

### 6.1 Configuration fournie par le module archéologie

Les deux définitions du §3.6 et leurs règles par défaut de la plateforme, sur les champs système actuels : `-328`
« Antérieur » (côté source, porté par l'UE postérieure) et `-329` « Postérieur » (son miroir) (définition ordre), `-330` Synchrone (définition synchronie). Le qualificatif de
l'ordre est stocké sur le lien ; plus tard, la durée de la relation (au plus court, au plus long) ira dans un
formulaire du lien. Sur l'UE, les bornes de datation sont de simples champs.

Les vocabulaires existants s'y prêtent déjà : la branche « relation vers une unité antérieure » (comble, coupe,
recouvre, s'appuie sur) et la branche « relation vers une unité postérieure » (est comblée par, est coupée par, est
recouverte par, est appuyée par) sont des **paires un à un** de concepts distincts. La branche synchrone n'a pas
besoin de correspondance.

**Les concepts sont désignés par leur `external_id`, jamais par leur identifiant de base**, qui varie d'une base à
l'autre (ou, à défaut, par leur appartenance aux branches des field codes `SIARU.STRATI.*`). Les identifiants des
champs système (`-328`, `-329`, `-330`) sont, eux, fixes par construction.

### 6.2 Reprise de `stratigraphic_relationship`

Aujourd'hui, une ligne stocke un seul concept, et `concept_direction` dit depuis quelle UE il se lit (vrai : depuis
`unit1`) ; la fiche d'en face n'affiche que le numéro.

**Étape 0, bloquante, avant d'écrire la migration** :

- auditer le trigger `order_strat_relationship` et `StratiTriggerInitializer` : ce qu'ils contrôlent ou modifient, pour
  que le nouveau mécanisme le reprenne ou que la perte soit actée (le code actuel indique qu'il ignore en silence
  l'insertion d'une paire inversée) ;
- inventorier les concepts **réellement utilisés** dans `stratigraphic_relationship` (distincts de ceux seulement
  présents dans les vocabulaires), avec leur sens (`concept_direction`), et la table de correspondance qui en découle :
  ancien concept → définition, sens, qualificatif ;
- fixer, par un test, quelle colonne de l'ancienne table est l'UE postérieure.

La reprise :

1. `asynchronous` vrai → lien de la définition ordre, faux → synchronie ; `uncertain` → colonne.
2. Chaque lien d'ordre est remis dans le sens de la relation : **source = l'UE postérieure**.
3. Le concept devient le qualificatif du lien (`fk_qualifier_concept_id`), **toujours pris dans la branche du qualificatif** : un
   concept de la branche miroir (« est recouverte par ») est remplacé par son correspondant (« recouvre »).
4. L'ancienne liste commune (`SIARU.STRATI`) reçoit des paires explicites (coupe / est coupée par, sur / sous,
   englobe / est englobée par, regroupe / est regroupée dans) ; ses termes sans inverse gardent le même concept.
5. Un **rapport de migration** liste les concepts utilisés sans correspondance connue (repris tels quels) et les termes
   qui ressemblent à de la hiérarchie (regroupe, englobe).
6. **L'ancienne table est supprimée, avec son historique** (changelog 2026-11-27-1, décidé le 27/11/2026) : plus
   aucun code ne la lit ni n'y écrit, le trigger `order_strat_relationship` et `StratiTriggerInitializer` sont retirés.
   SIAMOIS n'étant pas en production, l'historique de `stratigraphic_relationship_aud` n'est pas repris. Les liens
   non repris (« regroupe », « englobe ») ne sont utilisés par aucune base connue.
7. Comptages avant et après : lignes de l'ancienne table = liens ordre + synchronie ; liens incertains ; liens par
   qualificatif.

La reprise a été conçue sans base réelle (la base locale ne compte que 59 liens, dont un typé) : elle s'appuie sur les
correspondances connues et sur le rapport.

### 6.3 Phases et séquences : des sous-types d'UE (prévu)

L'UE est une entité générique, à laquelle les phases pouvaient déjà être rangées. Phases et séquences deviendront des
sous-types d'UE, reliés aux US et aux faits par une relation hiérarchique « regroupe » ; comme la définition ordre
n'accepte que US et Fait, elles n'entrent pas dans le contrôle de boucle. La migration de la table `phase` (prévue, non
faite) devra être précédée d'une revue du code qui suppose qu'une UE est une unité stratigraphique : listes et
compteurs, stratigraphie et graphe, export SDA, numérotation, vue en arbre. La mise en séquences n'est pas tranchée.

### 6.4 Livré sur le modèle actuel : graphe et liste des relations d'une UE

L'onglet « Graphe » de la fiche UE lit **un seul contrat** : `GET /api/v1/recording-units/{id}/relation-graph`, des
zones `parents`, `children`, `anterior`, `synchronous`, `posterior`, `others`, chacune un aperçu borné. Le back lit
chaque zone par `RelationFieldService` : **à la bascule, seul le branchement de `RelationField` change** (la zone
`others` se remplira des autres relations). L'écriture actuelle (`/stratigraphic-relationships`,
`RecordingUnitStratigraphyWriteService`) est **à remplacer** par `/relation-links` ; le front (`components/relationLinks`)
n'en dépend que par `api.ts`. La liste des relations (`GET /recording-units/{id}/relations`) lit elle aussi des
`RelationField`.

## 7. Ce que deviennent les autres tables actuelles

> **À trancher : la migration des champs système qui pointent vers une autre fiche.** Au-delà des trois champs
> stratigraphiques, de nombreux champs système sont en fait des relations, stockés en table ou en colonne. Leur
> migration vers le modèle de relations n'est pas décidée ; rien n'est prévu dans l'itération en cours.
>
> | Fiche | Champ système | Stockage actuel |
> |---|---|---|
> | UE, phase, étude, document, contenant, mobilier | Projet | colonne `fk_action_unit_id` |
> | Mobilier | UE d'origine | colonne `fk_recording_unit_id` |
> | UE | Fait partie de / Contient | `recording_unit_hierarchy` |
> | Mobilier | Fait partie de / Contient | `specimen_hierarchy` |
> | UE, mobilier | Phases | `recording_unit_phase`, `specimen_phase` |
> | Mobilier | Contenants | `specimen_container` |
> | Étude | UE, mobilier étudiés | `study_recording_unit`, `study_specimen` |
> | Document | UE, mobilier, lieux, phases, contenants | tables `*_document` |
> | Projet | Contexte spatial | `action_unit_spatial_context` |
> | UE | Lieu | colonne `fk_spatial_unit_id` |
> | Projet | Lieu principal | colonne |
> | Contenant | Lieu | colonne |
> | Document, mobilier | Lieu de stockage | colonne |
>
> Plusieurs champs sont déjà des miroirs sans le dire (les UE d'un projet ou d'une phase, le mobilier d'une UE ou d'un
> contenant). Questions à trancher : quels champs restent des **rattachements** en colonne (projet, UE d'origine du
> mobilier ?) et lesquels deviennent des relations ; les champs vers des **lieux** attendent la décision sur les lieux.

Les tables ci-dessous **restent en place**, avec leur fonctionnement actuel. Si leur migration est décidée, elle se fera
**plus tard, au cas par cas**, le jour où une relation configurée doit les remplacer :

| Aujourd'hui | Plus tard |
|---|---|
| `recording_unit_hierarchy`, `specimen_hierarchy`, `action_hierarchy`, `spatial_hierarchy`, `container.parent` | relations hiérarchiques ; la vue en arbre et `HierarchyCycleGuard` suivent. Mesurer les requêtes récursives avant |
| `custom_field_answer_action_unit_answers`, `custom_field_answer_spatial_unit_answers` | relations (choix unique ou multiple) ; une ligne par valeur, les valeurs d'une même fiche réparties sur plusieurs formulaires étant fusionnées |
| `recording_unit_phase`, `specimen_phase`, `specimen_container`, `action_unit_spatial_context`, `specimen_group_attribution`, `study_recording_unit`, `study_specimen` | relations système |
| tables `*_document` | à trancher avec les documents |
| liens vers des personnes et des concepts | inchangés |
| `specimen.fk_recording_unit_id`, `siamois_document.fk_action_unit_id` | restent des colonnes : ce sont des rattachements, pas des liens |

## 8. Conséquences pour l'audit (Envers)

- `relation_link` est audité : la stratigraphie a un historique à partir de la bascule. L'historique **antérieur**
  (`stratigraphic_relationship_aud`) est abandonné : SIAMOIS n'est pas en production.
- **Les liens supprimés avec leur fiche ne sont pas audités.** Le trigger de suppression agit en SQL, hors d'Envers ;
  c'était déjà le cas de l'ancienne table, que `ProjectDeletionService` vidait en SQL natif.
  Seuls les liens supprimés par l'utilisateur, à travers le service, laissent une révision. C'est un choix assumé.
- Les tables métier ne changent pas : aucune conséquence sur leur historique.
- Le qualificatif est une colonne du lien : il est historisé avec lui. Les autres attributs d'un lien complexe
  (plus tard) seront des réponses de champ, non auditées comme les autres réponses ; à traiter avec le chantier
  d'audit.
- Les règles (`relation_rule` et ses tables) sont de la configuration, auditées comme `form_config`.

## 9. Tables de relation hors données archéologiques

Pour mémoire — vocabulaires, droits et configuration, non concernés par ce modèle :

| Table | Relie |
|---|---|
| `concept_hierarchy` | concept parent → concept enfant (hiérarchique, reprise du thésaurus source) |
| `concept_related` | concept ↔ concepts associés |
| `collection_concept` | collection ↔ concepts |
| `profile_permission` | profil ↔ permissions |
| `person_profile_assignment` | personne ↔ profil, dans un périmètre (table porteuse de données) |
| `field_form_config` / `field_form_config_concept` | configuration de formulaire ↔ champs / concepts autorisés |

## 10. Points ouverts et plus tard

Ouverts :

- La migration des champs système qui pointent vers une autre fiche (§7) : lesquels restent des rattachements en
  colonne, lesquels deviennent des relations.

- Les lieux, qui ne dépendent pas d'un projet : relations entre projets et lieux.
- Classement de `spatial_unit`, `container`, `siamois_document` : cœur ou module archéologie.
- Phase : niveau de mise en séquences ou période chronologique indépendante ; la mise en séquences elle-même.
- Documents : tables de liaison actuelles ou relation.

Plus tard (s'ajoutent sans migrer `relation_link`, sauf le pivot) :

- l'écran de configuration des relations (frontend) ;
- le formulaire du lien (autres attributs qu'un qualificatif : durée, commentaire), par portée ;
- le synchrone comme équivalence dans le contrôle de boucle ;
- des champs différents selon le type d'un même côté ;
- le lien complexe comme fiche à part entière (documents, étiquettes, liens vers un lien) ;
- la fusion de colonnes pour comparer des champs différents ;
- la table « autre » des types non archéologiques ; un pivot d'entités si un besoin transverse le justifie (migration
  mécanique de `relation_link`) ;
- la migration des tables hiérarchiques et de liaison (§7) ;
- les vues de relations (onglet « Lieux » d'un projet, graphe généralisé) ;
- l'export sémantique (CIDOC-CRM / CRMarchaeo) et le traitement externe des déductions.

## 11. Chantiers

1. ~~**Liens de documents**~~ **Fait (01–02/10/2026)** : table de liaison par type, projet obligatoire.
2. ~~**Table d'études unique**~~ **Fait (14/10/2026), backend et API.** Reste le front, les onglets « études » des
   fiches UE et mobilier, la liste d'organisation et la feuille `traitement` de l'export.
3. **Mouvements** : un seul mobilier par mouvement (actuel) ou plusieurs (`movement_specimen`).
4. **Relations** (§2 à §4), en étapes avec critère de sortie : socle de données (définitions, règles par portée, liens,
   trigger, index), écriture (contrôles, verrou, résolution des règles, cycle de vie des définitions et des règles),
   copie des règles par les modèles de projet, API, lecture. Le cœur SQL (trigger, requête de boucle, index
   partiel, écritures simultanées) est couvert par des **tests sur PostgreSQL (Testcontainers)** : les tests actuels
   n'ont pas de base de données.
5. **Bascule de la stratigraphie** (§6.1, §6.2) : étape 0 d'audit et d'inventaire, reprise, ancienne table gardée en
   lecture seule jusqu'à la livraison suivante.
6. **Autres migrations** (§7), au cas par cas.
7. **Phases et séquences comme sous-types d'UE** (§6.3) : revue du code, puis migration de `phase`.
