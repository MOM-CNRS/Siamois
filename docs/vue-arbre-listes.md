# Vue arbre des listes

Les listes d'un type hiérarchique (lieux, unités d'enregistrement) ont une vue arbre, en plus de la
liste à plat. Un bouton de la barre d'outils (`bi-diagram-3` / `bi-list-ul`) bascule de l'une à
l'autre ; le choix est mémorisé par panneau (`usePaneState("treeView")`) et la liste à plat reste la
vue par défaut. La vue n'existe que pour la liste d'une organisation (`scope == null`), pas pour les
listes embarquées dans une fiche. Un type l'active avec `list.tree: true` dans sa config
(`entities/place/config.tsx`, `entities/recordingUnit/config.tsx`).

## Règles de comportement

1. **Filtre.** Il masque les branches qui ne contiennent aucun résultat.
2. **Tri.** Il réordonne les nœuds à chaque niveau, racines comprises. Le serveur trie les racines et
   les enfants de chaque parent avec le même critère.
3. **Hiérarchie.** Chaque enfant reste rattaché à son parent : le tri ne modifie jamais les relations.
4. **Expansion.** Ni le tri, ni le filtre, ni la recherche n'ouvrent de branche. Une branche ouverte à
   la main le reste quand on change le tri, la recherche ou les filtres, ou après l'enregistrement
   d'une cellule. Il n'y a pas de « tout déplier » pour le moment.
5. **Contexte.** Un parent qui ne correspond pas au filtre mais mène à un résultat reste visible. Il
   n'est pas compté comme résultat : son chip d'identifiant est en pointillé fin et d'une teinte plus
   claire de la couleur de l'entité (classe `entity-tree-context-row`) ; les résultats gardent le trait
   plein. Au survol, le chip se remplit comme les autres et son libellé repasse en blanc.
6. **Polyhiérarchie.** Une entité présente sous plusieurs parents apparaît dans chaque branche, mais
   reste une seule entité dans les compteurs.

## Compteurs

La réponse des racines (`tree=true`, liste d'organisation) porte deux nombres dans `meta` :

- `total` : le nombre de **racines**. Il sert au paginateur, comme `limit` et `offset`, qui portent sur
  les racines.
- `matchCount` : le nombre d'**entités distinctes qui correspondent**, parents de contexte exclus. Il
  alimente le compteur de la barre d'outils (`sélection/total`). Avec une recherche ou un filtre, c'est
  la taille de l'ensemble des correspondances déjà calculé pour trouver les racines (aucune requête de
  plus) ; sans filtre, c'est le nombre de toutes les entités du contexte (un `COUNT`).

`matchCount` n'est présent que sur les racines en mode arbre (`TreePage`, `ListMeta.of`) : absent des
autres listes et de la réponse des enfants.

## Édition et actions

Le tableau est le même que celui de la liste à plat : mêmes colonnes, mêmes chips, mêmes actions de
ligne, même édition de cellule en place (`CellEditOverlay`, droits et règles de champs inclus).

## Contrat d'API

Paramètre `tree=true` sur :

| Route | Effet |
|---|---|
| `GET .../lists/places`, `GET .../lists/recording-units` | Les racines (entités sans parent). Avec une recherche ou un filtre, les racines qui mènent à un résultat. |
| `GET .../places/{id}/children`, `GET .../recording-units/{id}/children` | Les enfants du parent, limités aux branches menant à un résultat quand la liste est filtrée. |

« Filtré » veut dire : une recherche, un filtre de colonne **ou un filtre par champ de formulaire**
(`FieldQuery.filtered()`, pris en compte par `FilterDTO.hasUserFilters()`). Un simple tri ne compte pas.

Chaque ligne porte :

- `hasChildren` : la ligne a des enfants à afficher (dans la fermeture si la liste est filtrée) ;
- `match` : la ligne correspond au filtre. `false` = parent de contexte. Absent sans recherche ni
  filtre, où toute ligne compte comme résultat.

Côté serveur, la fermeture des ancêtres des résultats est une requête récursive (`findAncestorClosure`
des dépôts) en `UNION`, qui déduplique et ne boucle pas en cas de cycle. `FilterDTO` porte
`rootOnly`, `ancestorClosure` et `matchIds`. Les enfants sont chargés au plus 200 par parent.

## Implémentation front

- `panels/list/useTreeRows.ts` : le hook (react-query) qui produit les nœuds du `TreeTable`, l'état
  ouvert/fermé, le chargement des enfants au premier dépliage et `matchCount`.
- `panels/list/EntityTable.tsx` : rend un `DataTable` (liste à plat) ou un `TreeTable` (vue arbre) avec
  les **mêmes colonnes** ; en vue arbre, l'expandeur est dans la colonne identifiant.
- `styles/main-panel.css` : chaque règle `.p-datatable` de la liste a son double `.p-treetable`, pour
  des lignes identiques.
- **Clé d'un nœud** : son chemin (`racine/parent/id`). C'est ce qui permet à une entité sous deux
  parents d'avoir un état ouvert par occurrence.

## Limites connues

- Pas de glisser-déposer des en-têtes de colonnes en vue arbre (le `TreeTable` n'a pas l'équivalent
  de `resetColumnOrder`) ; l'ordre se règle par le sélecteur de colonnes.
- Le mode `scrollable` du `TreeTable` sépare en-tête et corps en deux tables, ce qui casse les
  colonnes figées : il est désactivé. Le conteneur `.p-treetable-wrapper` défile et l'en-tête colle
  par CSS.
- La sélection est une case par ligne indexée par la position dans l'arbre (le `TreeTable` natif
  cocherait des branches entières). Cocher une occurrence ne coche pas l'autre.
- Pas d'indicateur de chargement pendant le dépliage d'une branche.
- Avec une recherche, seules les racines sont visibles : il faut déplier à la main pour atteindre un
  résultat. Un badge « N résultats » par branche ou un autre mécanisme de recherche reste à tester.

## Choix de conception

- **Un paramètre `tree` sur les listes existantes, pas de nouvelles routes.** Il réutilise tout le
  décodage des filtres, tris et champs de formulaire, sans duplication ; l'ancien JSF faisait pareil
  (un indicateur `rootOnly` sur le même modèle de données). Contrepartie : le paramètre change le sens
  du résultat (racines seulement), sa forme (`hasChildren`, `match`) et la portée de la pagination
  (les racines).
- **`/{id}/children?tree=true` est le point faible.** Ces routes sont la ressource de gestion des
  relations (elles ont aussi `POST` et `DELETE`) : leur faire porter un mode arbre mélange deux
  usages. Cible retenue : un paramètre `parent` (racines, ou l'id d'un nœud) sur la liste elle-même,
  et `/children` laissé à la gestion des relations. Pas encore fait.
- **`matchCount` dans la réponse des racines**, séparé de `total` (qui pagine les racines) : le client
  n'a pas à refaire une requête de liste pour compter.
- **Pas d'ouverture automatique.** L'ancien JSF ouvrait les parents purs en mode filtré ; on a choisi
  de ne rien ouvrir. Contrepartie : avec une recherche, seules les racines sont visibles. Le badge
  « N résultats » par branche, ou un autre mécanisme de recherche, est à tester ensuite.
- **Clé d'un nœud = son chemin**, pour la polyhiérarchie (voir plus haut).

## Question ouverte : un parent qui correspond

Aujourd'hui, avec un filtre, les enfants d'un nœud sont limités à la fermeture des ancêtres des
résultats, **même si le nœud correspond lui-même** : un parent qui correspond mais dont aucun enfant
ne correspond ressemble à une feuille. L'ancien JSF montrait tous les descendants d'un parent qui
correspond. À trancher : règle stricte actuelle (règle 1) ou comportement JSF.

## Arbres dans un contexte (à faire)

Un contexte est une liste filtrée par un ensemble fixe `S` (les UE d'un projet, les enfants d'un
lieu, les descendants d'une UE…). Le JSF le gérait avec un filtre constant (`withConstantFilter`) plus
`rootOnly`, avec le bouton arbre dans la barre d'outils de toute liste dont le type le supporte. Son
défaut : une racine était « une ligne du contexte sans aucun parent », donc une UE du projet A dont le
parent est dans le projet B n'apparaissait jamais (ni racine, ni enfant d'un nœud visible), et les
enfants d'un nœud n'étaient pas limités au contexte.

Règle retenue, la même pour tous les contextes :

- **Racines :** les lignes de `S` dont **aucun parent n'est dans `S`** (et non « sans parent »).
- **Enfants d'un nœud :** ses enfants qui sont dans `S`.
- **Recherche et filtre :** les correspondances sont prises dans `S`, la fermeture des ancêtres aussi :
  aucun ancêtre hors contexte n'est montré.

Cas couverts :

| Contexte | `S` | Racines |
|---|---|---|
| Liste d'une organisation | tout | les lignes sans parent (comportement actuel) |
| UE d'un projet | les UE du projet | celles dont aucun parent n'est dans le projet |
| UE d'une UE | les descendants de l'UE | ses enfants directs (leur parent, l'UE, n'est pas dans `S`) |

API : le contexte est déjà dans le chemin de la liste (`/projects/{id}/recording-units`…). Même mode
sur ces listes, avec `parent` (racines ou id d'un nœud) : le contexte n'est pas à répéter pour chaque
dépliage. Backend : « aucun parent dans `S` » = sous-requête `NOT EXISTS` sur la table de hiérarchie
jointe au filtre de contexte (les contextes actuels sont des égalités simples) ; « descendants d'une
UE » ajoute une requête récursive. Front : `canShowTree` exige aujourd'hui `scope == null` et le
chargement des enfants est écrit pour la liste d'organisation. Premier contexte visé : l'onglet des UE
d'un projet.

## Qualité : fait et reste à faire

Fait :

- **Tests.** Front : `useTreeRows.test.tsx` (racines, feuilles, pas d'ouverture automatique, enfants au
  dépliage sous le chemin du parent, entité sous deux parents, contexte, compteur, erreur, plafond,
  branches conservées au rechargement) et `EntityListPanel.tree.test.tsx` (bascule, pas d'arbre en
  contexte, colonnes de la liste, expandeur, compteur). Backend : `FilterDTOTest` (ce qui rend une
  liste « filtrée »), `FieldQueryServiceQueryTest` (drapeau `filtered`), `SpatialUnitServiceTest`
  (`treeFlags`), `RecordingUnitServiceTest` (drapeaux des racines sans et avec filtre, aucun drapeau
  hors arbre, enfants sans filtre et avec un filtre sans résultat).
- **Erreur de chargement des enfants :** la branche se referme et l'erreur s'affiche au-dessus du
  tableau ; une branche conservée qu'on ne peut pas rouvrir au rechargement reste fermée sans faire
  échouer les racines.
- **Plafond de 200 enfants :** un message d'information s'affiche quand une branche ouverte en contient
  davantage (`list.treeMoreChildren`).
- **`EntityTable.tsx` allégé** de la colonne de sélection de l'arbre (`treeSelectionColumn.tsx`).

Reste à faire :

- **Coût du dépliage filtré :** chaque `children?tree=true` refait la requête de correspondances et la
  fermeture des ancêtres (le JSF gardait la fermeture en mémoire). Pistes : cache de courte durée (la
  clé doit contenir l'utilisateur, l'organisation et les filtres de contexte), ou « ce nœud a-t-il un
  résultat en dessous » sans fermeture globale. Non fait : le risque d'un cache périmé ou partagé entre
  droits demande une décision.
- **`EntityTable.tsx`** porte encore deux tableaux aux propriétés dupliquées (~700 lignes) ; extraire la
  construction des colonnes (elle dépend de l'état d'édition des cellules).
- **Précédent/suivant d'une fiche** ouverte depuis l'arbre suit l'ordre de la liste à plat
  (`rememberListContext`), pas celui de l'arbre.
- **Création dans l'arbre :** l'arbre se recharge avec les branches ouvertes ; une entité créée sous
  une branche fermée reste invisible. Le JSF l'insérait à sa place (`LazyTreeMutator`).
- **Validation contre un backend réel :** tout est vérifié par typecheck, tests unitaires et harnais
  simulé (`?kind=list&type=recordingUnit`, bascule arbre).
