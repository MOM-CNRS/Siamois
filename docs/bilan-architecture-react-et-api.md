# Bilan : défauts de l'architecture React, de l'API, et plan

État au 01/10/2026, branche `feat/field-rules-engine`, avant le merge de la migration du panneau principal.

Ce document est un avis d'ensemble, sans complaisance. Il ne remplace pas les documents de détail :
- `docs/react-architecture-review.md` : l'architecture React, avec chaque point chiffré (§2.1 à §2.13) ;
- `docs/api-changes-vs-main.md` : le contrat de l'API (ruptures, nouveautés), en anglais pour les consommateurs externes ;
- `docs/react-migration-apres-merge.md` : la liste de vérification après déploiement et le reste à faire fonctionnel.

Les numéros « §2.x » ci-dessous renvoient à `react-architecture-review.md`.

## 1. Verdict

- **React : bon et durable.** Le modèle « registre d'entités + configuration » tient. Les règles de couches sont vérifiées par ESLint, pas seulement écrites. La dette est connue et bornée.
- **API : riche et cohérente, mais risquée à publier.** Le comportement est bon. Le risque vient du volume de ruptures de contrat d'un coup, et de types côté front qui ne sont pas vérifiés automatiquement.
- **Décision : on merge**, en traitant en premier la génération des types depuis l'OpenAPI (point A2).

Limite de cet avis : il s'appuie sur les documents existants, la structure du code et les mesures déjà faites. Ce n'est pas un audit ligne à ligne.

## 2. Ce qui est solide (à ne pas casser)

- Registre d'entités : un nouveau type d'entité ne touche presque que son dossier. Aucun `switch` sur l'entité dans `panels/`.
- Couches vérifiées par ESLint (`no-restricted-imports` en erreur).
- Contrat JSF/React versionné (`MOUNT_CONTRACT_VERSION`) : un décalage donne une erreur visible.
- `rules/` : moteur de règles isolé, avec un jeu de cas partagé avec Java.
- Navigation : réducteur pur et codec d'URL testés aller-retour.
- Une seule `QueryClient`, clés centralisées, une `PaneErrorBoundary` par panneau et par onglet.
- Environ 760 tests ; `tsc --strict`, ESLint et Vitest tournent dans le build Maven.
- API : conventions cohérentes (pagination, `meta`, `fields=`, `f.*`), `400` explicite sur clé inconnue, valeurs multiples jamais tronquées en silence (`total` / `complete` / `_links`), test de non-régression sur la session (`ApiV1SessionSafetyTest`, vérifié par mutation).

## 3. Défauts de l'architecture React

Classés par gravité réelle, pas par effort.

| # | Défaut | Conséquence | Réf. |
|---|---|---|---|
| R1 | **Types DTO écrits à la main** alors que Java publie déjà l'OpenAPI | Un champ renommé côté Java compile côté React et casse au runtime, souvent en cellule vide, sans erreur. Le risque grandit à chaque endpoint. | §2.2 |
| R2 | **Registre typé `any`** (`Map<string, EntityTypeConfig<any, any>>`, 6 `eslint-disable`) | Tous les panneaux travaillent sur des données non typées : la rigueur de l'architecture ne vaut pas pour les types. | §2.1 |
| R3 | **Couplage avec JSF** : `sessionStorage`, `popstate`, synchro de session, `remoteCommand` résolues par `window[nom]` | C'est la zone qui casse de façon surprenante (bug « déconnecté au F5 »). Une faute de frappe sur une commande est un no-op silencieux. La pile de focus est perdue au retour arrière et au F5. | §2.10 |
| R4 | **`fields/` connaît la liste des entités** (deux tables de correspondance à la main) | Ajouter un type d'entité oblige à éditer du code générique. | §2.3 |
| R5 | **Catalogues de types demandés de quatre façons** (`typeCatalog`, `useTypeRules`, `recordingUnitTypes`, `projectTypes`) | Même donnée chargée plusieurs fois, plusieurs clés de cache pour un seul catalogue. | §2.5 |
| R6 | **Cache uniforme pour des données qui ne le sont pas** (`staleTime: 0` partout) | Catalogues et règles rechargés à chaque focus et à chaque nouvel onglet. | §2.6 |
| R7 | **Duplication entre entités** (`DetailHeader` projet/UE, `CreateForm` mobilier/lieu) | Coût à chaque évolution, pas de risque immédiat. | §2.4 |
| R8 | **Un seul bundle de 940 Ko**, gros fichiers (`EntityTable` 491, `renderers` 439, `CellEditOverlay` 409, `types.ts` 380), plusieurs `exhaustive-deps` désactivés | Chargement initial, lisibilité, risque de fermeture périmée. | §2.7, §2.8 |
| R9 | **Un CSS global de 1 600 lignes**, non découpé, sans couche de cascade | Collisions possibles de noms avec le thème JSF. | §2.9 |
| R10 | **Qualité hors périmètre** : `frontend/src` non analysé par SonarCloud, pas de mesure de couverture, pas de Prettier, pas de test d'accessibilité | 12,7 k lignes que la porte qualité ne voit pas. | §2.13, §2.12 |
| R11 | **Erreurs de lecture affichées au cas par cas** ; `ApiError.body` et `path` ne sont rapportés nulle part | Affichage inégal, pas de télémétrie. | §2.11 |

## 4. Défauts de l'API

| # | Défaut | Conséquence |
|---|---|---|
| A1 | **Trop de ruptures de contrat d'un coup** : 3 endpoints supprimés, 6 changements incompatibles et 4 au niveau des schémas (B1 à B7, S1 à S5 dans `api-changes-vs-main.md`) | Plusieurs sont discrets : `limit` par défaut de 50 à 10, `answers` absent au lieu de `{}`, tri inconnu qui donne `400` au lieu d'être ignoré, liste de valeurs devenue un objet. Un client externe casse sans message clair. |
| A2 | **Pas de génération de types ni de contrôle de dérive** entre l'OpenAPI et le front | Voir R1 : c'est le même problème vu de l'API. |
| A3 | **Services trop gros** : `ProjectApiService` 1 586 lignes, `RecordingUnitOpenApiService` 1 087, `FieldQueryService` 696 | Objets-dieux difficiles à modifier et à relire. |
| A4 | **Requêtes JPQL non testées sur une vraie base** (`findActiveAdditionalByInstitutionAndTable` n'est validée qu'au démarrage de Spring) | Tout le code de tri et de filtre n'a pas de filet de sécurité d'intégration. |
| A5 | **Tri et filtre sur champ additionnel** = sous-requête corrélée par ligne | Mesuré à 460 ms pour 300 000 UE : correct aujourd'hui, mais tient sur une hypothèse de volume. Pas d'index trigramme (justifié seulement vers 10 fois ce volume). |
| A6 | **Coût fixe d'environ 430 parcours de table par page**, non analysé | Premier chiffre à regarder si la latence monte. |
| A7 | **Pagination contrainte** : `offset` doit être un multiple de `limit` | Gênant pour un client qui veut une pagination libre. Le front contourne en lisant une ligne à la fois pour les voisins. |
| A8 | **Duplication incomplète par construction** : les réponses aux champs additionnels ne sont pas copiées (UE comme mobilier) | Une copie perd des données saisies ; à dire clairement aux utilisateurs. |
| A9 | **Adresse en lecture seule**, ni triable ni filtrable | En attente de GéoPlateforme/INSEE. |

## 5. Plan

### Avant le merge

1. **Décider si l'API a des consommateurs externes.** Si oui : publier `api-changes-vs-main.md` aux consommateurs et envisager de versionner (`/v2`) plutôt que de casser `/v1` (A1). Si tout est interne : rien à faire de plus.
2. Exécuter la liste de vérification du premier déploiement (`react-migration-apres-merge.md` §1) : 403, changement de type d'UE, champs sans éditeur ne sont pas encore vérifiés.
3. Traiter l'alerte SonarCloud sur la fixation de session : la marquer faux positif avec la justification déjà rédigée (`react-migration-apres-merge.md` §6). Ne pas désactiver `sessionManagement`.

### Juste après le merge (par ordre de valeur)

| Ordre | Action | Corrige | Effort |
|---|---|---|---|
| 1 | **Générer les types TypeScript depuis l'OpenAPI** (`openapi-typescript` dans `src/api/generated/`, sortie committée, contrôle en CI que la régénération est identique), puis réduire les types à la main à des `Pick` / `Omit` | R1, A2 | moyen |
| 2 | `staleTime` par requête et pas de rechargement au focus (catalogues, formulaires, règles) | R6 | petit |
| 3 | Tables du registre dérivées des configs d'entités (`resourceSegment`, `referenceAnswerTypes`) | R4 | petit (½ jour) |
| 4 | **Typer le registre** (type indexé par `EntityKey`), supprimer les `any` | R2 | moyen (2 à 3 jours) |
| 5 | Un seul hook `useTypesCatalog(segment, scope, select)` | R5 | petit à moyen |
| 6 | Activer SonarCloud sur `frontend/src`, couverture Vitest avec un plancher à la valeur actuelle, Prettier en un commit isolé | R10 | petit |
| 7 | Test d'intégration sur base réelle pour les requêtes de filtre et de tri | A4 | moyen |
| 8 | Vérifier au démarrage que chaque `data-action-*` du pont JSF existe ; test Java qui compare la version du contrat des deux côtés ; encoder la pile de focus dans l'URL si l'usage le justifie | R3 | petit à moyen |

### Ensuite, au fil de l'eau

- Chargement paresseux par entité et découpage de `renderers.tsx` et `entities/types.ts`, **après mesure** du bundle (R8).
- Extraire les en-têtes et formulaires de création dupliqués (R7).
- Découper `ProjectApiService` quand on y retouche, pas avant (A3).
- Renommer les classes CSS propres à React en `sia-*` quand on y touche, et couper `main-panel.css` par composant (R9).
- Vue arborescente (lot 5) : plan dédié, cible déjà décrite dans `react-migration-apres-merge.md` §3.

### À surveiller en production

- Latence des listes avec tri ou filtre sur champ additionnel, à refaire avec des données réelles vers 10 fois le volume mesuré (A5).
- Coût fixe par page (A6).
- Tout bug de session ou de connexion après rechargement : c'est la zone R3.

## 6. Ce qu'il ne faut pas faire

- Ne pas désactiver `sessionManagement` sur la chaîne `/api/v1` pour calmer Sonar : l'API accepterait le cookie JSF comme authentification avec le CSRF désactivé.
- Ne pas rendre la fiche projet pilotée par schéma tant que son formulaire n'est pas dans le catalogue du backend.
- Ne pas ajouter de `@layer` à `main-panel.css` sans mettre aussi le thème en couche : le thème hôte, non stratifié, gagnerait toujours.
- Ne pas batcher le renommage des classes CSS : chaque renommage se vérifie contre `docs/theme-class-map.md`.
