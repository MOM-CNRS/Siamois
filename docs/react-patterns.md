# Frontend React — patterns en place et verdict

**Périmètre :** `frontend/` (React 18, PrimeReact 10.9, TanStack Query 5, Vite, Vitest).
**Date :** 2026-10-22, branche `main`.
**Taille mesurée :** ≈ 24 800 lignes de TS/TSX (tests compris), 148 fichiers de test.
**Complément de :** [react-architecture-review.md](react-architecture-review.md) (30/09, chiffres périmés : il annonce 12,7k lignes). Ce document dit *quels patterns tiennent et pourquoi* ; l'autre détaille les améliorations proposées, avec effort et risque.

---

## 1. Patterns en place

### 1.1 Registre d'entités piloté par config — **bon, à conserver**

Chaque type d'entité (`project`, `recordingUnit`, `find`, `phase`, `container`, `place`, `document`, `study`) fournit un `EntityTypeConfig` : colonnes, schéma de liste, onglets, en-tête, formulaire de création, actions de ligne, widget Home, duplication. Les panneaux génériques (`EntityListPanel`, `EntityDetailPanel`, `HomePanel`) ne consomment que cette config : aucun `switch (entityType)` dans `panels/`.

Intérêt : ajouter une entité = un dossier dans `entities/` + un enregistrement dans `mount.ts` + une clé dans `EntityKey`.

### 1.2 Couches imposées par ESLint — **très bon**

`eslint.config.js` définit trois règles `no-restricted-imports` en `error` :

- le code générique (`panels/`, `fields/`, `components/`, `api/`, `auth/`, `styles/`) n'importe jamais un dossier d'entité ;
- un dossier d'entité n'en importe jamais un autre (le partage monte dans `entities/`) ;
- `rules/` n'importe rien de l'application.

C'est une propriété vérifiée par l'outil, pas une convention. `tsc` et ESLint tournent dans la phase Maven `test`.

### 1.3 Navigation : reducer pur + codec d'URL — **bon**

`navigation/reducer.ts` et `navigation/url.ts` sont purs, testés sans DOM, avec tests aller-retour contre l'encodage base64url Java. `useNavigation` ne fait que les brancher sur `popstate` et sur la synchronisation avec le serveur JSF.

### 1.4 État serveur : TanStack Query seul — **bon**

- Un unique `QueryClient` pour tous les montages (panneau principal et aperçu).
- Clés centralisées dans `api/queryKeys.ts`, jamais écrites en littéral.
- `fetch` confiné à `api/client.ts` (plus `auth/sessionAuth.ts` pour le jeton de session).
- Pas de copie de donnée serveur dans du `useState`.

### 1.5 Moteur de règles sans framework — **très bon**

`rules/` est autonome et partage une fixture de conformité avec le Java : une seule vérité pour deux langages.

### 1.6 Isolation des erreurs et stabilité des panneaux — **bon**

- `PaneSplit` garde la position du panneau principal stable : ouvrir l'aperçu ne le remonte pas.
- Un `PaneErrorBoundary` par pane et par onglet de détail : un rendu qui plante n'emporte pas le reste.
- `ApiError` / `messageForError` unifient les erreurs d'API ; `shouldRetry` ne réessaie que sur 5xx et erreurs réseau, au plus deux fois.

### 1.7 Contrat de montage JSF versionné — **bon, avec réserve**

Trois artefacts doivent évoluer ensemble : `reactPanelMount.xhtml` (attributs), `reactPanelBootstrap.js` (stub), `mountOptions.ts` (parseur). `MOUNT_CONTRACT_VERSION` transforme un désaccord en erreur visible. Réserve : la version se met à jour à la main des deux côtés (voir §2.8).

### 1.8 Qualité et outillage — **bon**

- `tsc --strict`, ESLint et Vitest dans la phase Maven `test`.
- i18n typée : `en.ts` est typé sur les clés de `fr.ts`, une traduction manquante fait échouer `tsc`.
- Harnais de mock (`dev/panels`) pour contrôler le rendu et la performance sans backend.

---

## 2. Points faibles (état vérifié le 2026-10-22)

| # | Constat | Mesure | Gravité |
|---|---|---|---|
| 2.1 | Registre non typé : `Map<string, EntityTypeConfig<any, any>>`. Chaque panneau travaille sur des données `any`. Le commentaire de `registry.ts` l'assume comme frontière d'effacement de génériques. | 6 `no-explicit-any` hors tests | Moyenne |
| 2.2 | DTO écrits à la main depuis l'OpenAPI (`entities/types.ts`, 500 lignes ; `fields/types.ts`). Un champ renommé côté Java compile, puis casse à l'exécution (cellule vide). Pas de `src/api/generated/`. | — | **Haute** (croît avec chaque endpoint) |
| 2.3 | Suppressions `react-hooks/exhaustive-deps` : chacune est un risque de closure périmée. Le nombre a augmenté depuis le rapport du 30/09 (qui en citait quatre fichiers). | 15 | Moyenne |
| 2.4 | Cache uniforme : `queryClient.ts` ne règle que `retry`. Défaut `staleTime: 0` + refetch au focus. Des `staleTime` existent par requête, mais pas de politique par défaut. | 19 `staleTime` locaux | Faible, correctif trivial |
| 2.5 | Aucun code splitting : zéro `React.lazy`, un seul chunk (≈ 940 Ko selon le rapport, **non remesuré**). | 0 | Moyenne (à mesurer d'abord) |
| 2.6 | Gros fichiers, tous en croissance depuis le rapport : `EntityTable.tsx` 691 (490 au 30/09), `fields/renderers.tsx` 622 (438), `EntityListPanel.tsx` 610, `EntityDetailPanel.tsx` 572 (373), `entities/types.ts` 500 (380), `CellEditOverlay.tsx` 486 (405). | — | Moyenne |
| 2.7 | Fuite d'abstraction : `fields/display.tsx` et `fields/optionSources.ts` connaissent des noms d'entités (`"recording-units"`, types de réponse). Ajouter une entité oblige à toucher du code générique. Le respect de la règle de couches tient seulement parce que ce sont des chaînes et non des imports. | — | Faible |
| 2.8 | Pont JSF : les `remoteCommand` sont résolues par `window[name]`, une faute de frappe est un non-événement silencieux. `MOUNT_CONTRACT_VERSION` est incrémenté à la main côté Java et côté TS. | — | Moyenne |
| 2.9 | Aucune mesure : pas de Sonar ni de couverture sur `frontend/src`, pas d'axe pour l'accessibilité, pas de Prettier. | — | Moyenne |

Pour chaque point, le détail de la correction proposée, l'effort et le risque figurent dans [react-architecture-review.md](react-architecture-review.md) (§2.1 à §2.13).

Autres chiffres utiles : 203 `useState`, 57 `useEffect`, 128 usages de `useQuery`/`useMutation` hors tests. Le ratio effets/état est sain : les effets restent minoritaires parce que l'état serveur passe par TanStack Query.

---

## 3. Ordre de travail recommandé

1. **`queryClient`** : `refetchOnWindowFocus: false` et `staleTime` pour les catalogues, formulaires et règles (invalidés à la sauvegarde du form builder). Environ une demi-heure.
2. **Types générés depuis l'OpenAPI** (`openapi-typescript` dans `src/api/generated/`, sortie commitée, vérification en CI que la régénération est identique), **puis registre typé** par un type mappé sur `EntityKey`. Les deux se renforcent : les DTO générés sont ce qui paramètre le registre.
3. **Découpage** de `EntityTable`, `renderers.tsx` (un fichier par famille : texte, nombre, date, référence, concept), `entities/types.ts` (par contrat : config, liste, détail, duplication), et réduction des suppressions `exhaustive-deps` une par une (souvent une ref ou un handler d'événement suffit).
4. **Table entité → segment / types de réponse** construite depuis le registre (§2.7) : petit, sans risque, testable (« chaque `EntityKey` apparaît »).
5. **Code splitting** par entité, après mesure avec `rollup-plugin-visualizer`.
6. **Mesure** : Sonar sur `frontend/src`, couverture `@vitest/coverage-v8` avec plancher au niveau actuel, `vitest-axe` sur les trois types de panneau, Prettier en un commit isolé.
7. **Pont JSF** : vérifier au montage que chaque `data-action-*` se résout en fonction, et faire échouer le build Java si la version du contrat diverge.

---

## 4. À ne pas toucher

- Le reducer de navigation et le codec d'URL.
- `rules/` et sa fixture partagée.
- La règle de couches ESLint.
- Le principe « registre, pas de `switch` » dans `panels/`.
- La stabilité de `PaneSplit`.

## 5. Limites de cet audit

Les patterns ont été jugés sur la structure, la configuration ESLint et des comptages. Les gros composants (§2.6) n'ont **pas** été relus ligne par ligne ; leur qualité interne (responsabilités mélangées, effets redondants) reste à auditer. Le poids du bundle et les gains de `staleTime` ne sont pas mesurés.

## 6. Organisation des dossiers (réorganisée le 2026-10-22)

Déplacements purs, sans changement de comportement ; `tsc`, ESLint, les 1220 tests et `vite build` passent avant et après.

```
src/
  api/            transport seul : client, errors, queryClient, queryKeys, basePath, entityCache
    resources/    appels de domaine : bookmarks, views, homeShortcuts, homeAccesses, ficheConfigs, validation
  entities/
    <type>/       une entité = un dossier (config, api, types, columns, CreateForm, DetailHeader…)
    shared/       contrats et helpers communs aux entités (types, listApi, deleteApi, typeCatalog, routes, scope…)
    registry.ts, keys.ts
  panels/
    EntityListPanel, EntityDetailPanel, HomePanel     les trois panneaux
    list/         état et données de liste (listState, tableState, usePagedList, useRowActions, vues, tableau…)
    detail/       fiche : onglets (ficheConfig, ficheTabs*), relationTab, suppression, voisins
    pane/         cadre : PaneSplit, paneState, persistedPaneState, writeMode, bridge, entityNavigation
  components/
    layout/       cadres et feuilles : PaneErrorBoundary, PanelToolbar, PanelHeaderBar, BottomSheet, DetailSkeleton…
    create/       création : CreateEntityOverlay, CreateFormShell, TypeOnlyCreateForm, CreateLinkField…
    detail/       en-têtes et fiches pilotés par schéma, fil d'Ariane, dialogue de suppression, statut de validation
    duplicate/    duplication de structure
    home/ table/ relationLinks/    inchangés
```

Choix à connaître :

- `api/resources/` plutôt qu'un rangement « à côté du consommateur » : `views`, `bookmarks` et `homeShortcuts` ont plusieurs consommateurs répartis dans `panels/`, `components/home/` et `search/`.
- `components/ProjectName.tsx` reste à la racine : il est utilisé par `search/`, `components/home/` et par un type de `api/resources/homeShortcuts`. C'est un reliquat, pas une décision.
- `entities/shared/types.ts` (500 lignes) n'est pas encore découpé par contrat (config, liste, détail, duplication) : le fichier a seulement changé de dossier.
- Les règles de couches d'ESLint n'ont pas eu besoin de changer : leurs globs couvrent les sous-dossiers.

## 8. Types générés depuis l'OpenAPI et registre typé (2026-10-22)

**Cache.** `queryClient.ts` désactive `refetchOnWindowFocus` par défaut. Les catalogues, règles et formulaires avaient déjà `staleTime: Infinity` requête par requête ; une requête qui veut du frais au montage garde son propre `staleTime`.

**Types générés.**

- `frontend/openapi/openapi.json` : instantané du spec (`/siamois/v3/api-docs`), commité.
- `frontend/src/api/generated/schema.d.ts` : types générés par `openapi-typescript`, commités.
- `npm run api:pull` récupère le spec d'un backend lancé (`OPENAPI_URL` pour une autre adresse) puis régénère ; `npm run api:gen` régénère seul.
- `schema.test.ts` (Vitest, donc dans la phase Maven `test`) échoue si `schema.d.ts` ne correspond pas à l'instantané.
- `src/entities/apiConformance.ts` vérifie à la compilation que chaque DTO écrit à la main (projet, UE, mobilier, phase, contenant, lieu, document, étude, champ, et leurs permissions/compteurs) ne déclare que des champs présents dans le schéma. Un champ renommé ou supprimé côté Java fait échouer `tsc` en nommant le champ. Vérifié par un test négatif.

Springdoc marque presque toutes les propriétés de réponse comme optionnelles : les types générés servent donc à **contrôler** les DTO manuels, pas à les remplacer (sinon chaque accès demanderait une garde `undefined`).

**Limite connue.** Rien ne compare encore l'instantané au backend courant : il faut lancer `npm run api:pull` après une modification d'un DTO Java. Un test côté Java (ou une étape CI qui démarre l'application et compare) fermerait ce trou.

**Registre typé.** `entities/registry.ts` expose `EntityTypeMap` (un `EntityTypeConfig<Summary, Detail>` par `EntityKey`, vérifié exhaustif à la compilation) et une surcharge `getEntityType<K extends EntityKey>(key: K)`. Un appel avec une clé littérale (`getEntityType("recordingUnit")`) est précisément typé ; une clé `string` (URL, pont JSF, cible de relation) renvoie `AnyEntityTypeConfig`, l'unique endroit où les génériques sont effacés. Les `any` du registre et de `listSiblings.ts` sont passés de 6 à 1. `registerEntityType` ne vérifie pas que la config enregistrée correspond à la clé.
