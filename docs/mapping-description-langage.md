# Langage de description de mapping — v3 (Siamois export ⇄ ShareQ3)

Langage JSON déclaratif décrivant « quelles colonnes de quelles feuilles se remplissent avec quels champs
de quelles sources ». Il est partagé par deux outils, pas encore publiés ni l'un ni l'autre :

- **Siamois** (module d'export, `ExportTemplateJson`) : les sources sont des entités lues en base, les
  champs sont désignés par concept Opentheso.
- **ShareQ3** (`OpenModel`, transformation de fichiers) : les sources sont des tables d'un fichier, les
  champs sont désignés par leur nom dans un schéma SKOS.

Le Java de Siamois est l'implémentation de référence de cette version (tests : `ExportTemplateJsonTest`).
ShareQ3 était en « mapping.json v2 » ; la v3 en est un sur-ensemble de forme, voir §6.

## 0. Cœur commun et profils

Le langage a un **cœur commun**, qui ne dépend d'aucun concept ni d'aucun registre, et des **profils** qui
ajoutent ce que chaque outil sait lire.

| | Cœur commun | Profil Siamois | Profil ShareQ3 |
|---|---|---|---|
| Structure | `version`, `sources`, `join`, `root_connections`, `schema_target`, `sheets`, `fields` | idem | idem |
| Sources | `{"kind":"TABLE","name":…}`, `{"join":…}` | `ENTITY`, `PROJECT`, `TECHNICAL` (registres fermés du code) | `TABLE` |
| Champs | nom (chaîne) | `{"concept":…}`, `{"column":…}` | nom (chaîne) |
| Règles | `field`, `constant`, `concat`, `list` | idem | idem |
| Filtres | — | `types` (concepts Opentheso) | à définir |

Un outil qui rencontre une construction hors de son profil la lit sans la perdre, mais ne l'exécute pas : Siamois
signale une source `TABLE` ou un champ nommé comme non pris en charge (`ExportTemplateChecker`), et ShareQ3
pourra faire de même pour `ENTITY` ou `concept`. Le fichier circule ainsi entre les deux sans altération.

## 1. Forme générale

```json
{
  "version": 3,
  "id": "uuid", "name": "SDA — Rapport d'opération", "templateVersion": "1.0.0",
  "fileNamePattern": "OA{oaCode}_{date}",
  "sources": {
    "UE_1":         {"kind": "ENTITY", "entity": "RECORDING_UNIT", "types": [Concept]},
    "OA_1":         {"kind": "PROJECT"},
    "UE_1_project": {"join": {"topterm": "UE_1", "on": {"local_key": "project", "foreign_key": "id"}}}
  },
  "root_connections": [{"target_root": "UE", "source_topterm": "UE_1"}],
  "sheets": {"UE": {"omitIfEmpty": true, "sortBy": ["type_UE"]}},
  "schema_target": {"UE.code_OA_NAT": {"type": "text"}, "UE.type_UE": {"type": "text"}},
  "fields": [
    {"target": "UE.code_OA_NAT", "source": "UE_1_project", "field": {"concept": Concept}},
    {"target": "UE.type_UE",     "source": "UE_1",         "constant": "US"}
  ]
}
```

| Clé | Rôle | Origine |
|---|---|---|
| `version` | version du langage (3) | ShareQ3 (`version`) |
| `id`, `name`, `templateVersion`, `fileNamePattern` | identité du modèle, patron du nom du fichier produit | ajouté |
| `sources` | tables nommées (alias) | ShareQ3 (topterms), nommées |
| `root_connections` | une source primaire alimente une feuille ; l'ordre est celui des feuilles et des unions | ShareQ3 |
| `schema_target` | colonnes de chaque feuille, **dans l'ordre**, avec leur type de sortie | ShareQ3 (`schema_target`) |
| `sheets` | options de feuille : `omitIfEmpty`, `sortBy` (en-têtes) | ajouté |
| `fields` | liste plate des correspondances cible ← source | ShareQ3 (`fields`) |

Une cible s'écrit `Feuille.Colonne` ; le nom de feuille ne contient pas de point, l'en-tête peut en contenir.

## 2. Sources

- `{"kind":"TABLE","name":"Mobilier"}` *(cœur commun)* : une table nommée d'un schéma de fichier. C'est la
  source de ShareQ3 ; Siamois la conserve mais ne la lit pas.
- `{"kind":"ENTITY","entity":…,"types":[Concept]}` : les entités d'un type du projet, filtrées par types
  (liste vide = tous). *Profil Siamois.* Ce filtre est l'extension « Filtre » absente de ShareQ3 ; il est
  exprimé en concepts, donc propre à Siamois tant que ShareQ3 n'a pas défini le sien.
- `{"kind":"PROJECT"}` : une seule ligne, le projet exporté.
- `{"kind":"TECHNICAL","key":…}` : table technique (relations stratigraphiques, hiérarchie…), clé d'un
  registre fermé côté code. Ses colonnes se lisent avec `{"column":…}`.
- `{"join":{"topterm":A,"on":{"local_key":K,"foreign_key":"id"}}}` : source **jointe**, parcourue depuis
  la source `A`. C'est le `join` de ShareQ3. Côté Siamois, `K` est le nom d'une *navigation* du registre
  fermé (`project`, `recordingUnit`, `unit1`…) à cardinalité 1, et `foreign_key` vaut toujours `id` : le JSON
  ne contient jamais de SQL ni de nom de table. Les jointures se chaînent (`A_x` puis `A_x_y`). La nature
  d'une source jointe se déduit de la navigation.

Une source est **primaire** si elle n'est jamais la cible d'un `join` (règle de ShareQ3). Seules les sources
primaires peuvent figurer dans `root_connections` ; plusieurs primaires sur une même feuille = union.
Une même table peut être déclarée plusieurs fois sous des alias différents (par exemple deux filtres de
types sur les unités d'enregistrement, dans deux feuilles ou dans la même).

## 3. Champs (`fields`)

Une entrée = une cellule (cible × source primaire). Au plus une entrée par couple. Trois formes, une seule
à la fois :

| Forme | Sens |
|---|---|
| `"field": F` (+ `"list":{"separator":" & ","sorted":true}`) | valeur d'un champ de la source `source`, éventuellement jointe ; `list` réduit un champ multi-valué en une cellule |
| `"constant": "texte"` | valeur écrite telle quelle (source primaire) |
| `"concat": {"separator":"_","labelSeparator":": ","parts":[P]}` | concaténation (source primaire) |

- `F` = `"nom"` *(cœur commun)* : le nom du champ dans le schéma de la source (ShareQ3) ;
  `{"concept": Concept}` *(profil Siamois)* : champ désigné par son concept Opentheso, système ou additionnel ;
  `{"column": "nom"}` *(profil Siamois)* : colonne d'une source technique.
- `P` = `{"literal":"texte"}` ou `{"label":"libellé","source":alias,"field":F}` ; l'alias d'une partie doit
  descendre de la même source primaire que l'entrée.
- `Concept` = `{"thesaurus":"th230","id":"4290928","uri":"…"}` : thésaurus et concept externes (+ URI),
  jamais d'identifiant de base ; c'est ce qui rend le modèle portable d'une instance à l'autre.
- `status` (`draft`/`validated`, ShareQ3) est accepté et ignoré par Siamois.

Type de sortie, dans `schema_target` : `text` (défaut), `number`, `date` (ISO 8601).

## 4. Rejets (grammaire stricte)

Toute clé inconnue, toute source ou feuille inconnue, un cycle de jointure, un `foreign_key` différent de
`id`, une feuille sans colonne, deux entrées sur la même cellule, une constante ou une concaténation sur une
source jointe sont rejetés au chargement. L'existence des clés techniques et des navigations est vérifiée par
`ExportTemplateChecker`, à l'enregistrement et à l'export, pas par le codec.

## 5. Ce que Siamois fait en mémoire

Le modèle en mémoire reste organisé par feuille → colonne → règles (`ExportTemplateDefinition`), plus
commode pour l'éditeur (la matrice colonnes × sources est exactement la liste `fields`). Le codec traduit :
les alias sont générés (`<Feuille>_<n>`, `<alias>_<navigation>`), les jointures deviennent des chemins de
navigations, et les entrées identiques sur plusieurs sources sont refusionnées en une règle.

## 6. Pour passer ShareQ3 de v2 à v3

Tous les ajouts sont optionnels : un `mapping.json` v2 reste lisible (`version` 2) en lui donnant des alias
égaux aux noms de toptermes. À faire côté ShareQ3 si le langage est partagé :

1. Lire `sources` (alias → `{kind… | join}`) ; un topterme sans déclaration vaut une source primaire du
   même nom. Les `join` migrent des champs vers la déclaration de la source jointe.
2. Accepter `constant` et `concat` dans `fields` (jusqu'ici absents de la comparaison MapForce).
3. Lire le type de sortie dans `schema_target[...]["type"]` et `sheets` (`omitIfEmpty`, `sortBy`).
4. Écrire les sources de fichier en `{"kind":"TABLE","name":…}` et les champs en chaîne (c'est la forme du cœur
   commun) ; les `{concept}`, `{column}`, `ENTITY`, `PROJECT`, `TECHNICAL` et `types` sont à conserver sans les
   exécuter.
5. Définir, si utile, un filtre de lignes propre aux fichiers (par valeur de colonne) : il n'existe pas encore
   dans le cœur commun.

Les moteurs restent séparés (Python + JSONata d'un côté, Java + base de données de l'autre) ; ce qui est
mutualisé est la spécification, les fichiers de modèles et, idéalement, un jeu de cas partagé
(données d'entrée, mapping, sortie attendue).

## Extensions du profil Siamois (v3)

- **Feuille sans source** : une feuille déclarée seulement dans `schema_target` (aucune entrée dans
  `root_connections`) est une feuille sans ligne, avec ses en-têtes ; elle est omise si `omitIfEmpty`.
  Elle sert à porter une feuille dont la source n'existe pas encore.
- **Propriété d'une mesure** : `field: {"concept": {...}, "property": "unit" | "comment"}` lit l'unité
  ou le commentaire d'un champ de mesure ; sans `property`, la valeur saisie.
