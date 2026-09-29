# Migration React du panneau principal : reste à faire après le merge

État au 25/09/2026, branche `feat/main-panel-react-migration`.

Tout le panneau principal passe désormais par React : l'accueil, les 6 listes et les 6 fiches (projet, UE, mobilier, phase, contenant, lieu). Le code JSF qui ne sert plus a été retiré. Ce document recense ce qui a été volontairement laissé pour plus tard, par priorité.

## 1. À vérifier dès le premier déploiement

Ces points sont codés et couverts par des tests unitaires, mais n'ont pas encore été vérifiés sur l'application relancée.

- **Colonnes dynamiques du mobilier**. `GET /projects/{id}/mobiliers?fields=…` et `GET /recording-units/{id}/mobiliers?fields=…` : `SpecimenAnswersProjector` et `FindListProjectionService`. À vérifier : le sélecteur de colonnes, les valeurs affichées (références multiples comme les matériaux, les contenants ou les phases), le tri et les filtres sur un champ additionnel.
- **Catalogues de colonnes de l'organisation**. `GET /organizations/{id}/{recording-unit|find|phase|container}-types` et `fields=` sur les listes organisation. La requête JPQL `CustomFieldRepository.findActiveAdditionalByInstitutionAndTable` n'est validée qu'au démarrage de Spring : il n'existe pas de test sur base de données. Vérifier que l'union ne contient que les champs actifs de la bonne table.
- **Nettoyage JSF (retrait du code mort)**. Les panels Java ne sont plus que des descripteurs (`AbstractPanel`, `AbstractListPanel`, `AbstractEntityPanel` et une classe par type). À vérifier sur chaque type (liste et fiche) :
  - [ ] Ouverture d'une fiche par son URL ; entité inexistante → page 404, sans droit → 403.
  - [ ] Titre et icône de l'onglet du navigateur, historique de la barre latérale, favori.
  - [ ] Ouverture et fermeture de l'aperçu, puis F5 : l'aperçu est rouvert, ou reste fermé.
  - [ ] Mode focus et retour.
  - [ ] Bouton précédent du navigateur : un seul rechargement (le gestionnaire jQuery `popstate` de `template.js` a été retiré, React gère le sien).
  - [ ] Recherche du haut : un résultat ouvre sa fiche (redirection, plus d'aperçu JSF).
  - [ ] Pages de paramètres, connexion, création d'organisation (le `maxlength` du nom est désormais `50` en dur) : intactes.
  - Les anciennes URL avec `?tab=` ou `?viewId=` restent acceptées, mais ces paramètres sont ignorés.
- **Checklist navigateur des lots d'édition, de tri et de filtre** (reprise de `ok-je-crois-que-proud-globe-RESTE-A-FAIRE.md`) :
  - [ ] Liste des UE d'un projet : tri par chaque colonne, avec contrôle de l'ordre réel (valeurs vides en dernier, contributeurs triés par nombre de valeurs).
  - [ ] Liste des projets : tri et filtre sur les champs `-1xx`.
  - [ ] Onglets de relation : enfants d'une UE, UE d'une phase, mobiliers d'une UE (tri par champ).
  - [ ] Filtres dans l'interface : chips, plage de dates, filtre « in » sur une référence.
  - [ ] Éditeurs de référence en liste et en fiche : personne, projet, UE, mobilier, phase, contenant, lieu, vocabulaire legacy, mesure.
  - [ ] Pied « Nouveau » : l'overlay reste ouvert pendant la création, et Échap dans le dialog n'annule pas l'édition.
  - [ ] Changement de type d'une UE : les réponses communes sont reportées, les anciennes supprimées.
  - [ ] Les champs sans éditeur (code action, adresse) ne sont pas cliquables.
  - [ ] Anomalie de données de dev à confirmer : sur la liste UE de l'organisation, le tri asc et le tri desc sur le champ additionnel `1` donnent le même ordre.

### Vérification en direct du 29/09/2026 (lot 0, en cours)

Vérifié sur l'application relancée (base de dev, une seule organisation) :
- [x] Colonnes dynamiques du mobilier (sélecteur, valeurs, tri, filtres), listes organisation, projet et UE.
- [x] Catalogues de colonnes de l'organisation : seuls les champs actifs de la bonne table (contrôlé en base).
- [x] Tri de tous les champs triables des UE et des projets (aucune erreur) ; les ordres identiques en asc/desc sont des égalités ou des valeurs vides (dernières). L'« anomalie du champ `1` » n'en est pas une : les UE 4 et 5 ont la même valeur.
- [x] Filtres (texte, référence, plage numérique, plage de dates, recherche) et erreurs 400 explicites sur clé inconnue.
- [x] Onglets de relation (UE enfants, mobilier d'une UE, lieux enfants, projets d'un lieu).
- [x] 404, aperçu + F5, mode focus + retour (avec F5), bouton précédent du navigateur (un seul rechargement), recherche du haut, favori (React ↔ barre latérale), historique.
- [x] Éditeur de référence en cellule, pied « Nouveau » (l'overlay reste ouvert, Échap dans le dialog ne ferme que le dialog).
- Non vérifié : 403 (une seule organisation en base), changement de type d'UE (tous les types du projet 3 partagent les mêmes champs), champs sans éditeur (code action, adresse).

Corrigé pendant la vérification : titre de l'onglet du navigateur après navigation client, « Unité d'enregistrement » vide sur la fiche mobilier (`FormService` ne relisait pas la réponse « une UE »), fils d'Ariane « Tout le mobilier »/« Toutes les phases »/« Toutes les unités d'enregistrement », placeholder de recherche en anglais, tiret sur les champs vides en lecture seule, icône et couleur des favoris phase/contenant.

Restent ouverts : colonnes UE/Catégorie/Contient sans icône de tri dans la liste mobilier alors que le catalogue les déclare triables ; Échap ne ferme pas la liste de colonnes ; boutons de barre d'outils sans `aria-label` ; titre « Mobiliers » (JSF) vs « Mobilier » (React) après rechargement ; `recording-unit-types` chargé 3 fois d'affilée à l'ouverture d'un onglet de relation ; focus non rendu à l'éditeur après le dialog « Nouveau ».

## 2. Performance

Mesuré le 29/09/2026 sur la base de dev, en comptant les parcours de table côté PostgreSQL (`pg_stat_user_tables`, attente de 12 s avant de lire les compteurs) pour une requête isolée.

| Sujet | Traitement |
|---|---|
| Droits par projet dans les listes organisation | **Fait.** `OrganizationListService.canEditByProject` et `canValidateByProject` passent par `ProfilePermissionService.actionUnitIdsGranting` : les droits d'instance et d'organisation sont testés une fois, puis une seule requête liste les projets qui donnent le droit par un profil de projet. Le nombre de requêtes ne dépend plus du nombre de projets de la page. |
| Chargement des lignes de liste (mobilier, UE) | **Fait**, sans changer de code métier : `hibernate.default_batch_fetch_size: 50` (`application.yaml`). Les relations paresseuses et les références d'une page se chargent par un `IN (…)` par relation. Mobilier : 645 parcours pour 1 ligne, 2 382 pour 9 avant, contre 448 et 620 après (~217 par ligne avant, ~21 après). UE : 632 pour 1 ligne et 1 711 pour 25 avant, contre 434 et 492 après (~45 par ligne avant, ~2,4 après). Fiche mobilier en REST : 588 avant, 388 après. Le coût fixe d'environ 430 parcours pour une page d'une ligne n'a pas été analysé. |
| Tri et filtre sur champ additionnel | **Mesuré, rien à changer.** `EXPLAIN ANALYZE` sur une base jetable de 300 000 UE et 540 000 réponses (mêmes tables et index que la base réelle) : tri texte 460 ms, tri numérique 340 ms (sous-requête corrélée par ligne, accès par clé primaire, coût linéaire) ; filtres « contient » 42 ms (page) et 26 ms (comptage), plage numérique 18 ms. Pas d'index trigramme (`pg_trgm` est déjà installé) : il ne se justifie qu'à partir d'environ 10 fois ce volume, quand un « contient » approche 500 ms. À refaire alors avec des données réelles. |
| Initialisation des panels JSF | **Vérifié, rien à changer.** `AbstractEntityPanel.init()` charge l'entité une fois (titre, organisation, 404/403) : la page JSF d'une fiche mobilier coûte environ 165 parcours contre 588 pour son appel REST (avant le correctif ci-dessus). Un chargement allégé n'apporterait presque rien. |
| Conversion complète des mobiliers en liste | Le N+1 est levé par le lot chargé par paquets. Un DTO de liste dédié ne se justifie que si le coût fixe de ~430 parcours devient gênant. |

## 3. Fonctionnel reporté

- **Vue arborescente** de la liste des projets et des lieux. Le composant JSF (`LazyTreeTable`, `BaseLazyDataModel`…) a été retiré, mais le domaine est conservé : `FilterDTO.rootOnly/ancestorClosure/matchIds`, les branches `rootOnly` des services, les méthodes `findChildren…`/`existsChildren…` et les requêtes des repositories. Il manque `GET /projects/{id}/children` (l'URL est déjà annoncée par `ProjectResourceLinks.children`) et le mode arbre d'`EntityListPanel`.
- **Carte**. Il n'en existe aucune aujourd'hui.
- **Documents** et **Stratigraphie** (onglets de fiche).
- **Règles conditionnelles de la fiche UE** (`enabledWhen`/`dependsOn` de `RecordingUnitDetailsForm` : érosion selon la nature, interprétation selon la nature). Les champs s'affichent aujourd'hui sans condition.
- **Adresse** (`SELECT_ADDRESS`) : lecture seule, en attendant GéoPlateforme/INSEE.
- **Champs non triables** : `zInf`/`zSup` (mesures embarquées), `chronologicalPhase`, `endDate` et `excavators` sur les UE (binding non mappé).
- **Lieux** : pas de tri ni de filtre par id de champ, et pas de catalogue de colonnes.
- **« Dupliquer la structure »** (une UE avec ses descendants) : seule la duplication de l'UE seule est portée.
- **Duplication du mobilier** : non portée. Elle est cassée en JSF, car le constructeur de copie `SpecimenDTO` est vide.
- **Vues de table sauvegardées** (`?viewId=`) : React les ignore. `UiViewService`, `TableViewState` et `UITableViewDTO` sont conservés.
- **Fiche précédente/suivante liée au tri et aux filtres actifs de la table** : aujourd'hui les flèches suivent l'ordre par défaut de toutes les listes (date de création, la plus récente en premier ; ↓ = ligne du dessous), sans tenir compte du tri, des filtres ni de la recherche en cours. `GET /projects/{id}/siblings` accepte déjà `sort`/`search`/`f.*` ; il manque l'équivalent pour les autres types (`EntitySiblingsService`) et la transmission de l'état de la table à la fiche.
- **Anciennes clés de filtre nommées** (`f.status`…) : toujours acceptées par le serveur, mais un état `?s=` ou une vue qui les utilise n'affiche plus de chip libellé.

## 4. Thème

Le thème Siamois peint le panneau React via `primefaces-themes/theme-base/_primereact.scss` (voir `docs/theme-class-map.md`). Fait avant le merge : couleurs de statut (`--status-*` dans `main-panel.css`, reprises du JSF) et valeurs de repli des variables alignées sur `_variables.scss`.

Reste à harmoniser, par comparaison visuelle avec le JSF (harness `dev/theme/harness.html` et `dev/panels/panels.html`), les zones stylées uniquement par `main-panel.css` :
- barre de chips de filtre ;
- overlay d'édition de cellule ;
- libellés des champs de la fiche ;
- formulaire de création ;
- cartes de l'accueil ;
- actions de ligne ;
- `VisibilityChooser` ;
- séparateurs de la barre d'outils.

Points à trancher :
- **Chip identifiant de la liste** : le JSF le remplit avec la couleur de l'entité, React le dessine en contour (décision prise pendant la migration). À confirmer.
- **Couleur « annulé »** (`--status-cancelled`, nouveau statut sans équivalent JSF) : à faire valider.
- **Déplacement des jetons `--status-*`** dans `_variables.scss` : les règles JSF `status-button` y lisent aujourd'hui des couleurs en dur. Ce déplacement modifie leurs déclarations, et le script de non-régression le signalera (attendu).

Outillage (corrigé le 29/09/2026) :
- `frontend/dev/check-jsf-theme-regression.py` comparait chaque ancienne règle à la première nouvelle règle qui la couvre : plusieurs règles ayant les mêmes déclarations (variantes `:hover` des boutons secondaires) produisaient 6 faux « added selectors without .p- » même entre deux arbres identiques. Il compare maintenant par groupe (contexte, déclarations). Il échoue toujours sur une déclaration modifiée, un sélecteur ajouté sans `.p-` ou une règle nouvelle non `.p-`.
- `frontend/index.html` (harness racine de `npm run dev`) passe maintenant l'option `main`, désormais obligatoire pour `mount()`.

## 5. Nettoyage résiduel

- **SCSS du thème** : les règles `rum-*`, `sum-*`, `mca-*` et `strati*` de `others/_styles.scss` ont été retirées (41 règles). Il reste à passer à la main `panel/_panel.scss`, `panel/_panels.scss` et le reste de `others/_styles.scss` (classes des anciens templates : `panel-tab-wrapper`, `docs-container`, `sia-new-unit-dialog`, `sia-welcome-card`, `fieldmode-tabview`…). Les classes encore utilisées par React (`sideview*`, `panel-docked`, `siamois-panel`, `panel-splitter-panel-l/r`, `*-panel`, `*-chip*`) doivent rester.
- **Méthodes de domaine devenues orphelines** avec le composant arbre JSF : elles ont été conservées volontairement pour la future vue arborescente React (voir §3).
- **`UiViewService`, `TableViewState` et `UITableViewDTO`** (vues de table sauvegardées) : plus aucun consommateur côté interface. À supprimer ou à brancher sur React.
- **Réconciliation avec la branche `feat/ru-react-panel-phase1`** (panneau UE autonome, abandonné) : elle a son propre `frontend/` et son propre point de montage. Il faut la fermer ou la réaligner, pas la merger telle quelle.

## 6. Sécurité : alerte SonarCloud sur la fixation de session (à traiter la semaine prochaine)

SonarCloud signale `sessionFixation(fixation -> fixation.none())` sur `apiV1SecurityFilterChain` (`WebSecurityConfig.java`, ligne 97) : « Create a new session during user authentication to prevent session fixation attacks ».

**Analyse : faux positif, le code est à garder tel quel.**
- La chaîne `/api/v1` est `STATELESS` et authentifiée par JWT Bearer. Elle ne crée ni n'utilise de session HTTP : il n'y a pas de session à fixer.
- Avec la stratégie par défaut (`changeSessionId`), `SessionManagementFilter` prend chaque appel JWT pour une nouvelle connexion et change l'id de la session JSF dont le navigateur envoie le cookie. Les appels parallèles se disputent alors leurs `Set-Cookie`, et le navigateur peut garder un id mort : c'est le bug « renvoyé à la connexion au F5 », corrigé le 24/09/2026.
- `credentials: "omit"` dans `frontend/src/api/client.ts` empêche React d'envoyer le cookie, mais un autre appelant de la même origine l'envoie par défaut (Swagger UI, un `fetch` JSF). `none()` reste donc la protection côté serveur.

**Pistes écartées :**
- Désactiver `sessionManagement` sur cette chaîne : STATELESS ne s'appliquerait plus, le `SecurityContext` pourrait être lu depuis la session JSF, et l'API accepterait le cookie comme authentification alors que le CSRF y est désactivé. Ce serait une vraie faille.
- Remplacer par `sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())` : même comportement, mais ça fait juste taire la règle sans rien corriger.

**À faire :**
- [ ] Passer l'alerte en « Safe » / « False positive » dans SonarCloud, avec cette justification : « Chaîne `/api/v1` stateless (JWT Bearer, `SessionCreationPolicy.STATELESS`) : aucune session n'est créée à l'authentification. La protection par défaut (`changeSessionId`) modifiait l'id de la session JSF du navigateur à chaque appel d'API, et les appels parallèles déconnectaient l'utilisateur. `none()` est voulu. » Autre possibilité : un `// NOSONAR` commenté en fin de ligne.
- [x] Test `ApiV1SessionSafetyTest` (29/09/2026) : un appel `/api/v1` authentifié par JWT, avec une session HTTP existante, ne change pas son identifiant et ne renvoie pas de `Set-Cookie` ; sans session, il n'en crée pas. Vérifié par mutation : sans `sessionFixation(none())`, l'identifiant de session change et le test échoue.
