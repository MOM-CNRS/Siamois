# Révéler une entité dans la liste ou l'arbre

**Statut : plan écrit, non démarré, mis de côté (2026-10-20).** Contexte et état de l'overlay : [recherche-listes-overlay.md](recherche-listes-overlay.md).

Suite de l'overlay de recherche des listes (`feat/list-search-overlay`, lots 1-2). Objectif : depuis un résultat de l'overlay, amener la table (ou l'arbre) sur l'entité : bonne page, défilement, surlignage temporaire ; dans l'arbre, ouvrir le chemin jusqu'à elle.

## Décisions de conception

1. **Un endpoint de position par liste**, pas de calcul de rang côté client. `GET <endpoint de liste>/position?id=X&<mêmes paramètres que la liste>` renvoie `{found, rank, offset}`, avec `offset = floor(rank / limit) * limit` (le serveur refuse un offset qui n'est pas un multiple de `limit`).
2. **Le rang réutilise exactement le tri et le filtrage de la liste** : même `Specification` (portée + recherche + filtres), même `Sort` / `orderingSpecificationFor()`. Implémentation la plus sûre : récupérer les ids ordonnés (`findAll(specs, sort)` projeté sur l'id) puis `indexOf(X)`. C'est O(N) en ids, acceptable pour une action ponctuelle. Pas de `row_number()` ni de comparaison de clés : le tri par champ dynamique et par compteur synthétique (`recordingUnitCount`) rendrait cela fragile.
3. **Pas de réutilisation de `/breadcrumbs` pour l'arbre** : il n'est filtré ni par la portée ni par la recherche, inclut le crumb projet, est plafonné à 50 chemins et n'existe ni pour les projets ni pour les lieux. Le chemin est calculé côté serveur.
4. **Cible masquée** (hors filtres, recherche ou portée) : `found: false`, et le front le dit (« masqué par les filtres ») avec un bouton pour les lever. Aucun filtre n'est retiré en silence.
5. **Surlignage par clé, pas par id** : `__treeKey` dans l'arbre (polyhiérarchie : une entité apparaît sous plusieurs parents), l'id dans la liste à plat. Il s'efface seul après ~2,5 s.
6. **Déclenchement dans l'overlay** : un clic ou Entrée sur un résultat garde le comportement actuel (ouvrir en overview). Une action secondaire « Voir dans la liste » (icône sur la ligne, Maj+Entrée) lance la révélation. À confirmer avec l'utilisateur.

## Lot R1 — Backend : position dans une liste à plat
- Contrat partagé : un record de réponse `ListPosition(found, rank, offset)` à côté de `ListMeta` (`ui/api/openapi/v1/generic/response/`).
- Extraire des contrôleurs de liste le parsing des paramètres (`ListWindow`, `FieldListQuery`) pour que `/position` ne le recopie pas.
- Dans chaque service, un `positionOf(id, mêmes specs, mêmes sort)` à côté de `pageAndEnrich`. Ordre de mise en œuvre : unités d'enregistrement (`RecordingUnitService`, variantes projet et organisation), projets (`ActionUnitService.findAccessibleProjects`), lieux (`SpatialUnitService` via `PlaceOpenApiService`), puis mobilier, documents, contenants, études, phases (tris `parse*Sort` de `ProjectApiService`, tous avec départage `id`).
- Vérifier le départage : `parseSort` (l.1829 de `ProjectApiService`) n'a pas de tie-break `id`, ni peut-être les tris synthétiques (compteurs). Corriger avant d'exposer une position, sinon le rang flotte.
- Tests : rang d'un id sous tri par défaut, tri par champ, filtre et recherche actifs ; cible absente → `found: false` ; id hors institution → 404 ; ex æquo départagés par id.

## Lot R2 — Frontend : révélation dans la table
- `entities/listApi.ts` : `fetchListPosition(collectionPath, params, id)` (même construction de requête que `fetchList`).
- `EntityListPanel` : prop `reveal?: { id, nonce }` sur le modèle de `seed` (effet clé sur le nonce). Ordre : `setSearch` / `setFilters` éventuels d'abord, puis, avec les `params` à jour, la position, puis `setPage(offset, limit)`, puis le surlignage. `setSort`, `setSearch` et `setFilters` remettent l'offset à 0 : le `setPage` du reveal passe en dernier, dans un rendu séparé.
- `EntityTable` : prop `revealKey` ; `rowStateClasses` ajoute `reveal-flash` ; après la fin du fetch (`isFetching` faux, `placeholderData: previous` garde l'ancienne page), `scrollIntoView({ block: "center" })` sur la ligne. Le reset `scrollTop = 0` (l.161-169) est sauté tant qu'un reveal est en attente.
- CSS `reveal-flash` dans `styles/main-panel.css`.
- Comparer les ids avec `String(...)` : `overview-open` compare en `===`, avec un risque nombre/chaîne.
- `ListSearchOverlay` : action « Voir dans la liste » qui appelle `onReveal(id)` (nouvelle prop, branchée sur le reveal du panneau). Si l'entité est hors page ou masquée, message sous le champ ou toast.
- Vérifier que le `setPage` du reveal n'est pas enregistré comme état utilisateur (`toPersisted` ne garde pas l'offset, à confirmer par un test).
- Tests : reveal sur une autre page, cible masquée par un filtre, reveal pendant un changement de tri, scroll non écrasé.

## Lot R3 — Backend : chemin dans l'arbre
- `/position` en mode `tree=true` renvoie en plus `path: [{ id, rank }]` : la chaîne `[racine … cible]` avec, pour chaque niveau, le rang dans sa propre liste (racines, ou enfants du parent précédent).
- Choix du chemin : remonter par `findParentEdges` depuis la cible ; à chaque niveau, prendre le parent de plus petit id qui est dans la portée S et, si recherche ou filtre actif, dans la fermeture des correspondances (`HierarchyTreeQuery.ancestorClosureInScope`). Même règle que les breadcrumbs (« parents par id croissant »), donc stable.
- Écrire une variante de `HierarchyTreeQuery.page` qui renvoie des ids ordonnés (aujourd'hui `ids()` est un `findAll(specs)` sans tri) pour calculer les rangs niveau par niveau avec la même logique de racines et d'enfants.
- Attention : les arbres organisation-large des unités d'enregistrement passent par l'ancien chemin (`searchRecordingUnit` / `childrenOfTree` / `ancestorClosure`), pas par `HierarchyTreeQuery`. À traiter explicitement.
- Entités avec arbre : projets, lieux, unités d'enregistrement. Les autres types n'ont pas de mode arbre.
- Tests : entité multi-parents (même chemin à chaque appel), chemin restreint à la portée, chemin sous recherche active, racine hors page.

## Lot R4 — Frontend : révélation dans l'arbre
- Page des racines : `setPage(offset de la racine)` avec le rang du premier élément du `path`.
- `useTreeRows` : exposer `expandPath(path)`. Pré-remplir `openRef` avec les clés de préfixe (`rootId`, `rootId/parentId`, …) avant le fetch des racines : le `queryFn` rouvre déjà récursivement les branches de `openRef` via `fetchChildren`. Si les racines sont déjà chargées, remplir les enfants niveau par niveau (`fetchChildren` + `updateNode`).
- `tree.expanded` est un `useState` local : il reste local, `expandPath` s'appelle depuis le panneau via la valeur de retour du hook.
- Enfants au-delà de `CHILDREN_LIMIT` (200) : `fetchChildren` ne charge que la première page. Pour une cible dont le rang est ≥ 200, demander la page d'enfants qui la contient (`offset = floor(rang / 200) * 200`) et la fusionner ; le bandeau « tronqué » reste. Si ce n'est pas faisable en v1, dire explicitement que la cible est trop loin plutôt que d'échouer en silence.
- Surlignage et défilement par `__treeKey`, comme R2.
- Tests dans `useTreeRows.test.tsx` : chemin à 3 niveaux, racine sur une autre page, enfants chargés ou non, cible multi-parents (une seule branche allumée).

## Lot R5 — Autres types (flat)
Mobilier, documents, contenants, études, phases : `positionOf` déjà couvert par R1 pour ceux qui ont une liste ; ce lot n'est que le branchement et les tests par type, sans nouvelle conception.

## Risques à garder en tête
- Départage instable ou absent sur certains tris (voir R1).
- Rang périmé si l'entité change entre `/position` et la page : revérifier que la ligne est dans la page reçue, sinon message.
- Parents sans accès (404/`found: false`) et entité hors portée.
- Effets concurrents : le `setPage` du reveal est écrasé si un `setSort` / `setSearch` / `setFilters` suit.
- `placeholderData: previous` garde l'ancienne page affichée : attendre la fin du fetch avant de défiler.

## Vérification
- Backend : tests d'intégration du `/position` par type (JDK 17, cf. reference_backend_tests_jdk17).
- Front : `vitest` sur `EntityListPanel`, `useTreeRows`, `ListSearchOverlay`.
- À la main dans le harnais mock : liste longue (page 3), arbre profond avec polyhiérarchie, cible masquée par un filtre, tri changé avant la révélation.
