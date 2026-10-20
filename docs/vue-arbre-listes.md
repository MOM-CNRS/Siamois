# Vue arbre des listes

Les listes d'un type hiérarchique (lieux, unités d'enregistrement) ont une vue arbre, en plus de la
liste à plat.

**Mode d'affichage.** Il se choisit dans le menu de l'engrenage, sous le titre « Mode d'affichage » :
une entrée par mode (Liste, Arbre), celui en cours coché. Ce n'est pas un interrupteur à deux états :
les modes forment une liste ouverte (`ListViewMode` dans `panels/list/listViews.ts`), pensée pour
accueillir d'autres vues (carte, chronologie…). Ajouter un mode = une entrée dans `LIST_VIEWS` (libellé,
icône), une règle de disponibilité dans `availableViews`, et son rendu dans `EntityListPanel`. Le mode
est mémorisé par panneau (`usePaneState("viewMode")`) ; s'il n'est plus offert (autre contexte), on
retombe sur la liste. Le menu n'affiche le titre que si la liste offre au moins deux modes.

**Où.** Un type active l'arbre avec `list.tree: true` (`entities/place/config.tsx`,
`entities/recordingUnit/config.tsx`) pour sa liste d'organisation, et `list.treeScopes` liste les
contextes (types de parent) où sa propre liste s'affiche aussi en arbre (voir « Arbres dans un
contexte »).

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

**Côté serveur, les paramètres d'une liste sont des objets.** `ListWindow` (offset, limit, sort, search) et
`TreeRequest` (arbre ou non, `parent`, contexte spatial, `matchCount`) regroupent ce que les contrôleurs
transmettent aux services (`PlaceOpenApiService`, `ProjectApiService`, `OrganizationListService`), et
`ProjectTreeQuery` fait de même pour `ActionUnitService.findAccessibleProjectsTree`. Une liste qui
gagne une option (un mode d'affichage de plus) l'ajoute à ces objets au lieu d'allonger des signatures
déjà à sept paramètres.

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

## Décision : un parent qui correspond

Une branche disparaît si rien ne correspond dedans. Un parent qui correspond mais dont aucun descendant
ne correspond est donc une feuille : on ne montre pas ses descendants pour autant (l'ancien JSF les
montrait tous). Ses enfants ne s'affichent que si l'un d'eux, à un niveau quelconque en dessous, correspond.
C'est ce que fait `HierarchyTreeQuery` (enfants et drapeau `hasChildren` limités à la fermeture des
ancêtres des correspondances), pinglé par `HierarchyTreeQueryTest`.

## Arbres dans un contexte

Un contexte est une liste filtrée par un ensemble fixe `S` (les UE d'un projet, les enfants d'un lieu,
les descendants d'une UE…). Le JSF le gérait avec un filtre constant (`withConstantFilter`) plus
`rootOnly`, avec le bouton arbre dans la barre d'outils de toute liste dont le type le supporte. Son
défaut : une racine était « une ligne du contexte sans aucun parent », donc une UE du projet A dont le
parent est dans le projet B n'apparaissait jamais (ni racine, ni enfant d'un nœud visible), et les
enfants d'un nœud n'étaient pas limités au contexte.

Règle retenue, la même pour tous les contextes :

- **Racines :** les lignes de `S` dont **aucun parent n'est dans `S`** (et non « sans parent »).
- **Enfants d'un nœud :** ses enfants qui sont dans `S`.
- **Recherche et filtre :** les correspondances sont prises dans `S`, la fermeture des ancêtres aussi :
  aucun ancêtre hors contexte n'est montré. Un nœud dont le seul chemin vers une correspondance passe
  par un parent hors contexte n'est pas dans la fermeture (elle se calcule niveau par niveau, en ne
  gardant que les parents de `S`).

Cas couverts :

| Contexte | `S` | Racines | État |
|---|---|---|---|
| Liste d'une organisation | tout | les lignes sans parent | fait (`tree=true`, enfants par `/children`) |
| UE d'un projet | les UE du projet | celles dont aucun parent n'est dans le projet | **fait** (`parent=`) |
| UE d'une UE (onglet « UE ») | tout ce qui est sous l'UE | ses enfants directs | **fait** (route `/children`) |
| Lieux d'un lieu (onglet « Lieux ») | tout ce qui est sous le lieu | ses enfants directs | **fait** (route `/children`) |
| UE d'un lieu | les UE du lieu | idem projet | pas d'onglet aujourd'hui (le point d'API `/places/{id}/recording-units` est un stub 501) |
| Liste des projets | les projets accessibles | ceux dont aucun parent n'est accessible | **fait** (`parent=`) |
| Projets d'un lieu (onglet « Projets ») | les projets accessibles du lieu | idem | **fait** (`parent=`) |
| Mobilier | — | — | à faire (adaptateur par type) |

**Deux familles de contexte.** Une liste « filtrée » (les UE d'un projet) est un ensemble `S` quelconque :
les racines sont les lignes dont aucun parent n'est dans `S`, et les enfants d'un nœud viennent de la
même liste avec `parent=<id>` (`HierarchyTreeQuery`). Une liste « tout ce qui est sous un parent » (les
onglets « UE » d'une UE et « Lieux » d'un lieu) est fermée par ses enfants : ses racines sont les enfants
directs du parent, et les enfants d'un nœud sont ceux de sa propre route `/children?tree=true`, la même
que pour l'arbre de l'organisation. Dans ce cas la restriction « seulement ce qui mène à une
correspondance » donne le bon résultat sans matérialiser `S`. Le compteur `meta.matchCount` s'y obtient
par `?matchCount=true` (une requête récursive : `countDescendants` / `countDescendantsAmong`), demandé
par le seul appel des racines, jamais par les dépliages. Un contexte se nomme dans `list.treeScopes` par
son parent et sa sous-collection (`treeScopeKey` : `project`, `recordingUnit:children`, `place:children` ;
`recordingUnit:parents`, qui liste des ancêtres, n'en fait pas partie).

**Projets.** `GET /api/v1/projects?tree=true` renvoie les projets accessibles dont aucun parent n'est
accessible : un projet sous un parent que la personne ne voit pas est une racine pour elle (les droits
font partie du contexte). Avec `parent=<id>` : les sous-projets accessibles de ce projet. Mêmes champs
`hasChildren`/`match` et `meta.matchCount`. `GET /places/{id}/projects?tree=true` fait de même dans le
lieu, qui est alors une partie du **contexte** (`spatialContextId`) et non un filtre : un projet dont le
parent est dans un autre lieu y est une racine. Les projets n'ont pas de route `/{id}/children` : leur
type déclare `list.treeChildren: "parent"` pour que le front demande les enfants à la liste elle-même.
Côté serveur : `ActionUnitService.findAccessibleProjectsTree` sur `HierarchyTreeQuery`, la page portant
ses drapeaux (`TreePage.hasChildren/isMatch`) puisque `AccessibleProjectForApi` est un record sans
setters ; les conditions « a pour parent » et « aucun parent dans le contexte » sont partagées par tous
les types (`HierarchySpecs`).

**Liste des UE d'un projet** : `GET /api/v1/projects/{id}/recording-units?tree=true` renvoie les racines
du projet (ou, filtré, celles qui mènent à une correspondance) ; avec `parent=<id d'une UE>`, les
enfants de cette UE qui sont dans le projet. Mêmes champs `hasChildren` et `match`, et `meta.matchCount`
compte les UE du projet qui correspondent. `parent` sans `tree=true` est refusé (400). Côté serveur :
`RecordingUnitService.findTreeInScope` avec `RecordingUnitSpec.hasNoParentIn(scope)` (sous-requête
`NOT EXISTS` : aucun parent ne satisfait le contexte).

**Service générique.** Les règles (racines, enfants, fermeture des ancêtres dans le contexte, drapeaux,
compteur) sont dans `HierarchyTreeQuery` (`domain/services/tree`), indépendantes du type ; un type ne
fournit que ses requêtes, par l'interface `HierarchyTreeQuery.Hierarchy` (`hasParent`, `hasNoParentIn`,
`idIn`, arêtes parents et enfants, `ids`, `count`, `page`, `flag`). Les UE l'implémentent
(`recordingUnitHierarchy()`) ; étendre à un autre type, c'est écrire son adaptateur (et, au besoin, les
requêtes d'arêtes de son dépôt), pas recopier les règles. Les tests des règles tournent sur une
hiérarchie en mémoire (`HierarchyTreeQueryTest`). Côté front : `list.treeScopes` d'un type liste
les contextes dont sa propre liste peut s'afficher en arbre (`["project"]` pour les UE) ; en contexte,
`useTreeRows` va chercher les enfants dans la même liste avec `parent` plutôt que par `/children`.

**Tri sur un champ de formulaire.** Le tri sur une colonne de formulaire vit dans une spécification
(`FieldQuery`) mêlée aux filtres ; les chemins en arbre l'ignoraient. Elle est maintenant séparée
(`FieldQuery.ordering()`) et appliquée à chaque niveau, sans les filtres (ils restreignent par la
fermeture des ancêtres, pas par la spécification).

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

*Fonctionnalités*

- **Mobilier :** l'arbre n'est pas branché. La hiérarchie existe en base (`specimen_hierarchy`) mais le JSF
  ne l'exposait pas ; à faire seulement si les données l'utilisent vraiment (un adaptateur
  `HierarchyTreeQuery`, les requêtes d'arêtes du dépôt, `list.tree` sur le type).
- **UE d'un lieu :** pas d'onglet aujourd'hui, et `GET /places/{id}/recording-units` est un stub 501 ; il
  faut d'abord construire l'endpoint et l'onglet (le contexte se traite alors comme celui d'un projet).
- **Recherche en arbre :** sans ouverture automatique, avec une recherche seules les racines sont visibles
  et il faut déplier à la main pour atteindre un résultat. Un badge « N résultats » par branche, ou un
  autre mécanisme, est à tester ensuite. Pas de bouton « tout déplier » pour le moment.
- **Création dans l'arbre :** l'arbre se recharge avec les branches ouvertes ; une entité créée sous une
  branche fermée reste invisible. Le JSF l'insérait à sa place (`LazyTreeMutator` : à la racine, premier
  enfant, parent avec ré-affiliation, frère en dessous).
- **Précédent/suivant d'une fiche** ouverte depuis l'arbre : il suit l'ordre de la liste à plat
  (`rememberListContext`), pas celui de l'arbre.

*Performance*

- **Coût du dépliage filtré :** chaque dépliage d'une liste filtrée (`children?tree=true` ou `parent=`)
  refait la requête de correspondances et la fermeture des ancêtres (le JSF gardait la fermeture en
  mémoire). Pistes : un cache de courte durée (la clé doit contenir l'utilisateur, l'organisation, les
  filtres de contexte et les droits : un cache périmé ou partagé entre droits est le risque), ou « ce
  nœud a-t-il un résultat en dessous » sans fermeture globale. Même coût dans les contextes.

*Interface*

- **Pas d'indicateur de chargement** pendant le dépliage d'une branche.
- **Glisser-déposer des en-têtes** absent en vue arbre (le `TreeTable` n'a pas `resetColumnOrder`).
- **Plafond de 200 enfants** par parent : un message l'indique, pas de pagination.

*Qualité et cohérence*

- **`EntityTable.tsx`** porte encore deux tableaux aux propriétés dupliquées (~680 lignes) ; extraire la
  construction des colonnes (elle dépend de l'état d'édition des cellules).
- **API à unifier :** les listes d'organisation des UE et des lieux déplient par `/{id}/children?tree=true`,
  les projets et les UE d'un projet par `parent=`. La cible est `parent=` partout, `/children` restant à
  la gestion des relations.
- **Validation contre un backend réel :** tout est vérifié par typecheck, tests unitaires et harnais
  simulé (`?kind=list&type=recordingUnit`, mode d'affichage « Arbre »), puis un premier essai
  manuel de l'utilisateur (« ça semble marcher ») ; les contextes (UE d'un projet, projets d'un lieu), le
  compteur avec un filtre par champ et le tri sur une colonne de formulaire sont à passer en revue.

*Avant de fusionner*

- La branche `feat/tree-view` part de `feat/rels` ; elle n'a pas les commits de `feat/search-bar`.
