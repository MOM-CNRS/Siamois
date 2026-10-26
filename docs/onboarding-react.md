# Le panneau principal en React : guide de reprise

Pour quelqu'un qui a quitté le projet avant la migration JSF → React. Ce document dit **ce qui a changé, comment c'est construit, quelles règles tiennent l'ensemble, et comment travailler dedans**. Il renvoie vers les docs détaillés plutôt que de les répéter.

Pile : React 18, PrimeReact 10.9, TanStack Query 5, Vite, Vitest, TypeScript strict. Code dans `frontend/`.

---

## 1. Ce qui a changé, en une page

| Avant | Maintenant |
|---|---|
| Le contenu du panneau principal (accueil, listes, fiches) était rendu par JSF/PrimeFaces, un `.xhtml` et un bean par entité. | Un **bundle React** est monté dans une `<div>` posée par JSF. Trois panneaux génériques (Accueil, Liste, Fiche) remplacent tout. |
| Les beans JSF lisaient la base et rendaient le HTML. | React parle à l'**API REST `/api/v1/**`** (JWT). Les beans de panneau Java ne sont plus que des **descripteurs** (`AbstractPanel`, `AbstractListPanel`, `AbstractEntityPanel` + une classe par type). |
| Une page par entité. | Une **config par entité** (`EntityTypeConfig`) ; les panneaux ne la connaissent que par le registre. |

**Ce qui reste en JSF :** le shell (`template.xhtml`, barre latérale, topbar, connexion), les pages de paramètres (dont l'éditeur de configuration des champs/formulaires), la gestion de session. React ne remplace pas l'application, seulement le contenu du panneau principal et la barre de recherche du haut.

Historique et reste-à-faire : [react-migration-apres-merge.md](react-migration-apres-merge.md). Plan d'origine : [react-main-panel-migration-plan.md](react-main-panel-migration-plan.md).

---

## 2. Vue d'ensemble

```
Page JSF (focus.xhtml)
 └─ <div class="react-main-panel-mount" data-*>   ← panel/reactPanelMount.xhtml
        │  resources/js/reactPanelBootstrap.js    (choisit QUELS conteneurs monter)
        ▼
   window.SiamoisMainPanel.mountFromDataset(div)  ← mount.ts
        │  parseMountOptions(dataset)             ← mountOptions.ts (vérifie la version du contrat)
        ▼
   App.tsx
     ├─ useNavigation()    reducer pur + codec d'URL + popstate + sync serveur   navigation/
     ├─ providers          QueryClient · Notify (Toast) · WriteMode · Bridge · EntityNavigation · PaneState
     ├─ PaneSplit          panneau principal (jamais remonté) + aperçu optionnel
     │     chaque pane dans un PaneErrorBoundary
     ├─ HomePanel          widgets venant de config.home de chaque entité
     ├─ EntityListPanel    panels/list/*
     └─ EntityDetailPanel  config.detail ; un PaneErrorBoundary par onglet
              │
   entities/registry.ts   EntityKey → EntityTypeConfig   (8 types)
   entities/<type>/       config + api + types (+ CreateForm, DetailHeader, columns…)
   entities/shared/       contrats et helpers communs (types.ts, listApi, typeCatalog, scope, routes…)
   fields/                answerType → renderer / affichage, layout, sources d'options
   rules/                 moteur de règles conditionnelles (sans React, fixture partagée avec Java)
   api/                   client (JWT, retry sur 401), queryKeys, queryClient, errors, generated/
```

Mono-bundle : `npm run build` produit `frontend/dist`, copié par Maven (`frontend-maven-plugin`) dans les ressources web (`/resources/react-main-panel/…`). `tsc`, ESLint et Vitest tournent dans la phase Maven `test`.

---

## 3. Le pont JSF ↔ React

C'est la zone la plus piégeuse, parce que deux mondes se partagent la page.

**Montage.** JSF rend `reactPanelMount.xhtml` : une `<div>` dont les `data-*` portent tout le contexte (type de panneau, entité, id, organisation, mode écriture, locale, jeton CSRF, URI de ressource et titre pour les favoris, noms des `remoteCommand`…). `reactPanelBootstrap.js` parcourt les `<div>` non encore montées et appelle `mountFromDataset`. C'est `mountOptions.ts` qui lit et valide les attributs, pas le script.

**Contrat versionné.** Trois fichiers évoluent ensemble : `reactPanelMount.xhtml`, `reactPanelBootstrap.js`, `mountOptions.ts`. `data-contract-version` doit égaler `MOUNT_CONTRACT_VERSION` : si on change le sens d'un attribut, on incrémente des deux côtés, sinon l'erreur est visible (message dans la div) au lieu d'un panneau à moitié cassé.

**JSF re-rend, React doit suivre.** JSF remplace la `<div>` à chaque re-rendu du flow (bascule du mode écriture, changement d'institution) sans jamais appeler `unmount`. `mount()` balaye donc d'abord les racines dont le conteneur a quitté le DOM (`unmountDetached`). `data-mounted="true"` évite le double montage.

**Le pont inverse (`PanelBridge`).** React n'appelle JSF que via cinq `p:remoteCommand` (`reactPanelActions.xhtml`) : `setOverview`, `closeOverview`, `setMain`, `refreshBookmarks`, `refreshViews`. Ils servent uniquement à garder la session JSF synchrone (F5, historique de la barre latérale, favoris) ; tous prennent leur cible en paramètre, donc restent valides après une navigation côté client. Du côté React, ils arrivent par `panels/pane/bridge.tsx`.

**Authentification.** React n'utilise jamais le cookie de session pour l'API. `auth/sessionAuth.ts` échange la session JSF contre un JWT (`POST /api/auth/session-token`, avec le CSRF rendu par JSF), stocké par `tokenStore`, renouvelé sur 401. Les appels `/api/v1` partent avec `credentials: "omit"`. Côté serveur, la chaîne `/api/v1` est `STATELESS` avec `sessionFixation(none())` : **ne pas y toucher** (c'est le correctif du bug « renvoyé à la connexion au F5 », voir `react-migration-apres-merge.md` §6).

**Mode écriture.** Le bouton lecture/écriture du topbar est dans `FlowBean` (JSF). Il arrive en `data-write-mode` et alimente `WriteModeProvider` ; tout contrôle d'édition en dépend. Défaut : lecture seule.

---

## 4. Les patterns à connaître

### 4.1 Registre d'entités piloté par config (le pattern central)

Chaque type (`project`, `recordingUnit`, `find`, `phase`, `container`, `place`, `document`, `study`) fournit un `EntityTypeConfig<Summary, Detail>` (`entities/shared/types.ts`) : libellés, icône, chemin REST, `api` (list/get/patch/duplicate/delete…), `list` (colonnes épinglées, `schema` du catalogue de champs dynamiques, tri par défaut, arbre, vignettes, création, actions de ligne), `detail` (onglets, en-tête), widget d'accueil, duplication.

Les trois panneaux génériques ne consomment que cette config. **Aucun `switch (entityType)` dans `panels/`.**

> Ajouter une entité = un dossier dans `entities/`, un `registerEntityType(...)` dans `mount.ts`, une clé dans `EntityKey` (`entities/keys.ts`). Rien d'autre à toucher dans les panneaux.

Le registre est typé (`EntityTypeMap`, `getEntityType<K extends EntityKey>`). Une clé littérale donne un type précis ; une clé `string` (URL, pont JSF) renvoie `AnyEntityTypeConfig`, **l'unique endroit où les génériques sont effacés**.

### 4.2 Couches imposées par ESLint

`no-restricted-imports` en `error` :
- le code générique (`panels/`, `fields/`, `components/`, `api/`, `auth/`, `styles/`) n'importe **jamais** un dossier d'entité ;
- un dossier d'entité n'en importe **jamais** un autre (ce qui est partagé monte dans `entities/shared/`) ;
- `rules/` n'importe rien de l'application.

Si ESLint refuse un import, la bonne réaction est de déplacer le code partagé, pas de désactiver la règle.

### 4.3 Navigation : reducer pur + codec d'URL

`navigation/reducer.ts` (état : vue principale, aperçu, pile de focus) et `navigation/url.ts` (encodage base64url, identique au Java) sont **purs**, testés sans DOM. `useNavigation` ne fait que les brancher sur `popstate` et sur la synchro serveur. Navigation entièrement côté client : pas de rechargement JSF entre fiches. Le panneau principal n'est jamais remonté quand l'aperçu s'ouvre (`PaneSplit`).

### 4.4 État serveur : TanStack Query seul

- Un seul `QueryClient` pour tous les montages (principal + aperçu).
- Clés **uniquement** via `api/queryKeys.ts`, jamais en littéral.
- `fetch` confiné à `api/client.ts` (plus `auth/sessionAuth.ts`).
- Pas de copie de donnée serveur dans un `useState`.
- `refetchOnWindowFocus` désactivé ; `shouldRetry` ne réessaie que sur 5xx/réseau, deux fois au plus. Catalogues, règles et formulaires : `staleTime: Infinity`.

### 4.5 Champs : `answerType → renderer`

`fields/registry.ts` associe un type de réponse (`SELECT_ONE`, `DATE`, `NUMBER`…) à un composant d'édition et un affichage ; `registerDefaultRenderers.ts` enregistre ceux par défaut. Les formulaires et cellules sont **pilotés par la config de champs du serveur** (`FieldResource`), pas écrits champ par champ. Les listes ont des colonnes **épinglées** (écrites à la main) plus des colonnes **dynamiques** issues du catalogue (`list.schema.load`) avec sélecteur de colonnes, tri et filtres par `fieldId`.

### 4.6 Moteur de règles conditionnelles (`rules/`)

Évalue `enabledWhen` / `dependsOn` / bornes / contexte d'options, **sans React ni dépendance applicative**. Une fixture de conformité est partagée avec le Java : une seule vérité pour deux langages. Nouvelle sémantique de règle = d'abord dans la fixture, puis dans les deux implémentations. Les formulaires UE/mobilier/phase/contenant sont composés par config (groupes, largeurs, règles par projet × type) ; projet et lieu restent codés en dur. Voir [regles-conditionnelles-champs.md](regles-conditionnelles-champs.md).

### 4.7 Isolation des erreurs

Un `PaneErrorBoundary` par pane et par onglet de fiche : un composant qui plante n'emporte pas le reste. `ApiError`/`messageForError` unifient les erreurs d'API ; les notifications passent par `NotifyProvider` (Toast).

### 4.8 Types de l'API générés et vérifiés

- `frontend/openapi/openapi.json` : instantané du spec, commité.
- `src/api/generated/schema.d.ts` : généré par `openapi-typescript`, commité.
- `src/entities/apiConformance.ts` : vérifie **à la compilation** que chaque DTO écrit à la main ne déclare que des champs présents dans le schéma. Un champ renommé côté Java fait échouer `tsc` en le nommant.
- **Après toute modification d'un DTO Java** : lancer le backend, puis `npm run api:pull`. Rien ne compare encore l'instantané au backend courant : c'est un oubli possible.
- Springdoc rend presque tout optionnel : les types générés **contrôlent** les DTO manuels, ils ne les remplacent pas.

### 4.9 i18n typée

`i18n/fr.ts` est la référence ; `en.ts` est typé dessus, donc une clé manquante casse `tsc`. La locale vient de `data-locale`. Les libellés PrimeReact passent par `primeLocale.ts`.

---

## 5. Où vit quoi

```
src/
  mount.ts, mountOptions.ts, App.tsx      montage, contrat, composition racine
  api/            transport seul (client, errors, queryClient, queryKeys, basePath, entityCache)
    resources/    appels de domaine partagés : bookmarks, views, homeShortcuts, homeAccesses, ficheConfigs, validation
    generated/    schema.d.ts
  entities/
    <type>/       config, api, types, columns, CreateForm, DetailHeader…
    shared/       types.ts (contrats), listApi, deleteApi, typeCatalog, scope, routes, createEntityApi…
    registry.ts, keys.ts, apiConformance.ts
  panels/
    EntityListPanel, EntityDetailPanel, HomePanel
    list/         état et données de liste (listState, tableState, usePagedList, useTreeRows, vues, EntityTable…)
    detail/       fiche : onglets (ficheConfig, ficheTabs*), relationTab, suppression, voisins
    pane/         cadre : PaneSplit, paneState, writeMode, bridge, entityNavigation
  components/     layout/ create/ detail/ duplicate/ home/ table/ relationLinks/
  fields/         registre de renderers, layout, sources d'options, rules/contexts
  rules/          moteur de règles
  navigation/     reducer, url, useNavigation, externalBus, ContextPublisher
  search/         barre de recherche globale (son propre root React, hors arbre des panneaux)
  auth/, notify/, i18n/, styles/
dev/panels/       harnais de mock (panels.html + mockApi.ts)
```

---

## 6. Bonnes pratiques, règles du jeu

1. **Pas de `switch (entityType)` dans les panneaux.** Un comportement propre à une entité va dans sa config (ou un champ optionnel de `EntityTypeConfig`).
2. **Pas de `fetch` hors `api/`.** Pas de clé de requête en littéral.
3. **Pas de `any`** : une exception documentée (`AnyEntityTypeConfig`). Chaque `eslint-disable react-hooks/exhaustive-deps` est un risque de closure périmée ; en ajouter un demande une justification en commentaire.
4. **Une donnée serveur reste dans le cache de requêtes**, pas dans `useState`. Après mutation : invalider/mettre à jour par `queryKeys`.
5. **Pas de scroll de page.** Le shell est en `100vh` fixe : tout panneau scrolle dans sa propre boîte `overflow: auto` bornée. Ne pas compter sur le scroll du document.
6. **Pas de `frozen` PrimeReact** sur les colonnes de tableau (thrash de layout par cellule) : utiliser `panels/list/frozenColumns.ts`.
7. **Styles** : le thème Siamois (`primefaces-themes/theme-base/_primereact.scss`, `docs/theme-class-map.md`) peint les composants PrimeReact ; `styles/main-panel.css` ne contient que ce qui est propre au panneau, sans couleur en dur hors valeur de repli de `var(--…, …)`. Les jetons de statut vivent dans `_variables.scss`.
8. **Géométrie** : les champs géométriques stockent le SRID envoyé par le client, aucune reprojection.
9. **Tests** : un fichier `.test.ts(x)` à côté du code ; la logique (reducer, codec, listState, règles) est testée sans DOM. Un test négatif vaut mieux qu'un test qui passe toujours.
10. **Ne pas casser le contrat de montage** sans bump de version des deux côtés (§3).

---

## 7. Travailler dedans

```bash
cd frontend
npm install
npm run dev          # Vite, harnais de mock sans backend
npm test             # Vitest
npm run lint         # ESLint (les règles de couches y sont)
npx tsc --noEmit     # types
npm run build        # tsc && vite build
npm run api:pull     # re-synchronise openapi.json + schema.d.ts depuis un backend lancé
```

**Harnais de mock** (React sans backend, pour perf et rendu) :
`http://localhost:5174/resources/react-main-panel/dev/panels/panels.html?kind=list|detail|home&type=project&id=1&overview=1&write=0&rows=N`. Le préfixe `base` de Vite est obligatoire. Le mock applique le PATCH naïvement : un `[object Object]` après édition est un artefact du mock, pas un bug de l'app.

**Application complète** : backend Spring Boot + JSF. Un changement Java demande un redémarrage (sinon page JSF blanche) ; un changement React est repris à chaud.

**Tests backend** : Maven doit tourner sous **JDK 17** (`JAVA_HOME` sur un Corretto 17), le JDK par défaut casse Mockito.

---

## 8. Recettes

**Ajouter une entité.** Créer `entities/<type>/` avec `types.ts`, `api.ts`, `columns.tsx`, `config.tsx` (et `CreateForm`, `DetailHeader` au besoin) ; ajouter la clé dans `entities/keys.ts`, l'enregistrer dans `mount.ts`, déclarer son DTO dans `apiConformance.ts`. Côté Java, un descripteur de panneau et les endpoints `/api/v1`.

**Ajouter un type de champ.** `registerFieldRenderer("MON_TYPE", …)` dans `registerDefaultRenderers.ts` (édition + affichage), test à côté. Si le type a des règles ou une source d'options, voir `fields/optionSources.ts` et `rules/`.

**Ajouter un endpoint consommé par React.** Contrôleur dans `ui/api/openapi/v1/…`, puis `npm run api:pull`, puis le DTO manuel côté `entities/` : `tsc` dit s'il diverge du schéma.

**Ajouter un onglet de fiche / une action de ligne.** Dans la config de l'entité (`detail.tabs`, `list.rowActions`), pas dans les panneaux.

---

## 9. Dette connue et à ne pas toucher

À ne pas toucher : reducer de navigation et codec d'URL, `rules/` et sa fixture, la règle de couches ESLint, le principe « registre, pas de `switch` », la stabilité de `PaneSplit`.

Dette (détail et chiffres dans [react-patterns.md](react-patterns.md) et [react-architecture-review.md](react-architecture-review.md)) : gros composants à relire, suppressions `exhaustive-deps`, pas de code splitting (un seul chunk, poids non remesuré), `entities/shared/types.ts` (≈ 500 lignes) à découper, `registerEntityType` qui ne vérifie pas que la config correspond à la clé, instantané OpenAPI non comparé automatiquement au backend.

Fonctionnalités non faites : carte, révélation d'une entité dans la liste ou l'arbre (plan mis de côté). La stratigraphie (onglet de fiche UE, `entities/recordingUnit/graph/`) et la vue arbre du mobilier sont faites.

---

## 10. Pour aller plus loin

| Sujet | Doc |
|---|---|
| Patterns jugés, points faibles, organisation des dossiers | [react-patterns.md](react-patterns.md) |
| Revue d'architecture et améliorations proposées | [react-architecture-review.md](react-architecture-review.md) |
| Bilan architecture React et API | [bilan-architecture-react-et-api.md](bilan-architecture-react-et-api.md) |
| Reste-à-faire après le merge, vérifs en live | [react-migration-apres-merge.md](react-migration-apres-merge.md) |
| Changements d'API par rapport à `main` | [api-changes-vs-main.md](api-changes-vs-main.md) |
| Règles conditionnelles des champs | [regles-conditionnelles-champs.md](regles-conditionnelles-champs.md) |
| Vue arbre, vues sauvegardées, recherche en overlay | [vue-arbre-listes.md](vue-arbre-listes.md), [vues-et-etat-utilisateur.md](vues-et-etat-utilisateur.md), [recherche-listes-overlay.md](recherche-listes-overlay.md) |
| Relations configurables, export, permissions | [modele-relations.md](modele-relations.md), [module-export-modeles.md](module-export-modeles.md), [permissions.md](permissions.md) |
| Thème PrimeReact | [theme-class-map.md](theme-class-map.md) |
