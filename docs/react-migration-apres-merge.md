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

## 2. Performance

| Sujet | Où | Proposition |
|---|---|---|
| Droits par projet dans les listes organisation | `OrganizationListService.canEditByProject` et `canValidateByProject` | Ces méthodes font une vérification par projet distinct de la page, soit jusqu'à 4 requêtes chacune. Il faut passer à `ProfilePermissionService.actionUnitIdsGranting(user, INSTANCE_X, ORGANIZATION_X, PROJECT_X)`, qui renvoie `null` quand le droit couvre toute l'organisation, sinon l'ensemble des projets. Cela fait 3 ou 4 requêtes au total, quel que soit le nombre de projets. Ce n'est valable que si tous les projets de la page appartiennent à l'institution, ce qui est le cas pour une liste organisation. |
| Tri et filtre sur champ additionnel | `FieldQueryService` (sous-requêtes corrélées sur `custom_field_answer`) | Lancer un `EXPLAIN ANALYZE` sur un jeu de données réaliste. Les index du lot A sont en place. Étudier un index trigram sur `value_as_text` pour les filtres « contient ». |
| Conversion complète des mobiliers en liste | `SpecimenService.search*` → `SpecimenMapper` | Chaque ligne charge toutes ses relations (auteurs, matériaux, contenants, phases, parents/enfants) : N+1 probable. Envisager un DTO de liste ou un `@EntityGraph`. |
| Initialisation des panels JSF (s'il en reste après le nettoyage) | `*Panel.init()` | Vérifier qu'aucun modèle lazy ni compteur n'est encore calculé pour rien au montage React. |

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

Outillage :
- `frontend/dev/check-jsf-theme-regression.sh` signale 6 « added selectors without .p- » (`.ui-button-secondary:hover`…) même entre deux arbres identiques : c'est un faux positif de normalisation à corriger dans `check-jsf-theme-regression.py`.
- `frontend/index.html` (harness racine de `npm run dev`) plante : il ne passe pas l'option `main` désormais obligatoire de `mount()`. Utiliser `dev/panels/panels.html`, ou mettre ce fichier à jour.

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
- [ ] Optionnel : un test d'intégration qui vérifie qu'un appel `/api/v1` portant un cookie `JSESSIONID` ne renvoie pas de `Set-Cookie`. Il protège la correction si quelqu'un « corrige » l'alerte.
