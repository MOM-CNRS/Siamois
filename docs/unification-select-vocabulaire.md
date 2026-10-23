# Unification des champs vocabulaire : plus de `*_FROM_FIELD_CODE`

> Décision de principe (2026-10-22), implémentation reportée.

## Décision

Il ne doit plus y avoir de types de champ `SELECT_ONE_FROM_FIELD_CODE` / `SELECT_MULTIPLE_FROM_FIELD_CODE`.
Un champ vocabulaire est un simple `SELECT_ONE` ou `SELECT_MULTIPLE`. D'où vient sa liste est une
**configuration** du champ, pas son type :

- `vocabulary.source.kind` : `FIELD_CODE` | `BRANCH` | `COLLECTION` (déjà modélisé, voir `FieldResource.vocabulary`) ;
- `vocabulary.mode` : `FOLLOW` | `FROZEN` (déjà modélisé, `VocabularyMode`).

Le field code reste une **source parmi d'autres** (et l'identité de certains champs système : `TYPE_FIELD_CODE`,
`CATEGORY_FIELD_CODE`, import, moteur de règles, `ValueMatcherFactory`), pas un type de champ.

## Pourquoi

- Aujourd'hui le type de champ décide du chemin d'appel : `*_FROM_FIELD_CODE` appelle
  `GET /organizations/{id}/concepts?fieldCode=…` (déprécié), `SELECT_ONE/MULTIPLE` appelle `?fieldId=…`.
  Les champs « field code » ignorent donc les restrictions branche/collection et l'état FOLLOW/FROZEN.
- Le serveur sait déjà résoudre la liste à partir du `fieldId` (restriction du champ → thésaurus du projet →
  field code → état follow/frozen). Le client n'a pas à connaître la source.
- Le doc [reflexion-vocabulaire-champs.md](reflexion-vocabulaire-champs.md) sépare déjà les deux axes
  (synchronisation × nombre de sources) : le type du champ n'en fait pas partie.

## Cible

### API
- `FieldResource.answerType` ne renvoie plus que `SELECT_ONE` / `SELECT_MULTIPLE` pour les champs vocabulaire
  (plus de `*_FROM_FIELD_CODE` dans `FieldResource`, `FieldAnswer`, `SelectOneFieldAnswer`, `SelectManyFieldAnswer`).
- Une seule lecture de liste : `GET /organizations/{id}/concepts?fieldId=&projectId=&valueConceptId=`.
  `fieldCode` seul est supprimé après la période de dépréciation.
- `relatedToConceptId` (listes dépendantes `RELATED_CONCEPTS`) passe aussi par `fieldId` ; à vérifier :
  `getRelatedConcepts` doit résoudre le field code d'un champ à liste propre (qui n'en a pas).

### Frontend
- `optionSourceFor` ([optionSources.ts](../frontend/src/fields/optionSources.ts)) : un seul chemin
  (`fetchFieldConceptOptions`) pour tous les champs vocabulaire ; suppression de la branche `fieldCode`,
  de `fetchConceptOptions` et des cas `*_FROM_FIELD_CODE` dans `registerDefaultRenderers.ts` / `registry.ts`.
- `entities/place/CreateForm.tsx` : le champ type est décrit en dur avec `fieldCode: "SIASU.TYPE"` parce qu'aucun
  endpoint ne sert le catalogue de champs de SpatialUnit hors détail. Il faut fournir son vrai `fieldId`
  (catalogue ou endpoint de formulaire de création) avant de supprimer `fetchConceptOptions`.

### Modèle backend
Les types concernés (liste issue d'un grep, à affiner à l'implémentation) :

- Entités : `CustomFieldSelectOneFromFieldCode`, `CustomFieldSelectMultipleFromFieldCode`,
  `CustomFieldConceptFromFieldCode`, `CustomFieldAnswerSelectOneFromFieldAnswerCode` (+ discriminants
  `SELECT_ONE_FROM_FIELD_CODE` / `SELECT_MULTIPLE_FROM_FIELD_CODE`), view models JSF
  `CustomFieldAnswer*FromFieldCodeViewModel`, `CustomFieldAnswerFactory`.
- Déclarations de formulaires : `RecordingUnitForm`, `PhaseForm`, `StudyForm`, `DocumentForm`, `ActionUnitForm`,
  `ContainerForm`, `SpecimenForm`, `SpatialUnit`, `CustomFieldStratigraphicRelationships`.
- Services / API : `FieldType` (le repli `*_FROM_FIELD_CODE → SELECT_ONE` devient inutile), `FieldConfigurationService`,
  `FormService`, `CustomFieldAnswerService`, `TableFieldConfigServiceImpl`, `CustomFieldSeeder`, les projectors
  `*AnswersProjector`, `FieldAnswerWireService`, `FieldAnswerPatchService`, `ProjectApiService`, `VocabularyOpenApiService`.

Piste : le field code passe de « discriminant de classe » à un attribut de la config du champ (source `FIELD_CODE`),
sur `CustomFieldSelectOne/Multiple` ou sur la `FormConfig` du champ. Une migration convertit les lignes
`custom_field` de ces discriminants (y compris `custom_field_answer`) vers `SELECT_ONE` / `SELECT_MULTIPLE` en
conservant le field code comme source.

## Points ouverts

1. Où stocker le field code d'un champ unifié : colonne du champ, ou source `FIELD_CODE` de sa `FormConfig` ?
   (à trancher avec le modèle de [reflexion-vocabulaire-champs.md](reflexion-vocabulaire-champs.md)).
2. Distinction aujourd'hui portée par le type : `SELECT_ONE` + `custom_field_choices` (liste statique) vs
   field code (liste thésaurus). Dans le modèle cible, la liste statique devient le mode `FROZEN`.
3. Compatibilité des clients API existants qui lisent `answerType = *_FROM_FIELD_CODE` : période de double
   publication ou rupture de contrat versionnée (cf. `contract` / B8 de `api-changes-vs-main.md`).
4. Ordre conseillé : (a) frontend sur `fieldId` partout ; (b) `answerType` unifié côté API ;
   (c) migration du modèle et suppression des classes.


## Source d'un champ : thésaurus + sélecteur (2026-10-24)

> Décision, première passe backend et écran de réglage par champ. Reste : API unifiée (`answerType`, `fieldId`)
> et frontend.

### Modèle

Un champ vocabulaire a une **source** = `{thésaurus, sélecteur, mode}` :

- le **thésaurus** dit où l'on cherche ;
- le **sélecteur** dit comment trouver la liste dans ce thésaurus : `FIELD_CODE` (le concept qui porte ce code,
  sa « notation », dans la branche `SIAMOIS#SIAAUTO`), `BRANCH` (un concept de tête) ou `COLLECTION` ;
- le **mode** reste `FOLLOW` ou `FROZEN`.

Le field code n'est donc plus une identité à part ni un type de champ : c'est un paramètre de recherche, comme
la branche et la collection. Il est stocké sur `ConceptFieldFormConfig` (`vocabulary` + `fieldCode`), à côté de
`branchTopTerm` et `collection`.

### Où est lu un code quand rien ne nomme de thésaurus

`FieldCodeResolver`, dans cet ordre :

1. le thésaurus **SIAMOIS** (`SiamoisThesaurus`, th252 par défaut, propriété `siamois.thesaurus.url`) s'il définit le code ;
2. le thésaurus de documentation du **projet** ;
3. le thésaurus de documentation de l'**institution**.

Les champs système, type compris, lisent donc SIAMOIS par défaut. Un code que SIAMOIS définit ne passe jamais au
thésaurus d'un projet de lui-même : il faut pointer le champ sur un autre thésaurus explicitement.

### Stockage

- `ConceptFieldConfig` : `(vocabulary, fieldCode) -> concept`, **global par thésaurus**, plus d'institution ni de projet.
  Un thésaurus se charge une fois (`FieldConfigurationService.loadFieldCodes`), quel que soit le nombre d'utilisateurs.
- `DocumentationThesaurus` : le thésaurus dans lequel une institution, ou un de ses projets, documente ses propres champs
  et types. Remplace le repli institution/projet de l'ancien `ConceptFieldConfig`.
- Les écrans de thésaurus d'institution et de projet appellent toujours `setupFieldConfigurationFor…` : ils chargent
  les codes du thésaurus choisi et le mémorisent comme thésaurus de documentation.

### Écran de réglage par champ

Le tiroir d'un champ (`fieldEditDrawer.xhtml`, `ProjectTableFieldSettingsBean`) propose, à côté de « Branche » et
« Collection », une source **« Code de champ »** :

- le thésaurus se choisit comme pour les autres sources (instance puis thésaurus) ; s'il n'y en a pas encore, le tiroir
  part du thésaurus de documentation du projet, puis de l'institution ;
- la liste des codes est celle que le thésaurus définit (`FieldConfigurationService.findFieldCodesOf`, qui charge le
  thésaurus la première fois) ;
- l'enregistrement écrit `vocabulary` + `fieldCode` sur la `ConceptFieldFormConfig` du champ
  (`FormConfigService.addFieldCodeConfigFor`) ; choisir une branche, une collection ou la source principale
  efface le code, et inversement ;
- pour un champ système, la source « principale » est le thésaurus SIAMOIS.

### Source des types d'une table

Les types d'une table ne sont portés par aucun champ : leur source se règle sur la page de la table
(`ProjectTableTypeSourceBean`), avec les deux mêmes sélecteurs qu'un champ, un code ou une branche. Elle est
rangée par projet et par code de type (`ProjectCodeSource`) et lue en premier par `FieldCodeResolver`.

La liste du type d'une fiche reste celle des **types configurés** du projet, quelle que soit la source. La source ne
fait que fournir les candidats que l'on peut configurer ensuite. Les types configurés sont rangés sous le concept où le
code de type est lu **par défaut**, sans la redirection du projet (`TableFieldConfigServiceImpl#findFieldConcept`) :
changer la source ne déplace donc aucun type déjà configuré. Une collection n'est pas proposée pour les types, car elle
n'a pas de concept racine.

### Hors périmètre de cette passe

- aucune migration : la table `concept_field_config` est supprimée, les thésaurus sont rechargés au démarrage ;
- le repli par code seul (sans champ) ne sait pas désigner un thésaurus : seuls les champs ont une source explicite ;
- `*_FROM_FIELD_CODE` existe encore comme type de champ (voir « Cible » ci-dessus).
