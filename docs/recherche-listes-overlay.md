# Recherche des listes : overlay

Branche `feat/list-search-overlay`. La barre de recherche des listes (tables et arbre) ouvre un overlay inspiré de la recherche globale, mais fondé sur le contexte de la table. La révélation d'une entité dans la table ou l'arbre n'est **pas** faite : voir [plan-revelation-liste-arbre.md](plan-revelation-liste-arbre.md).

## Comportement
- Le champ du toolbar (`ListSearchOverlayBox`) est un point d'entrée en lecture seule : il affiche la recherche appliquée et ouvre l'overlay au clic, à Entrée, espace ou flèche bas.
- Dans l'overlay, la saisie **ne modifie pas la table**. L'aperçu (8 résultats) interroge la liste elle-même, avec ses paramètres (portée, tri, filtres, filtres fixes des onglets) : le total affiché est celui que la table aura.
- **Entrée** ou la ligne « Appliquer les N résultats » : la table reçoit `setSearch(texte)` et `setFilters(...)` (offset remis à 0). **Échap** ferme sans rien appliquer.
- **Clic sur un résultat** : ouvre la fiche en overview (`onOpenOverview`, sinon `onNavigate`) ; le surlignage `overview-open` existant s'applique.
- Les listes embarquées dans une fiche ont le même overlay (portée et filtres fixes déjà dans les paramètres). `RelationsList` utilise encore l'ancienne `ListSearchBox` (filtrage en direct).

## Mots-clés `mot:valeur`
- Générés depuis les colonnes filtrables de la liste (`filterSpecs`) : `keywordsOf()` dans `panels/list/ListSearchOverlay.tsx`. Le nom est le libellé de la colonne sans accents ni espaces (« Type d'UE » → `type-d-ue:`). En cas de doublon, la première colonne l'emporte.
- Retenus : filtres de texte (`contains`) et filtres à options avec `loadOptions` (concept, `in`). Les plages, dates, booléens et spatiaux restent éditables dans la table seulement.
- Texte : devient `{op:"contains", v}`. Options : suggestions à la frappe (`loadOptions(partial)`), le choix devient `{op:"in", v:[id]}` et ses libellés sont ajoutés à `filterOptionsByKey` pour les puces.
- Une valeur d'option tapée à la main sans passer par une suggestion est ignorée (pas d'id).

## Puces de filtres
L'overlay affiche les filtres actuels de la table, avec une croix pour en retirer un (`onFilterRemove`). L'édition riche (`FilterChipBar`) reste dans la table : son `OverlayPanel` passerait sous le fond de l'overlay (z-index 3000).

## Code
| Pièce | Fichier |
|---|---|
| Coquille partagée (portail, champ, dropdown, lignes, boutons de mots-clés, navigation clavier) | `frontend/src/search/SearchOverlay.tsx` |
| Parseur paramétré par la liste de mots-clés (défaut : `projet`) | `frontend/src/search/queryParser.ts` |
| Recherche globale, bâtie sur la coquille | `frontend/src/search/GlobalSearchBar.tsx` |
| Overlay de liste | `frontend/src/panels/list/ListSearchOverlay.tsx` |
| Branchement | `frontend/src/panels/EntityListPanel.tsx` (zone `ListSearchOverlayBox`) |
| i18n | `list.search.apply`, `list.search.keyword` dans `i18n/fr.ts` et `en.ts` |
| CSS | `.sia-search-chips` dans `styles/main-panel.css` |

## Points d'attention
- L'aperçu appelle `render` de la colonne identifiant (libellé) et de la première autre colonne non `unscopedOnly` (sous-titre). Cela suppose un `render` qui renvoie du texte brut ; sinon le libellé retombe sur l'id.
- Les boutons de mots-clés affichent `names[0]` (le libellé), jamais `key` (l'id du champ).
- Les tests d'`EntityListPanel` passent par l'overlay (`typeInSearchOverlay`, `pressEnter`) ; `pageCalls()` ignore les requêtes de comptage (limite 1) et d'aperçu (limite 8).

## À faire plus tard : révéler l'entité dans la table ou l'arbre
Mis de côté (2026-10-20), jugé trop lourd pour l'instant. Plan complet : [plan-revelation-liste-arbre.md](plan-revelation-liste-arbre.md).

- **But :** depuis un résultat de l'overlay, amener la table sur la bonne page (défilement, surlignage temporaire) et, dans l'arbre, ouvrir le chemin jusqu'à l'entité, y compris en polyhiérarchie.
- **Idée centrale :** un endpoint `GET <liste>/position?id=X&<mêmes paramètres>` qui renvoie `{found, rank, offset}`, calculé avec le même tri et les mêmes filtres que la liste. En arbre, il renvoie aussi le chemin (`[{id, rank}]`), restreint à la portée et aux correspondances actives.
- **Par où commencer :** la table seule (lots R1-R2 du plan) est bien moins lourde que l'arbre (R3-R4).
- **À vérifier d'abord :** `parseSort` (`ProjectApiService`) n'a pas de départage par id, et les tris synthétiques (compteurs) sont à contrôler. Sans départage, le rang n'est pas stable.
- **Pourquoi c'est lourd :** pas de moteur de liste commun côté backend (une logique de tri par entité), racines d'arbre paginées, enfants plafonnés à 200 (`CHILDREN_LIMIT`), arbres d'UE au niveau organisation sur l'ancien chemin (`searchRecordingUnit`, pas `HierarchyTreeQuery`).
- **Déclencheur envisagé :** clic ou Entrée = ouvrir la fiche (comportement actuel) ; action secondaire « Voir dans la liste » (icône, Maj+Entrée).
- **Lié :** le problème « avec une recherche, seules les racines sont visibles » de [vue-arbre-listes.md](vue-arbre-listes.md) est en partie traité par cette révélation.
