# Réflexion : sources de vocabulaire des champs concept

> Document de réflexion, rien n'est décidé (2026-09-25).

## Situation actuelle

Un champ concept résout sa liste de valeurs de trois manières :

1. **Field code** : `CustomFieldConceptFromFieldCode.fieldCode` → `ConceptFieldConfig` (projet, sinon institution) → concept parent → son sous-arbre.
2. **Branche** : `ConceptFieldFormConfig.branchTopTerm` → son sous-arbre.
3. **Collection** : `ConceptFieldFormConfig.collection`.

Il existe en plus une quatrième source, statique : `custom_field_choices` (`CustomFieldSelectOne/Multiple.concepts`).

L'**alignement** sémantique du champ est porté ailleurs, par `CustomField.concept`. Le choix de la liste de valeurs ne le remet pas en cause.

Aujourd'hui, `fetchAutocomplete` et `getUrlForConceptField` enchaînent deux mécanismes de résolution. On cherche d'abord la form config (valeur de type → défaut), puis on se rabat sur le field code (projet → institution).

## Rôle du field code

- Les thésaurus des institutions ont chacun leur propre concept « Type », avec des identifiants différents. Le field code est une **clé de correspondance neutre** : il permet d'associer une même définition de champ à n'importe lequel de ces thésaurus.
- Le code l'utilise aussi comme **identité** de certains champs système : `TYPE_FIELD_CODE`, `CATEGORY_FIELD_CODE`, l'import (`resolveConceptByLabel`), le moteur de règles, `ValueMatcherFactory`. Cette identité doit être conservée, quel que soit le modèle retenu.

## Proposition : deux axes indépendants

Plutôt que deux modes qui changeraient à la fois la synchronisation et le nombre de sources, on sépare deux axes :

| Axe | Valeurs |
|---|---|
| Synchronisation | **LIÉ** (la liste suit la source) / **CUSTOM** (copie figée) |
| Nombre de sources | une seule / union de plusieurs |

Modèle possible :

```
mode = LIÉ | CUSTOM
LIÉ    → sources   = [ fieldcode | branche | collection ]*   (union)
CUSTOM → concepts  = [ ... ]                                   (liste explicite)
```

Pourquoi garder les axes séparés : une union liée (par exemple les types céramique plus les types verre) a du sens. Il ne faut pas obliger l'utilisateur à passer en custom, et donc à perdre la synchronisation, simplement pour combiner deux sources.

## Mode lié

**Pour**
- Le thésaurus reste la référence vivante : une correction faite dans OpenTheso est répercutée partout.
- Le field code garde un rôle de vraie source liée, notamment au niveau de l'institution : si le tag `TYPE` change de concept dans le thésaurus, le champ suit. Une branche fixée ne suivrait pas.
- Au niveau d'un projet, une branche ou une collection explicite suffit en général.

**Contre**
- La liste peut changer en cours de fouille. Des réponses existantes peuvent alors se retrouver hors de la liste.

## Mode custom

**Pour**
- Stabilité : un projet de plusieurs années garde un vocabulaire figé.
- Il couvre les listes qu'aucune branche ni collection ne décrit proprement : quelques concepts pris çà et là, une branche dont on retire des éléments.
- La table `custom_field_choices` existe déjà. Il suffit de la déplacer au niveau de la configuration, et la « 4e source » s'intègre alors au modèle.
- Les concepts restent des concepts du thésaurus, avec leurs URI : on perd la synchronisation, pas l'alignement.

**Contre**
- L'écart avec la source grandit sans être visible. Il faudra une vue « différences avec la source » : concepts ajoutés, supprimés ou dépréciés depuis la copie.
- Les concepts référencés par une config custom ne doivent jamais être purgés par une synchronisation.

## Changement de mode

- **Lié → custom** : on copie la liste résolue à cet instant. C'est simple.
- **Custom → lié** : la liste manuelle est perdue et des réponses existantes peuvent sortir de la liste. Il faut au moins un avertissement.

## Points transverses

- **Résolution** : une seule chaîne, (projet, valeur de type) → (projet, défaut) → (institution, défaut). Les valeurs par défaut de l'institution sont de vraies lignes `FormConfig` avec `actionUnit = null`, et un projet ne stocke que ce qu'il surcharge. C'est préférable à une copie de toute la configuration à la création du projet : pas de backfill quand on ajoute un champ, pas de milliers de lignes dupliquées.
- **Synchronisation des sous-arbres** : `saveAllSubConceptOfIfUpdated` et `existing_hash` sont aujourd'hui rattachés à `ConceptFieldConfig`. Il faudrait les rattacher au concept de tête de branche, partagé entre toutes les configs qui y pointent, et ignorer les configs custom.
- **Code qui lit `ConceptFieldConfig` directement**, à revoir : `fetchAllConfiguredVocabularies` (API vocabulaires org), `findVocabularyUrlOfInstitutionId/ActionUnitId`, `ConceptService.resolveConceptByLabel`, `fetchAutocompleteRelated`.
- **Concurrence** : les configs par défaut, dont `valueConcept` est NULL, ne sont pas protégées par `uk_form_config_scope`. Il faut réutiliser la garde applicative de `TableFieldConfigServiceImpl#createOrGetFormConfig`.
- **Migration** : chaque ligne `concept_field_config(institution, actionUnit, fieldCode, concept)` devient une source liée « fieldcode » (ou branche) dans la `FormConfig` par défaut du scope correspondant.

## Questions ouvertes

1. En mode custom, peut-on ajouter des concepts locaux qui n'existent pas dans le thésaurus ? Si oui, on perd l'alignement pour ces valeurs. Piste : non, ou seulement via une proposition au thésaurus.
2. L'alignement (`CustomField.concept`) est-il unique par institution, ou un projet peut-il réaligner un champ sur son propre thésaurus (`setupFieldConfigurationForActionUnit`) ?
3. Une collection ou une liste custom peut contenir des concepts qui ne sont pas sous le concept aligné du champ. Faut-il l'autoriser, afficher un avertissement ou l'interdire ?
4. Un réimport du thésaurus doit-il mettre à jour les sources liées « branche » explicites, ou seulement les sources « fieldcode » ?
