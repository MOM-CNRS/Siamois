# Vues de liste et état par utilisateur

Trois choses distinctes, qui partagent un même modèle d'état :

1. **L'état de l'utilisateur** : ce qu'un utilisateur a réglé sur une liste ou une fiche (filtres, tri,
   colonnes, taille de page, mode liste/arbre, onglet, sections repliées…) est enregistré en base. Il le
   retrouve à sa prochaine visite, sur n'importe quel navigateur.
2. **Les vues** : l'état d'une liste, nommé, créé par un utilisateur et visible de toute son organisation.
   Elles reprennent le système de vues de l'ancien JSF.
3. **Les onglets de fiche configurés par projet** : l'arrangement des onglets d'une fiche, par projet et type
   (et sous-type), modifiable par un droit dédié. (Des vues par défaut de *liste* avaient été envisagées : elles
   ne sont pas faites, voir plus bas.)

> **Avancement** (branche `feat/saved-views`). Fait : le correctif de la sidebar, les droits, le backend
> (modèle et API des trois), l'état de l'utilisateur côté React (listes et fiches) avec sa robustesse aux
> changements de définition, et les vues côté React sur le modèle de l'ancien header JSF (bouton de
> l'en-tête, `?viewId=`, favori), le volet « Vues » de la sidebar, et les onglets de fiche configurés par projet.
> **Retiré du périmètre** : l'édition des vues par défaut de liste.
> Les sections ci-dessous le disent pour chaque point.

## Principes

- **Une fiche garde son propre état.** L'état d'une fiche (onglet ouvert, ce que chaque onglet a gardé) est
  enregistré par *identifiant de fiche* (`detail:recordingUnit:42`) : ce qu'un utilisateur règle sur une UE
  ne vaut pas pour les autres. Une fiche jamais réglée s'ouvre sur la configuration du projet (voir « Vues et
  onglets de fiche »), puis sur les défauts du code. Les **listes** ne dépendent pas d'une fiche : leur état
  est par vue ou par contexte.
- **Par organisation.** Toutes les lectures et écritures portent l'`organizationId`.
- **Ordre de résolution à l'affichage** : état de l'utilisateur, puis état de la vue (enregistrée ou par
  défaut), puis valeurs par défaut du code et du catalogue. « Réinitialiser » supprime l'état de
  l'utilisateur sur une vue.
- **Aucune écriture tant que l'utilisateur n'a rien changé.** Ce avec quoi le panneau s'ouvre est la base
  de comparaison, pas un état à enregistrer.
- **Dernière écriture gagnante** entre deux onglets du navigateur.

## L'état de l'utilisateur

### Ce qui est enregistré

**Une liste** (`panels/list/listState.ts`) : la taille de page, le tri, les filtres, les colonnes visibles (et
leur ordre) et le mode liste/arbre. **Pas** la page courante ni le texte de recherche : revenir à la page 7
d'une recherche d'hier surprend, revenir à ses filtres et à son tri est le but.

**Une fiche** : un seul état par fiche,

```json
{ "v": 1, "tab": "graph", "tabs": { "fiche": { "foldedSections": { "…": true } },
                                    "graph": { "relationsMode": "list", "showLinks": false,
                                               "relationList": { "filters": {}, "sort": "kind:asc", "limit": 50 } } } }
```

`tab` est l'onglet actif, `tabs` ce que chaque onglet a gardé sous sa propre clé. Chaque onglet n'écrit que
ses morceaux, et l'écriture est **fusionnée** avec l'état existant : changer d'onglet ne perd jamais l'état
des autres. Une liste placée dans un onglet (les UE d'un projet) garde son état avec sa propre clé de liste.

### Clés

Un état est identifié par `(personne, organisation, viewKey)` :

| `viewKey` | Pour |
|---|---|
| `ctx:<type>:global` | la liste d'organisation d'un type (`ctx:recordingUnit:global`) |
| `ctx:<type>:<parentType>:<parentId>-tab` | la liste d'un type dans un onglet de relation (`ctx:recordingUnit:project:5-tab`) : les UE de *ce* projet, arrangées indépendamment de celles des autres projets |
| `detail:<type>:<id>` | une fiche (`detail:recordingUnit:42`) |
| `view:<id>` | une vue enregistrée (quand les vues nommées seront là) |

### API

Contrat dans OpenAPI (tag « Vues »).

- `GET /api/v1/view-states?organizationId&viewKey` : `{ viewKey, state }`, `state` absent si rien n'a été
  modifié.
- `PUT /api/v1/view-states` : `{ organizationId, viewKey, state }` remplace l'état.
- `DELETE /api/v1/view-states?organizationId&viewKey` : réinitialise.

`state` est un JSON libre : sa forme appartient au front, le serveur ne l'interprète pas.

### Côté front

- `api/resources/views.ts` : les appels. `useUserViewState(viewKey, organizationId)` (`panels/list/useUserViewState.ts`) :
  lecture (React Query, mise en cache à vie, écrite à chaque sauvegarde), `save`, `update` (fusion avec le
  dernier état), `reset`. Les modifications partent **600 ms après la dernière**, et aussi à la fermeture du
  panneau et au `pagehide`.
- Listes : `useEntityListData` lit l'état, `useTableState` l'applique **une fois par panneau**, avant le
  premier chargement. La liste n'interroge donc pas deux fois (défauts, puis état enregistré). Le mode
  liste/arbre fait partie de `TableState` (`viewMode`).
- Fiches : `EntityDetailPanel` lit `detail:<type>:<id>` et n'affiche la fiche qu'une fois l'état lu, pour ne pas
  s'ouvrir sur le premier onglet puis sauter. Un aperçu qui nomme un onglet reste prioritaire.
  `usePersistedPaneState` (`panels/pane/persistedPaneState.tsx`) remplace `usePaneState` dans un composant
  d'onglet qui veut garder un morceau d'état. Hors d'une fiche, c'est un `usePaneState`.
- En échec (réseau, serveur), l'état est lu comme « rien d'enregistré » et la liste s'affiche avec ses
  défauts. Une écriture perdue est remplacée par la suivante, qui renvoie l'état entier.
- Les colonnes viennent, dans l'ordre : de l'état de l'utilisateur, de l'ancien `localStorage`
  (`listPreferences.ts`, conservé en repli), puis du catalogue.

## Robustesse aux changements de définition

Entre deux visites, une définition peut changer : un champ supplémentaire est supprimé, une colonne n'est
plus déclarée, un formulaire gagne ou perd une section. Ce qui était enregistré doit alors être écarté sans
casser l'écran. Le serveur répond **400 « Champ inconnu »** à un filtre ou à un tri sur un champ disparu, et
un filtre que personne ne voit ne peut pas être enlevé : il faut donc le retirer avant la première requête.

**Listes** (`panels/list/listStateValidity.ts`, appelé par `useEntityListData` une fois le catalogue chargé) :

- un **filtre** n'est gardé que s'il porte sur une colonne épinglée déclarée filtrable ou sur un champ du
  catalogue qui accepte encore un filtre ;
- un **tri** n'est gardé que s'il porte sur le tri par défaut du type, une colonne épinglée triable ou un
  champ du catalogue encore triable ; sinon, le tri par défaut ;
- les **colonnes** disparues du catalogue sont retirées (`seedVisibleColumns`) ;
- une **taille de page** hors de `PAGE_SIZE_OPTIONS` est ignorée ;
- un **mode** qui n'est plus offert (`availableViews`) retombe sur la liste à plat ;
- **filet de sécurité** : si le serveur répond quand même 400 avec un état enregistré, l'état est abandonné
  une fois (filtres, tri, taille de page, suppression côté serveur) et la liste se recharge avec les
  défauts.

**Fiches** :

- un **onglet** enregistré qui n'existe plus : la fiche s'ouvre sur le premier ;
- les **sections repliées** sont repérées par leur **nom** (le code du formulaire), pas par leur position :
  ajouter ou déplacer une section n'en replie pas une autre. Le nom d'une section disparue n'est jamais lu ;
- chaque morceau d'état d'onglet est validé par son composant (`isValid` de `usePersistedPaneState`) : le
  mode des relations (`graph` ou `list`), l'affichage des liens, et pour la liste de relations les types de
  relation, la colonne et le sens du tri, la taille de page. Une valeur invalide est remplacée par la
  valeur d'origine ;
- l'état d'un onglet supprimé reste en base, sans effet.

**Limites connues** :

- un champ **ajouté** au catalogue n'apparaît pas pour un utilisateur qui a déjà enregistré ses colonnes :
  son choix prime. « Réinitialiser » ramène aux défauts ;
- un filtre « dans » dont une valeur n'existe plus (un concept supprimé) n'est pas détecté côté client : il
  donne une liste vide, pas une erreur ;
- l'état d'une fiche supprimée reste en base (une ligne par utilisateur et par fiche réglée) : aucune purge
  n'existe encore, ni pour les fiches supprimées ni pour les états anciens. À prévoir (tâche planifiée sur
  `updated_at`, les clés `detail:*` et `ctx:*:*:*-tab`).

## Les vues

Une vue est l'état d'une liste, nommé. Toutes les vues d'une organisation sont visibles de tous ses
membres ; **seul le propriétaire** la modifie ou la supprime. Il n'y a pas de case de partage.

**Modèle** (`ui_table_view`, entité `UiTableView`, réutilisée de l'ancien JSF) :

| Champ | Rôle |
|---|---|
| `institution` | l'organisation de la vue (les lignes antérieures n'en ont pas et ne sont pas listées) |
| `owner` | le propriétaire ; absent pour une vue par défaut |
| `kind` | `USER` ou `DEFAULT` |
| `resourceType` | la clé du type dans le registre front (`recordingUnit`) |
| `scopeEntityType` | le type de la fiche parente quand la vue liste ce qui est lié à une fiche |
| `project`, `typeId` | pour une vue par défaut : le projet et le type d'enregistrement |
| `state` (jsonb) | `TableViewState` en version 2 |

`TableViewState` v2 reprend le `TableState` React (`visibleColumns`, `sort`, `search`, `limit`, `viewMode`,
`typeSegment`, `listFilters` aux formes `contains`/`in`/`range`) pour qu'une vue s'applique telle quelle à
une liste. Les champs de la version 1 (JSF) restent lisibles ; les anciens filtres JSF ne sont pas rejoués.
Un champ `tab` (`{label, order}`) est réservé : il permettra à une vue d'être un onglet de fiche (voir
plus bas).

**API** (`ViewControllerApi`) :

- `GET /api/v1/views?organizationId&resourceType&q&limit&offset` : les vues de l'organisation, les plus
  récentes d'abord, sans les vues par défaut.
- `GET /api/v1/views/{id}`, `POST /api/v1/views`, `PUT /api/v1/views/{id}`, `DELETE /api/v1/views/{id}` :
  la modification et la suppression sont réservées au propriétaire (403 sinon). Supprimer une vue supprime
  aussi l'état que chacun avait dessus.

### Titre et compteur d'une liste sur une vue

Sur une vue, le titre du panneau est **le nom de la vue**, à la place du nom du type. Pour le propriétaire c'est un
bouton : un clic le transforme en champ (Entrée ou la sortie du champ enregistre, Échap abandonne), ce qui
renomme la vue sans toucher à son état (`panels/list/ViewTitle.tsx`). Pour un autre utilisateur, c'est du texte.

Le **compteur** du titre est le nombre d'éléments que gardent **les filtres propres de la vue** : ce que
l'utilisateur ajoute ou retire par-dessus ne le fait pas bouger (le nombre sélectionné / total de la barre
d'outils, lui, suit l'état courant). Une vue sans filtre compte toute la collection.

### Le bouton de l'en-tête (reprise de l'ancien modèle JSF)

Pas de menu de vues pour l'instant : on reprend le bouton scindé de l'ancien header JSF (`panels/list/ViewActions.tsx`),
à droite de la barre d'outils du panneau de liste, juste avant le favori. C'est un vrai bouton scindé de
PrimeReact, léger : fond transparent, contour et texte en vert Siamois. Il n'apparaît **qu'une fois la liste modifiée** (sur une
vue : différente de l'état de la vue ; sur la liste par défaut : l'utilisateur a changé quelque chose).

| Situation | Bouton principal | Menu |
|---|---|---|
| vue dont l'utilisateur est propriétaire | Mettre à jour | Enregistrer comme nouvelle vue, Réinitialiser la vue |
| vue d'un autre, ou liste par défaut | Enregistrer comme nouvelle vue | Réinitialiser la vue |

- **Mettre à jour** écrit l'état courant dans la vue (`PUT /views/{id}`) ; ce que l'utilisateur avait changé
  dessus est alors la vue elle-même.
- **Enregistrer comme nouvelle vue** demande un nom dans une petite boîte, crée la vue (`POST /views`), puis
  l'ouvre. La liste d'où viennent les changements revient à son propre état.
- **Réinitialiser la vue** abandonne les changements de l'utilisateur : retour à l'état de la vue, ou aux
  défauts sur la liste par défaut.
- Une vue s'ouvre par son adresse : `…/recording-unit?viewId=12` (`FocusViewBean` → `data-view-id` →
  `NavigationView.viewId`). Le bouton favori d'une liste sur une vue met la vue en favori (adresse avec
  `?viewId=` et titre de la vue) ; supprimer une vue supprime ses favoris. Une vue qui n'existe plus ramène à
  la liste par défaut, avec un message.
- L'état affiché sur une vue est celui de la vue, avec les changements de l'utilisateur par-dessus, clé par
  clé ; il est enregistré sous `view:<id>`.

## Les vues par défaut

La vue que chacun voit avant toute modification personnelle, par organisation, type d'entité, projet et type
d'enregistrement (« la table des UE a autant de vues que de types d'UE »). Ce ne sont pas des vues
personnalisées : il n'y en a qu'une par couple.

- `GET /api/v1/views/default?organizationId&resourceType&projectId&typeId` : `{ view, canEdit }`. `view`
  est absent tant que personne n'en a enregistré une, `canEdit` dit si l'utilisateur peut le faire.
- `PUT /api/v1/views/default` : enregistre. Demande le droit ci-dessous.
- Une contrainte d'unicité partielle (`2026.10.20-0.xml`) garantit une seule vue par défaut par couple.
- **Non utilisée par l'application** : aucune interface n'écrit ni ne lit cette vue par défaut de liste. Elle a
  été retirée du périmètre : les colonnes par défaut se règlent dans le paramétrage du projet
  (`ProjectTableColumnDefault`) et les vues nommées partagent un état de liste. L'API reste, si le besoin revient
  (il faudrait alors que le catalogue de colonnes la lise et que le cache `typesCatalog` soit invalidé).

**Droit** : `PROJECT_EDIT_DEFAULT_VIEWS`, avec ses contreparties `ORGANIZATION_EDIT_DEFAULT_VIEWS` et
`INSTANCE_EDIT_DEFAULT_VIEWS`. Donné aux administrateurs et au gestionnaire de projet, propagé aux profils
existants au démarrage. Sans projet (la liste des projets), seuls les codes d'organisation et d'instance
comptent. Voir `permissions.md`.

## Sidebar

Les volets Favoris et Historique sont en JSF (`template.xhtml`, `focus.xhtml`, `SidebarBean`). Leurs boutons
étaient des `h:commandLink`, qui ignorent `update`, `process` et `oncomplete` : le clic soumettait la page
entière au lieu d'ouvrir le volet avec son animation. Ils sont maintenant des `p:commandLink` (ajax). Seuls
Accueil et Réglages gardent `h:commandLink`, la redirection y étant voulue.

Un troisième bouton, « Vues », ouvre un volet qui liste les vues de l'organisation, avec la même animation que
les deux autres (`p:commandLink` ajax, `handleSidebarToggle('VIEWS')`).

- `SidebarBean.SidebarMode.VIEWS`, le lien `viewsLinkNav` dans `template.xhtml`, le panneau `viewsPanel` dans
  `focus.xhtml`.
- `ViewMenuBean` (session) en est le modèle : une table paginée à défilement virtuel sur
  `ViewOpenApiService.list` (le même service que l'API, donc les mêmes règles), les vues de **tous les types**,
  les plus récemment modifiées d'abord, avec un champ de recherche par titre (frappe, 400 ms).
- Une ligne : icône et couleur du type de liste (celles des favoris), titre, et au survol « par <propriétaire> ».
  Un clic ouvre la liste sur la vue (`flowBean.redirectToFocus("/recording-unit?viewId=12")`).
- La corbeille n'apparaît que sur une vue dont l'utilisateur est propriétaire ; le service refuse de toute
  façon toute autre suppression. Supprimer une vue supprime aussi les favoris qui la désignent, mais le volet
  Favoris ne se rafraîchit qu'à sa prochaine ouverture.
- `PanelFactory.listPathOf(clé)` traduit la clé de registre d'un type (`recordingUnit`) en chemin de liste
  (`/recording-unit`). Une vue d'un type que le serveur ne connaît pas est listée mais ne s'ouvre pas.
- Textes : `nav.views`, `nav.views.hint`, `views.sidebar.*` (`messages*.properties`).
- **Mise à jour par React** : quand React crée une vue (« Enregistrer comme nouvelle vue »), la met à jour ou la
  renomme, il demande à JSF de redessiner le volet (`bridge.refreshViews`, la `remoteCommand`
  `reactAction_refreshViews_<n>` de `reactPanelActions.xhtml`, qui met à jour `.sia-views-table`), comme pour
  les favoris. Le volet se recharge seul à son ouverture ; cet appel sert quand il est déjà ouvert.
- Pas de renommage depuis le volet : le titre d'une vue se choisit à sa création.

## Onglets de fiche configurés par projet

Les onglets d'une fiche sont déclarés dans le code, identiques pour tous. Un projet peut les arranger pour
ses fiches : ceux qui ont le droit de modifier les vues par défaut (`*_EDIT_DEFAULT_VIEWS`) configurent,
tout le monde voit. Il n'y a pas de variante personnelle : ce que l'utilisateur règle lui-même est l'état de
la fiche, par-dessus la configuration. La mise en page (sections, champs visibles) n'en fait pas partie : elle
relève du paramétrage du projet.

**Portée** : une configuration par organisation × projet × type de fiche × sous-type (type d'UE, de projet…).
Sans projet (types qui n'en ont pas) : le projet est absent. Sans sous-type : la configuration vaut pour tous.
À la lecture, celle du sous-type l'emporte, à défaut celle de tous les sous-types.

**Ordre de résolution d'une fiche** : état de l'utilisateur sur cette fiche, puis configuration du projet, puis
onglets du code.

**Contenu** (`fiche_config.tabs`, la forme est celle du front, le serveur n'en borne que la taille, les clés
et leur unicité) :

```json
{ "v": 1, "tabs": [
  { "key": "fiche" },
  { "key": "recording-units", "label": "UE" },
  { "key": "documents", "hidden": true },
  { "key": "custom:3f2a", "kind": "list", "target": "recordingUnit", "label": "US du projet",
    "baseFilters": { "type": { "in": [412] } } } ] }
```

L'ordre est celui du tableau. Un onglet du code absent de la liste s'affiche à la fin : un onglet ajouté dans
une version future apparaît chez tout le monde. Un onglet configuré dont la cible ou un filtre n'existe plus
est ignoré sans bloquer la fiche. Les `baseFilters` d'un onglet de liste sont fixes (ils définissent l'onglet) ;
les filtres de l'utilisateur s'ajoutent par-dessus et sont enregistrés dans l'état de la fiche.

**API** : `GET|PUT|DELETE /api/v1/fiche-configs` (`organizationId`, `resourceType`, `projectId?`, `typeId?`).
`GET` renvoie aussi `canEdit`.

**Lecture dans la fiche** (`panels/detail/ficheConfig.tsx`) : `useFicheConfig` interroge la configuration une fois la
fiche chargée (le projet et le sous-type viennent d'elle : `projectId`, `type.id`), `resolveTabs` produit la
liste d'onglets. La fiche attend la configuration, comme l'état de l'utilisateur, pour ne pas changer d'onglets
après coup. Si la lecture échoue, la fiche garde les onglets du code. L'onglet enregistré par l'utilisateur ou
demandé par un aperçu est cherché dans les onglets résolus ; absent, la fiche s'ouvre sur le premier.

Un onglet de liste configuré est un `EntityListPanel` intégré, avec `fixedFilters` (les `baseFilters`, toujours
appliqués, prioritaires sur le filtre de l'utilisateur sur le même champ, ignorés si le champ n'existe plus,
jamais enregistrés comme état de l'utilisateur) et `stateKey` (sa clé d'état :
`ctx:<cible>:<type>:<id>-tab:<clé de l'onglet>`). Il est **créable** comme l'onglet de relation du code sur la même
cible (`DetailTabDef.relation.creatable` et `createPrefill` : ce que la création lie à la fiche). Si ses filtres
de base fixent **un seul type** (filtre « dans » à une valeur sur le champ type), l'élément créé a ce type, déjà
choisi dans le formulaire mais modifiable (`CreatePrefill.typeId`, `list/fixedType.ts`). Sans onglet de relation
du code sur cette cible, il n'est pas créable : la création ne serait pas liée à la fiche.

**Configurer les onglets** : deux boutons après le dernier onglet de la fiche, pour ceux qui ont le droit
(`canEdit` de `GET /fiche-configs`), en mode écriture : « Ajouter » (+) et « Modifier » (crayon). Ce ne sont pas
des onglets : un clic ouvre l'action sans changer l'onglet affiché.

- « Modifier » ouvre `FicheTabsEditor` : les onglets de la fiche tels que le projet les montre, avec pour chacun
  monter/descendre, un nom (vide = celui du code) et une corbeille. Seuls les onglets de liste se suppriment
  (ceux que le projet a ajoutés, qui disparaissent, et les onglets de relation du code, qui sont masqués et
  peuvent revenir) ; les onglets propres à la fiche (détails, graphe…) ne se suppriment pas. Rien n'est écrit
  avant « Enregistrer » ; « Revenir aux onglets par défaut » supprime la configuration en vigueur. La
  configuration est enregistrée pour le sous-type de la fiche quand elle en a un, sinon pour tous.
- « Ajouter » ouvre un menu : les onglets du code supprimés (pour les remettre, enregistré aussitôt) et « Liste
  filtrée… » (dialogue ci-dessous). Sans rien à remettre, le clic ouvre directement le dialogue ; sans rien à
  ajouter du tout, le bouton n'apparaît pas. Le même menu est dans l'éditeur (« Ajouter un onglet »).

**Ajouter un onglet de liste** (`AddListTabDialog`) : on choisit ce que l'onglet liste (les types que les onglets
de relation de la fiche listent déjà, `DetailTabDef.relation`), on filtre la liste affichée en direct (une
`EntityListPanel` « volatile » : son état n'est ni lu ni gardé pour l'utilisateur), on nomme l'onglet. Les
filtres posés deviennent ses `baseFilters`. Le même dialogue s'ouvre depuis une liste d'un onglet de relation
filtrée, par le bouton « Enregistrer comme onglet… » (visible seulement si la fiche est configurable et que la
liste a des filtres) : la configuration est alors enregistrée aussitôt, avec ce nouvel onglet à la fin, et la
fiche s'ouvre dessus.

Modules : `ficheTabsModel.ts` (lignes de l'éditeur, pures), `FicheTabsEditor.tsx`, `AddListTabDialog.tsx`,
`useFicheTabsSave.ts`, `ficheTabsContext.tsx`.

**État d'avancement** : modèle, API, lecture dans la fiche et éditeur sont faits. Les vues par défaut de
liste (« Enregistrer comme vue par défaut ») sont retirées du périmètre : les colonnes par défaut se règlent
déjà dans le paramétrage du projet et les vues nommées couvrent le partage d'un état de liste.

## Où est le code

- Volet de la sidebar : `ui/bean/ViewMenuBean`, `SidebarBean`, `pages/focus.xhtml`, `template.xhtml`.
- Backend : `domain/models/uiview/` (`UiTableView`, `UiViewKind`, `UserViewState`),
  `infrastructure/database/UiViewRepository`, `UserViewStateRepository`,
  `ui/api/openapi/v1/service/ViewOpenApiService`, `controller/ViewControllerApi`, `dto/view/TableViewState` ;
  onglets configurés : `FicheConfig`, `FicheConfigRepository`, `FicheConfigOpenApiService`, `FicheConfigControllerApi` ; front `api/resources/ficheConfigs.ts`, `panels/detail/ficheConfig.tsx`, `panels/detail/ficheTabsModel.ts`, `FicheTabsEditor.tsx`, `AddListTabDialog.tsx`.
- Front : `api/resources/views.ts`, `panels/list/useUserViewState.ts`, `panels/list/listState.ts`, `panels/list/listStateValidity.ts`,
  `panels/pane/persistedPaneState.tsx`, `panels/list/useTableState.ts`, `panels/list/useEntityListData.ts`,
  `panels/EntityDetailPanel.tsx`.
- Harnais de maquette : `frontend/dev/panels/mockApi.ts` simule `/view-states` en mémoire.
- Tests : `ViewOpenApiServiceTest`, `ViewMenuBeanTest` ; côté front `useUserViewState.test.tsx`, `listState.test.ts`,
  `listStateValidity.test.ts`, `persistedPaneState.test.tsx`, `EntityListPanel.userState.test.tsx`,
  `EntityDetailPanel.test.tsx`. Un mock global de `api/resources/views` est déclaré dans `src/test/setup.ts`. Les
  tests de panneaux attendent deux cycles `act` (`flush`) : la liste lit l'état avant son premier
  chargement.

## Favoris nommés, historique persisté, raccourcis de l'accueil

- **Nom d'un favori** : propre au favori (`bookmark.title_code`), plus celui de la vue ou de la page visée. À la création, un code i18n est enregistré sous forme de texte dans la langue de l'utilisateur ; le nom d'une vue est celui qu'elle a à ce moment. Renommer une vue ne renomme pas ses favoris. Le renommage se fait dans le volet Favoris (crayon) ou par `PATCH /api/v1/bookmarks`. Les anciens favoris encore à l'état de code sont affichés (et édités) avec leur texte.
- **Historique** : la table `user_history` (par personne et organisation) garde les 50 dernières pages (page principale + volet latéral éventuel ; une liste sur une vue est une page distincte). Revisiter une page remonte son entrée. `HistoryBean` l'écrit à chaque ouverture et s'en sert pour alimenter le volet de la sidebar au début d'une session (20 entrées). Lecture : `GET /api/v1/history?organizationId&limit`.
- **Accueil** : une section « Consulté récemment » (8 cartes, `GET /api/v1/history`) avec « Voir tout » qui ouvre le volet Historique de la sidebar JSF (clic sur son entrée, sauf s'il est déjà ouvert). Une entrée avec volet latéral rouvre la page principale puis la fiche en volet. Les favoris n'y figurent pas. Dans « Projets récents », une carte « Créer un projet » (si `GET /api/v1/organizations/{id}/permissions` donne `canCreateProjects`) ouvre le même overlay de création que la liste des projets et mène à la fiche créée.
- **Accès aux données (accueil)** : troisième section, configurée pour toute l'organisation par qui a le droit de modifier les vues par défaut (`ORGANIZATION_EDIT_DEFAULT_VIEWS` ou `INSTANCE_EDIT_DEFAULT_VIEWS`, aucune permission nouvelle). « Ajouter » demande une table et, si elle en a, un type (ou « Tous les types ») : `POST /api/v1/home-accesses` crée la vue correspondante (filtre `in` sur le champ type, propriétaire = celui qui ajoute, visible de toute l'organisation comme toute vue) et la carte (table `home_access`, supprimée avec sa vue). La carte ouvre la table sur cette vue ; son titre est celui de la vue. Retirer une carte laisse sa vue en place. Les membres sans le droit voient les cartes, et rien tant qu'il n'y en a pas.
