# Règles conditionnelles des champs

Passation de la phase 1 (moteur de rendu, faite) et cadrage de la phase 2 (configuration par table et
par type, à faire). Rédigé le 2026-09-30, branche `feat/field-rules-engine` (changements indexés, non
commités).

## 1. Ce que fait le moteur (phase 1)

Une colonne de formulaire peut porter des **règles** :

| Règle | Effet |
|---|---|
| `enabledWhen` | Le champ n'est éditable que si la condition est vraie. Sinon il est grisé, en lecture seule. Une valeur déjà saisie est conservée, signalée « incohérente », et peut être vidée (jamais effacée automatiquement). |
| `requiredWhen` | Le champ est requis si la condition est vraie. Jamais requis tant qu'il est désactivé. |
| `options` | Restreint la liste des valeurs proposées selon un autre champ : `RELATED_CONCEPTS` (concepts liés à la réponse d'un autre champ) ou `REF_MATCH` (entités candidates ayant la même valeur d'un champ). |
| `constraints` | Ordre entre deux champs (`GT`/`GTE`/`LT`/`LTE`), vérifié dans les deux sens : bornes dans les sélecteurs, avertissement sur les deux champs si violé. |

Une condition est une feuille `{fieldId, op, values}` (`EQ NEQ IN NOT_IN EMPTY NOT_EMPTY GT GTE LT LTE`)
ou une composition `{all:[…]}`, `{any:[…]}`, `{not:c}`. Les règles **lisent des valeurs, jamais l'état
d'un autre champ** (pas de transitivité).

### Où c'est évalué

- **Front** (TypeScript pur, sans React, embarquable dans l'app mobile hors ligne) : `frontend/src/rules/`.
- **Serveur** au PATCH : `domain/services/form/rules/FieldRulesEvaluator` + `FieldAnswerPatchService`
  (400 si champ désactivé rempli, requis vidé, contrainte violée, concept hors liste liée) et
  `ProjectApiService#checkAnswerRules` pour les projets. Seuls les champs **écrits** sont contrôlés :
  une incohérence déjà présente ailleurs ne bloque jamais un enregistrement. Le chemin mobile
  « lenient » journalise au lieu de refuser.
- Les deux évaluateurs partagent `src/test/resources/form-rules/cases.json`
  (`FieldRulesEvaluatorConformanceTest` côté Java, `frontend/src/rules/evaluate.conformance.test.ts`
  côté front). **Toute nouvelle sémantique s'ajoute d'abord dans ce fichier.**

### Format (fil = futur stockage)

Défini une seule fois dans `domain/models/form/rules/FieldRulesJson.java`. Un concept est désormais
stocké et envoyé par son id interne, `{conceptId}` (chaîne), que le client compare directement avec
`ResourceRef.resourceId`. `ConceptIdLookup` ne sert plus qu'à convertir les fichiers de mise en page initiale.

### Où les règles circulent

- **Fiche** : `columns[].rules` dans le layout (`FormUiDtoLayoutJson`) → `FormLayoutView` → `evaluateForm`.
- **Liste** : `FieldResource.rules` dans le catalogue de champs (`FieldQueryService#withQuery`, qui lit
  la colonne du formulaire de détail de l'entité). La liste demande en plus les champs que les règles
  lisent (`fields=`) et évalue ligne par ligne : `frontend/src/panels/useRowRules.ts`.
- **Listes filtrées** : `GET /organizations/{id}/concepts?relatedToConceptId=…` ; le type de l'entité
  part en `valueConceptId` ; `REF_MATCH` passe par le filtre de liste `f.<idChamp>=<id>`.

### Règles actuellement définies (mise en page initiale `form-layouts/UE.json`)

Forme, profil et orientation d'érosion actifs si la **nature** = « Négative » (thésaurus **th337**, concept 1216) ;
fermeture ≥ ouverture, TAQ ≥ TPQ, Z sup ≥ Z inf. Dans `ActionUnitDetailsForm` : fin ≥ début, ZMAX ≥ ZMIN.
**L'interprétation normalisée n'a plus aucune règle** (voir § 4) : elle proposait les concepts liés au cycle, ce qui ne fonctionne
plus puisque le cycle est masqué et que ses valeurs n'ont pas de lien avec les interprétations.

### Reste de la phase 1

- **Bundle hors ligne** : ajouter `relatedByConcept: {conceptId: [conceptId]}` à ce que l'app mobile
  synchronise (`filterOptionsOffline` / `offlineIsAllowed` existent déjà côté TS). L'endpoint
  `VocabulariesData` est `@Deprecated(forRemoval)` : **confirmer quel endpoint le mobile utilise**.
  Il faut aussi forcer `ConceptService#loadUnloadedRelatedConceptsOf` au build, car ces relations sont
  chargées à la demande depuis Opentheso.
- `REF_MATCH` n'est appliqué que dans le sélecteur, pas contrôlé au PATCH ; aucune règle réelle ne
  l'utilise encore.

## 2. Phase 2 : formulaires composés depuis la configuration (état au 2026-09-30)

La phase 2 a été élargie : les formulaires codés en dur (`*DetailsForm`) de UE, Mobilier, Phase et
Contenant disparaissent, chaque formulaire est composé depuis la configuration du type dans le projet
(groupes, ordre, largeur, règles par champ). Projet et Lieu gardent leur formulaire codé pour l'instant.
Plan : `~/.claude/plans/vectorized-imagining-bird.md`.

**Fait (lots 0 à 2)**

- Catalogue des champs système découplé des mises en page : `SystemFieldSpec` (`hidden`, `readOnly`),
  `SystemFieldCatalog.specsOf`. Les `*DetailsForm` et `*NewForm` sont supprimés.
- Mise en page initiale par table : `src/main/resources/form-layouts/{UE,MOBILIER,PHASE,CONTENANT}.json`
  (`FormLayoutSeeds`). Les concepts des règles y sont cités par identifiants externes et convertis en id
  interne ; une règle dont le concept est inconnu est ignorée avec un avertissement.
- Stockage : `form_config_group` (`FormConfigGroup`) et, sur `field_form_config`, `fk_group_id`, `width`
  (`FieldWidth` : 1/4, 1/2, 3/4, pleine) et `rules` (jsonb). Une configuration « a une mise en page »
  dès qu'elle a un groupe. Les valeurs de concept des règles sont `{"conceptId":"<id interne>"}`.
- `EffectiveFormResolver` : mise en page stockée du type, sinon (type sans configuration) la mise en page de base de la table, sinon
  mise en page initiale + fusion historique des drapeaux actif/obligatoire. Un groupe sans champ actif
  n'est pas une section. Rien ne change pour un projet qui n'a pas de mise en page stockée.
- Écriture (`FormLayoutService`) : `rowsOf`, `ensureLayouts`, `ensureOwnLayout`, `saveArrangement`,
  `setFieldWidth`. **Pas de migration au démarrage** : la première édition d'une table écrit la mise en
  page effective de *toutes* ses configurations existantes (`TableFieldConfigService#legacyLayout`), donc
  rien ne bouge à l'écran. Un champ ajouté à une configuration qui a une mise en page tombe dans le groupe
  « Champs additionnels ».
- Écran JSF (`projectTablesSettings.xhtml`) : une seule table glissable (en-têtes de groupe et champs),
  largeur par champ, ajout/renommage/suppression/déplacement de groupes. Un champ appartient à l'en-tête
  situé au-dessus de lui. Le libellé d'un groupe reste une clé i18n tant qu'il n'est pas modifié.

**Lot 3 (fait) : éditeur de règles**

- Bouton « Règles du champ » sur chaque ligne de l'écran → tiroir `fieldRulesDrawer.xhtml`
  (`FieldRulesEditorBean`). Il édite « Modifiable seulement si », « Obligatoire si » (une comparaison, ou
  plusieurs combinées par tout/au moins une), « Valeurs proposées » (concepts liés à un autre champ) et
  l'ordre avec d'autres champs. Une règle plus complexe (composition imbriquée, filtre `REF_MATCH`) est
  conservée telle quelle, affichée comme « règle avancée », supprimable mais pas modifiable.
- Opérateurs et valeurs selon la famille du champ lu (`RuleFieldFamily` : concept, nombre, date, texte,
  autre). Les valeurs de concept viennent du vocabulaire configuré du champ (id interne).
- Enregistrement : `FormLayoutService#saveRules` passe par `FieldRulesValidator` (champ existant dans le
  même formulaire, pas d'auto-référence, opérateur et valeur compatibles, contraintes entre champs du même
  type ordonnable, 20 conditions au plus) ; `InvalidRulesException` porte les messages affichés dans le
  tiroir. Un champ `is_institution_locked` garde ses règles. La règle configurée remplace toutes celles du champ.
- Les sélecteurs de valeurs de concept chargent la liste complète de l'autocomplétion du champ
  (`fetchAutocomplete` sans saisie) : à surveiller pour un très gros vocabulaire.

**Lot 4 (web, fait) : la liste lit les règles par type**

- Les règles d'une table configurable n'accompagnent plus le catalogue de champs
  (`FieldQueryService#rulesOf` ne renvoie des règles que pour Projet et Lieu, dont le formulaire est fixe).
- `EntityListPanel` charge, pour chaque projet présent dans les lignes déjà reçues, les formulaires par type
  de l'endpoint du projet (`recording-unit-types`, `find-types`, `phase-types`, `container-types` ; clé
  `list.rulesSegment` de la config d'entité) et évalue chaque ligne avec les règles du formulaire de son
  projet et de son type (le premier type de la table pour une ligne sans type) : `panels/list/useTypeRules.ts`, `useRowRules.ts`.
  Les champs lus par ces règles sont demandés (`fields=`) dès la deuxième requête, une fois les projets connus.
- Audit : `FormConfig`, `FormConfigGroup`, `FieldFormConfig` et `ConceptFieldFormConfig` sont `@Audited`
  (relations vers champ, concept, projet et institution en `NOT_AUDITED`).

**Reste à faire**

- Mobile (à discuter) : bundle hors ligne `relatedByConcept` et endpoint de synchronisation à confirmer ;
  pour l'instant l'endpoint des concepts par field code suffit.
- Obligatoire par type dans la liste (seule la fiche l'applique).
- Fait : `_default` est supprimé (chaque table a au moins un type, les clés `_default` des réponses d'API aussi). Plus tard : rendre Projet et Lieu configurables.

### Conception d'origine (avant l'élargissement)


### Pourquoi le catalogue actuel ne suffit pas

Aujourd'hui **une règle est attachée au champ**, identique pour tous les types (un seul formulaire codé
en dur). Dès qu'une règle est configurable, elle peut différer selon le type **et selon le projet** :
la configuration des champs existe déjà à cette granularité.

### Le modèle de configuration existant

- `FormConfig` (`form_config`) = une configuration pour **(institution, projet [null = niveau
  institution], concept du champ de type, valeur du type [null = configuration par défaut])**.
- `FieldFormConfig` (`field_form_config`, héritage JOINED, clé (champ, form_config)) = `is_active`,
  `is_mandatory`, `is_institution_locked`, `position`.
- Héritage : `TableFieldConfigServiceImpl#storedFields` charge la configuration **par défaut** puis
  celle du **type**, la ligne du type l'emportant **champ par champ**.
- `EffectiveFormResolver#resolveEffectiveForm` compose le formulaire par (projet, table, type) :
  retire les champs inactifs, marque les obligatoires, ajoute les champs additionnels. C'est là que
  les règles configurées devraient être fusionnées, comme `isMandatory` l'est déjà.
- L'écran de configuration est en **JSF** (`ProjectTableFieldSettingsBean`, ~970 lignes, portée
  projet). Il n'existe pas d'écran de configuration en React.

### Proposition de conception

1. **Stockage** : une colonne `rules` (jsonb, format `FieldRulesJson`) nullable sur
   `field_form_config`. Elle hérite gratuitement du mécanisme défaut → type et du verrouillage
   institution. Alternative : une table `field_rule` séparée, plus lourde sans bénéfice apparent.
2. **Résolution** : `EffectiveFormResolver` remplace, pour chaque colonne, les règles par celles de la
   configuration effective (type, sinon défaut) si elles existent, sinon celles du code. La fiche
   fonctionne alors sans autre changement : elle charge déjà le formulaire effectif du type.
3. **Catalogue de liste** : ne plus porter une règle unique par champ. Voir la question 1 ci-dessous.
4. **Validation à l'enregistrement** (serveur) : champs référencés existants et de la bonne table,
   valeurs compatibles avec le type de réponse, opérateurs valides pour ce type, pas de contrainte
   d'ordre sur un type non ordonnable, taille raisonnable.
5. **Éditeur** : un constructeur de conditions (champ, opérateur, valeur, all/any/not) qui **réutilise
   les renderers de `fields/registry`** pour saisir la valeur selon le type de réponse.

### Questions ouvertes (à trancher avant d'implémenter)

1. **Clé des règles dans la liste.** Une liste au niveau organisation mélange des lignes de plusieurs
   projets ; les règles dépendent de (projet, type), pas seulement du type.
   - A. Le client évalue, avec des règles chargées par (projet, type) : lazy, pour les projets présents
     dans la page (`GET …/field-rules?projectIds=…`, mis en cache). Garde l'évaluateur unique.
   - B. Le serveur annote chaque ligne avec l'état de ses cellules (l'évaluateur Java existe). Plus
     simple côté client, mais un second chemin d'évaluation et une réponse plus lourde.
   - Recommandation : A. Le mobile a de toute façon besoin des règles par (projet, type) hors ligne.
2. **Granularité du remplacement.** Une règle configurée remplace-t-elle *tout* l'ensemble de règles du
   champ (recommandé, simple à comprendre), ou seulement une clé (`enabledWhen`, `options`…) ?
3. **Règles du code : défauts ou figées ?** Erosion, interprétation, dates : restent-elles des défauts
   surchargeables par configuration, ou verrouillées (institution) ? Recommandation : défauts
   surchargeables, mais jamais supprimables silencieusement (afficher l'origine : code / défaut /
   type).
4. **Où vit l'éditeur.** Dans l'écran JSF actuel (intégrer un îlot React), ou attendre une migration de
   l'écran de configuration en React ? Le choix conditionne le volume de travail.
5. **Champ référencé absent du type.** Une règle lit un champ inactif ou absent pour ce type : le
   traiter comme vide (recommandé) ou refuser à l'enregistrement ? Les champs additionnels ont des ids
   par projet : une règle copiée d'un projet à l'autre doit être ré-associée.
6. **Identification des concepts.** `(thésaurus, concept)` est portable entre instances mais casse
   quand le même concept existe dans plusieurs thésaurus (th252 / th277 / th1295 ici). Pour une règle
   **saisie via l'éditeur**, le concept vient du sélecteur du champ, donc cohérent avec l'instance. Pour
   les règles **du code**, choisir entre : comparer sur l'identifiant de concept seul (le serveur
   enverrait alors une liste d'ids), ou résoudre le thésaurus depuis la configuration du champ.
7. **Audit et droits.** Qui peut éditer les règles (niveau institution vs projet) ? Les révisions
   Envers de `field_form_config` couvrent-elles la colonne ? Le champ `is_institution_locked` doit
   s'appliquer aux règles.
8. **Obligatoire par type dans la liste.** Aujourd'hui seule la fiche l'applique. À décider en même
   temps que la question 1 (même donnée par (projet, type)).

### Lots suggérés

1. Colonne `rules` + fusion dans `EffectiveFormResolver` + validation serveur (la fiche en profite
   immédiatement, testable par API).
2. Règles par (projet, type) pour la liste et le mobile (question 1), y compris le bundle hors ligne.
3. Éditeur de règles (question 4).
4. Journal et droits (question 7).

## 3. Environnement et vérification

- Tests : `export JAVA_HOME=/Users/gregorybliault/Library/Java/JavaVirtualMachines/corretto-17.0.16/Contents/Home; mvn -o test`
  puis `cd frontend && npx vitest run && npx tsc --noEmit -p .`. État au 2026-09-30 : 2694 tests Java,
  719 tests front, tous verts.
- Vérifié à la main dans le navigateur : fiche (champs grisés, avertissements, « Vider », liste filtrée,
  bornes de dates) et liste (cellules grisées, avertissements, dépendances masquées demandées).
- Lancer une instance de test sans toucher à celle d'IntelliJ (port 8099) :
  `DB_URL=jdbc:postgresql://localhost:5432/siamois DB_USERNAME=<user> DB_PASSWORD= SIAMOIS_ADMIN_LOGIN=admin mvn -o spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments="--server.port=8100"`
  (compte du profil `dev`, voir `application-dev.yaml`).
- La base locale `siamois` est **partagée** avec l'instance de l'utilisateur : ne pas y laisser de
  données de test. Pendant les vérifications, l'unité 037 (id 144) a vu sa nature passer à 120 par une
  modification faite dans l'application (valeur d'origine : 118) ; l'unité 038 (id 145) est revenue à vide.
- `application-dev.yaml` contient un jeton GitHub en clair : à révoquer et sortir du dépôt.

## 4. Nature, cycle, agent et interprétation d'une unité d'enregistrement (cadrage du 2026-10-29)

### Quatre champs distincts

| Champ | Code | Concept th337 | Valeurs | Liens `skos:related` dans th337 | Dans la mise en page |
|---|---|---|---|---|---|
| Nature | `SIARU.NATURE` | 1180 | Positive, Négative, Englobante, US construite, Dépôt naturel, Altération, Unité artificielle, Interface, Anomalie | aucun | affiché |
| Cycle géomorphologique | `SIARU.GEOMORPHO` | 1181 | Dépôt, Erosion, Altération, Composite | vers les types US / Fait / Ensemble ; chaque valeur vers les champs qu'elle active (érosion) | **masqué** (`active: false`) |
| Agent géomorphologique | `SIARU.GEOMORPHOAGENT` | 1182 | 4 valeurs | vers les types | **masqué** |
| Interprétation normalisée | `SIARU.INTERPRETATION` | 1183 | 76 valeurs | vers le **type** seulement (US 32, Fait 34, Ensemble 7, Sépulture…) | affiché |

Le cycle et l'agent sont des champs propres à Siamois, sans équivalent dans le référentiel national, et masqués par défaut. La nature
est le champ du référentiel (`nature_UE`).

### Fait pour l'instant

- L'interprétation est **découplée de tout autre champ** : la règle `options` (`RELATED_CONCEPTS` sur le cycle) est retirée de
  `form-layouts/UE.json`. La liste proposée est celle du champ, éventuellement restreinte par type via les collections
  « Enregistrement · Interprétation normalisée — Type » du projet par défaut.
- Forme, profil et orientation d'érosion dépendent de la **nature** (champ -331) = « Négative » (1216), et plus du cycle masqué.
  Le choix de « Négative » est à confirmer : aucune nature ne s'appelle « Erosion » ; c'est celle qui désigne un creusement ou une
  perte de matière.

### À faire : deux dépendances

1. **L'interprétation dépend de la nature.**
   - Mise en page : remettre `rules.options = {kind: RELATED_CONCEPTS, fieldId: -331}` sur l'interprétation (-308). Une ligne.
   - Thésaurus : relier chaque interprétation à ses natures par `skos:related` (symétrique). Ces liens n'existent pas ; ils sont à
     définir (une première proposition par le nom de l'interprétation, à faire relire).
2. **La nature dépend du type de l'unité.** Le moteur ne sait filtrer que par la **valeur d'un autre champ**. Il faut une nouvelle
   option, par exemple `options.kind = "RELATED_TO_TYPE"` : la liste proposée est celle des concepts reliés au **type de la fiche**
   (US, Fait, Ensemble…), sans champ source. À faire :
   - ajouter la règle dans `FieldRulesJson`, `FieldRulesEvaluator`, `FieldAnswerPatchService` (rejet d'un concept hors liste), l'endpoint
     `concepts?relatedToConceptId=` (le type de l'entité part déjà en `valueConceptId`) et l'éditeur de règles ;
   - côté TypeScript : `frontend/src/rules/` ;
   - un cas dans `src/test/resources/form-rules/cases.json` **d'abord** (les deux évaluateurs le partagent) ;
   - thésaurus : relier chaque nature à ses types. Ces liens n'existent pas non plus.

### Liens à faire dans le thésaurus pour la règle d'érosion

À ajouter dans OpenTheso (`skos:related`, symétrique), à la main ou dans `generate_thesaurus_socle.py` (`applies(<concept>, [champs])`) :

| Concept (id th337) | Relié à (champ) | Id du champ |
|---|---|---|
| Nature · **Négative** (1216) | Forme de l'érosion (`SIARU.EROSIONSHAPE`) | à relever dans le RDF |
| Nature · **Négative** (1216) | Profil de l'érosion (`SIARU.EROSIONPROFILE`) | idem |
| Nature · **Négative** (1216) | Orientation de l'érosion (`SIARU.EROSIONORIENTATION`) | idem |

Aujourd'hui ces trois champs sont reliés à la valeur **Erosion** du cycle (1225). Ces liens ne servent plus la règle de formulaire ;
à retirer, ou à garder si le cycle réapparaît (champ masqué pour l'instant).

## 5. Inventaire des champs conditionnés par un autre (toutes les tables, état au 2026-10-29)

Il y a deux sortes de dépendances, qui ne vivent pas au même endroit :

- **Règles de formulaire** : portées par la mise en page (`form-layouts/*.json`, ou le formulaire codé de Projet), évaluées par le
  moteur (§ 1). Elles activent un champ, bornent une valeur ou filtrent une liste selon la valeur d'**un autre champ**.
- **Dépendances du thésaurus** : liens `skos:related` du socle commun (th337, généré par `docs/thesaurus-socle/generate_thesaurus_socle.py`).
  Elles disent qu'un champ ne s'applique qu'à un type (ou une valeur), ou qu'une liste de valeurs dépend du type (ou d'un autre champ).
  Elles ne sont lues que si une règle ou une collection les exploite (§ « Une liste qui dépend du type » du README du thésaurus).

### A. Règles de formulaire en place

| Table | Champ conditionné | Condition | Source |
|---|---|---|---|
| Enregistrement | Forme de l'érosion, Profil de l'érosion, Orientation de l'érosion | actifs seulement si **Nature = Négative** (th337 · 1216, champ -331) | `UE.json` (`enabledWhen`) |
| Enregistrement | TAQ | ≥ TPQ | `UE.json` (`constraints`) |
| Enregistrement | Altitude supérieure (Z sup) | ≥ Z inf | `UE.json` (`constraints`) |
| Enregistrement | Date de fermeture | ≥ date d'ouverture | `UE.json` (`constraints`) |
| Enregistrement | Interprétation normalisée | **aucune règle** (découplée le 2026-10-29, à relier à la nature, § 4) | `UE.json` |
| Projet | Date de fin | ≥ date de début | `ActionUnitDetailsForm` (codé) |
| Projet | Date de fin de terrain | ≥ date de début de terrain | `ActionUnitDetailsForm` (codé) |
| Projet | ZMAX | ≥ ZMIN | `ActionUnitDetailsForm` (codé) |
| Projet | Lieu principal (commune) | suggestions INSEE (source de lieux, `placeSources`) | `ActionUnitDetailsForm` (codé) |
| Projet | Contexte spatial (lieux précis) | adresses GéoPlateforme **filtrées par la commune choisie** dans « Lieu principal » (toutes tant qu'elle n'est pas choisie) | `ActionUnitDetailsForm` (codé) |
| Mobilier, Phase, Contenant, Document, Étude, Lieu | aucune | aucune règle de champ dans les mises en page livrées | `MOBILIER.json`, `PHASE.json`, `CONTENANT.json`, `DOCUMENT.json`, `STUDY.json` |

Un projet peut en ajouter dans l'écran de configuration des tables (« Règles du champ », § 2, lot 3) : elles sont stockées avec la mise
en page du type et ne figurent pas ici.

### B. Dépendances décrites dans le thésaurus

Légende : « s'applique à » = le champ n'a de sens que pour ce type ou cette valeur (lien type/valeur → champ) ; « liste selon » = les valeurs
proposées dépendent du pilote (lien pilote → valeur, et collections « Table · Champ — Pilote »).

**Projet** (`SIAAU`)

| Champ | Dépendance |
|---|---|
| Nature de l'aménagement, N° d'arrêté de prescription, Date de l'arrêté, Surface prescrite, Aménageur | s'applique aux types **préventive** et **diagnostic** |
| Sujets | liste selon le **type** de projet (préventive, diagnostic, programmée) |

**Lieu** (`SIASU`)

| Champ | Dépendance |
|---|---|
| Numéro de carte Shom | s'applique au type **zone maritime** |
| Code INSEE | s'applique au type **commune** |
| Lieu-dit | s'applique au type **parcelle** |

**Enregistrement** (`SIARU`)

| Champ | Dépendance |
|---|---|
| Cycle géomorphologique, Agent géomorphologique | s'appliquent aux types **US, Fait, Ensemble** (ces deux champs sont masqués par défaut, § 4) |
| Couleur de la matrice | s'applique au type **US** et aux valeurs de cycle **Dépôt, Altération, Composite** |
| Cycle géomorphologique | liste selon le type : US = dépôt, érosion, altération, composite ; Fait = dépôt, érosion ; Ensemble = dépôt, composite |
| Forme, Profil, Orientation de l'érosion | liés dans th337 à la valeur de cycle **Erosion** ; la règle de formulaire (A) les active selon la **nature = Négative**, **lien à créer** dans le thésaurus (voir § 4) |
| Interprétation normalisée | liste selon le **type** (US 32, Fait 34, Ensemble 7, Sépulture) |
| Attribution chronologique | copie de la liste sous chaque champ concerné, sans dépendance |

**Mobilier** (`SIAS`)

| Champ | Dépendance |
|---|---|
| Matière | liste selon le **type** (céramique, TCA → terre cuite ; métal ; lithique → minéral ; verre → verrerie ; faune, VAB → organique ; prélèvement) |
| Classe | liste selon le **type**, et aussi selon la **matière** (la matière pilote la classe, lecture du référentiel) |
| Interprétation | liste selon le **type** |
| Alerte sanitaire | liste selon le **type** (liste « objet » pour céramique, métal, lithique, verre, faune, TCA ; liste VAB pour le VAB) |
| Nature du prélèvement, Objectif, Condition de conservation, Validité | s'appliquent au type **prélèvement** |
| Potentiel d'étude, Commentaire du potentiel | s'appliquent au type **VAB** |
| État sanitaire | s'applique aux types **métal, faune, VAB** |

**Document** (`SIAD`)

| Champ | Dépendance |
|---|---|
| Catégorie | liste selon le **type** de document (un type reprend les valeurs de sa catégorie) |
| Nature du support | liste selon le **type** |
| Format du fichier | liste selon le **type** |
| Échelle | liste selon le type, **document graphique** seulement |

**Définition de mesure** (`SIAMD`)

| Champ | Dépendance |
|---|---|
| Nature de la mesure (facultative) | liste selon le **type de mesure** |

**Phase, Contenant, Étude, Personne** : aucune dépendance entre champs dans le thésaurus.

### C. Écarts à connaître

- Les dépendances du thésaurus (B) ne produisent un comportement que si une règle de formulaire ou une collection de type les lit.
  Aujourd'hui seules les listes **par type** du projet par défaut (`default-project/definition.json`, collections par type) en profitent ;
  « s'applique à » (champ réservé à un type) n'est pas lu par le moteur de règles ; je n'ai pas vérifié si la configuration du projet par
  défaut le reprend champ par champ.
- Le filtre d'une liste par la valeur d'un autre champ (`RELATED_CONCEPTS`) n'est plus utilisé dans les mises en page livrées depuis le
  retrait de l'interprétation (§ 4). Il reste disponible dans l'éditeur de règles.
- Le filtre d'une liste par le **type de la fiche** (nature → type, matière → type…) n'existe pas dans le moteur : c'est le nouveau type de
  règle à créer (§ 4, point 2).

