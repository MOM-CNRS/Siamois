# Opérateurs des filtres de liste (DataTable / TreeTable)

Branche `feat/complex-filters`. La barre de filtres au-dessus des tables et des arbres permet de choisir, pour chaque filtre, **comment** la colonne est comparée à la valeur : est, n'est pas, contient, ne contient pas, commence par, se termine par, est vide, n'est pas vide (texte) ; est égal à, est entre, supérieur/inférieur ou égal à (nombres, dates) ; est / n'est pas (listes, références, oui/non).

## Comportement
- La puce affiche l'icône du type de valeur, le libellé, la valeur précédée de l'opérateur quand ce n'est pas celui par défaut (« commence par AB », « est vide ») et un chevron.
- L'éditeur de la puce : « Colonne… [opérateur ▾] ⋯ » au-dessus de la valeur (« Saisissez une valeur »). « ⋯ » propose de supprimer le filtre.
- Changer d'opérateur garde la valeur quand c'est possible (« contient AB » → « commence par AB ») ; « est vide » / « n'est pas vide » s'appliquent tout de suite, sans valeur.
- Une négation garde exactement les lignes que la forme positive écarte, **valeurs vides comprises**.
- Seuls les filtres par champ (`f.<fieldId>`, y compris `-901` statut de validation) proposent tous les opérateurs. Les colonnes épinglées à clé nommée (`f.fullIdentifier`, `f.name`…) gardent leur seul opérateur (« contient ») : leurs parseurs serveur (`ProjectListFilter`, `RecordingUnitListFilter`…) ne savent comparer qu'ainsi.
- Les autres barres de filtres (`RelationsList`) et l'overlay de recherche ne changent pas : une `FilterSpec` sans `operators` garde l'éditeur nu.

## Contrat
- `FilterValue` (`entities/shared/types.ts`) : `op` reste la forme de la valeur (compatibilité des vues enregistrées), `operator` facultatif la précise ; `{ op: "empty" }` et `{ op: "notEmpty" }` n'ont pas de valeur. Un opérateur par défaut n'est jamais stocké.
- API : `f.<fieldId>.op=<opérateur>` (`is`, `is_not`, `contains`, `not_contains`, `starts_with`, `ends_with`, `in`, `not_in`, `between`, `empty`, `not_empty`). Absent : comportement historique. Les opérateurs de plage (égal, ≥, ≤) partent en bornes `.from` / `.to`. Détail pour les clients externes : `api-changes-vs-main.md` §12.

## Code
| Pièce | Fichier |
|---|---|
| Opérateurs (énumération, nom sur le fil, négation) | `ui/api/openapi/v1/request/list/FilterOperator.java` |
| Lecture de `f.<id>.op` | `FieldListQuery` |
| Opérateurs acceptés par sorte de colonne, prédicats | `FieldQueryService` (`Kind`), `FilterPredicates` |
| Catalogue côté React (par `FilterKind`, libellés, changement d'opérateur) | `frontend/src/components/table/filterOperators.ts` |
| En-tête de l'éditeur | `frontend/src/components/table/FilterOperatorEditor.tsx` |
| Barre et puces | `frontend/src/components/table/FilterChipBar.tsx` |
| Bornes affichées selon l'opérateur | `frontend/src/components/table/ColumnFilter.tsx` |
| Specs des tables (qui reçoit quels opérateurs) | `frontend/src/panels/list/useListColumns.ts` |
| Encodage `f.*` | `frontend/src/panels/list/tableState.ts` |

## Pistes
- Donner les opérateurs aux colonnes à clé nommée : les faire passer par un `ListOnlyColumn` (comme le statut de validation) plutôt que par les parseurs propres à chaque liste.
- Groupes ET / OU de filtres : non traité (tous les filtres restent combinés en ET).
