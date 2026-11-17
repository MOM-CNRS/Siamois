# Opérateurs des filtres de liste (DataTable / TreeTable)

Branche `feat/complex-filters`. La barre de filtres au-dessus des tables et des arbres permet de choisir, pour chaque filtre, **comment** la colonne est comparée à la valeur. Les opérateurs proposés dépendent du champ (profils de `filterOperators.ts`) :

| Champ | Opérateurs |
|---|---|
| Texte, identifiant, nom | est, n'est pas, contient, ne contient pas, commence par, se termine par, est vide, n'est pas vide |
| Nombre | est égal à, est entre, supérieur ou égal à, inférieur ou égal à, est vide, n'est pas vide |
| Date | est le, est entre, le ou après le, le ou avant le, est vide, n'est pas vide |
| Liste, concept, personne, oui/non, statut | est, n'est pas, est vide, n'est pas vide |
| Type | est, n'est pas (une fiche a toujours un type) |
| Relation (fait partie de, contient, mobilier, stratigraphie, lieux…) | contient, ne contient pas, est vide, n'est pas vide |
| Géométrie | est vide, n'est pas vide (appliqué dès l'ajout du filtre, « n'est pas vide » par défaut) |

## Comportement
- La puce affiche l'icône du type de valeur, le libellé, la valeur précédée de l'opérateur quand ce n'est pas celui par défaut (« commence par AB », « est vide ») et un chevron.
- L'éditeur de la puce : « Colonne… [opérateur ▾] ⋯ » au-dessus de la valeur (« Saisissez une valeur »). « ⋯ » propose de supprimer le filtre.
- Changer d'opérateur garde la valeur quand c'est possible (« contient AB » → « commence par AB ») ; « est vide » / « n'est pas vide » s'appliquent tout de suite, sans valeur.
- Une négation garde exactement les lignes que la forme positive écarte, **valeurs vides comprises**.
- Tous les filtres de la barre passent par les champs (`f.<fieldId>`). Les colonnes épinglées identifiant et nom filtrent par des colonnes réservées (`ColumnDef.filterKey`, `entities/shared/listOnlyColumns.ts`) : `-901` statut de validation, `-902` identifiant (identifiant complet des UE, du mobilier et des projets), `-903` nom (projets). Un filtre enregistré sous l'ancienne clé (`fullIdentifier`, `identifier`, `name`) est repris sous la nouvelle (`withCurrentFilterKeys`, `listStateValidity.ts`).
- Une relation est un champ `SELECT_MULTIPLE_*` qui n'est pas un concept (`isRelationField`, partagé avec l'édition en lot) ; un type est le champ lié à `type` (`isTypeField`).
- Une géométrie (la géométrie propre de l'entité ou un champ additionnel `GEOMETRY`) ne se trie pas et ne se compare à aucune valeur (`filterOp = "presence"` dans le catalogue).
- Basculer sur la vue carte (roue dentée, « Disposition ») ajoute « <géométrie affichée> n'est pas vide », une puce comme les autres (`mapGeometryFilter`, `panels/list/map/mapGeometry.ts`). Rien n'est ajouté à l'ouverture d'une liste déjà en carte, ni si cette colonne a déjà un filtre. Retirée, la carte retrouve son comportement (bandeaux sur les lignes sans géométrie) jusqu'à la prochaine bascule ; quitter la carte ne la retire pas. Changer de « Géométrie affichée » déplace ce filtre sur la nouvelle colonne (`filtersForGeometryChange`), sauf s'il a été retiré ou si la colonne porte un autre filtre.
- Les autres barres de filtres (`RelationsList`) et l'overlay de recherche ne changent pas : une `FilterSpec` sans `operators` garde l'éditeur nu.

## Contrat
- `FilterValue` (`entities/shared/types.ts`) : `op` reste la forme de la valeur (compatibilité des vues enregistrées), `operator` facultatif la précise ; `{ op: "empty" }` et `{ op: "notEmpty" }` n'ont pas de valeur. Un opérateur par défaut n'est jamais stocké.
- API : `f.<fieldId>.op=<opérateur>` (`is`, `is_not`, `contains`, `not_contains`, `starts_with`, `ends_with`, `in`, `not_in`, `between`, `empty`, `not_empty`). Absent : comportement historique. Les opérateurs de plage (égal, ≥, ≤) partent en bornes `.from` / `.to`. Détail pour les clients externes : `api-changes-vs-main.md` §12.

## Code
| Pièce | Fichier |
|---|---|
| Opérateurs (énumération, nom sur le fil, négation) | `ui/api/openapi/v1/request/list/FilterOperator.java` |
| Lecture de `f.<id>.op` | `FieldListQuery` |
| Opérateurs acceptés par sorte de colonne, prédicats | `FieldQueryService` (`Kind`, dont `GEOMETRY`), `FilterPredicates` |
| Colonnes réservées (statut, identifiant, nom) | `ListOnlyColumn` |
| Profils côté React (opérateurs, défaut, libellés, changement d'opérateur) | `frontend/src/components/table/filterOperators.ts` |
| En-tête de l'éditeur | `frontend/src/components/table/FilterOperatorEditor.tsx` |
| Barre et puces | `frontend/src/components/table/FilterChipBar.tsx` |
| Bornes affichées selon l'opérateur | `frontend/src/components/table/ColumnFilter.tsx` |
| Specs des tables (quel profil pour quel champ) | `frontend/src/panels/list/useListColumns.ts` |
| Encodage `f.*` | `frontend/src/panels/list/tableState.ts` |

## Pistes
- Groupes ET / OU de filtres : non traité (tous les filtres restent combinés en ET).
