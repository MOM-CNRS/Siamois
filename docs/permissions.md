# Système de permissions

Ce document décrit le système de rôles/permissions de Siamois : le modèle de données, les permissions
disponibles, les profils qui les combinent, et les règles de garde appliquées sur les pages de settings.

Le système repose entièrement sur `ProfilePermissionService`/`PermissionConstants`, piloté par la donnée
(profils assignés en base) plutôt que par des vérifications de rôle codées en dur. Il vit en dehors de Spring
Security : le principal authentifié (`Person.getAuthorities()`) ne porte aucune autorité Spring, tout contrôle
d'accès passe par un appel explicite à `ProfilePermissionService`.

## Portées (scopes)

Un profil (`Profile`) est rattaché à une portée (`PermissionScopeType`) qui détermine où ses permissions
s'appliquent :

- **INSTANCE** : partout dans l'application (super administrateur).
- **ORGANISATION** : sur une institution précise (et par cascade, sur tous ses projets).
- **PROJECT** : sur un projet (action unit) précis.

Une permission détenue à une portée large est automatiquement valable aux portées inférieures : une permission
INSTANCE couvre toutes les organisations et tous les projets ; une permission ORGANISATION couvre tous les
projets de cette institution. C'est le rôle de `ProfilePermissionService.hasOrganizationPermission`/
`hasProjectPermission`, qui remontent la cascade en interrogeant `hasInstancePermission` en premier — mais
uniquement pour le **même code de permission**. Un profil INSTANCE qui ne détient pas explicitement le code
vérifié à l'échelon inférieur ne bénéficie donc pas d'un bypass automatique : c'est pourquoi certains contrôles
(ex. accès aux settings d'une institution) vérifient explicitement `hasInstancePermission(INSTANCE_MANAGE_SETTINGS)`
en plus du contrôle organisationnel.

## Permissions (`PermissionConstants`)

### Portée INSTANCE

| Code | Droit |
|---|---|
| `INSTANCE_MANAGE_SETTINGS` | Ajouter, modifier, supprimer les utilisateurs de l'instance ; gérer les settings globaux de l'instance. Remplace l'ancien contrôle de rôle codé en dur `isSuperAdmin()` — c'est la permission utilisée partout où l'application vérifiait auparavant "est-ce un super admin". |

### Portée ORGANISATION

| Code | Droit |
|---|---|
| `ORGANIZATION_CREATE` | Créer des organisations (portée INSTANCE dans son usage : seul un profil INSTANCE peut créer une organisation). |
| `ORGANIZATION_ACCESS` | Accès en lecture à toutes les données d'une institution. |
| `ORGANIZATION_MANAGE_SETTINGS` | Gérer les settings de l'institution : membres (ajout/retrait/rôles), nom, configuration du thésaurus. |
| `ORGANIZATION_MANAGE_ACTIONS` | Créer/gérer les projets (action units) d'une institution. |
| `ORGANIZATION_MANAGE_PLACES` | Créer/gérer les lieux (spatial units) d'une institution. |

### Portée PROJECT

| Code | Droit |
|---|---|
| `PROJECT_MANAGE_SETTINGS` | Gérer les settings du projet : membres, thésaurus, fiche projet, types/champs/identifiants de tables, upload de données. |
| `PROJECT_EDIT_RECORDING_UNITS` | Créer/modifier les unités d'enregistrement (UE). |
| `PROJECT_EDIT_FINDS` | Créer/modifier le mobilier (finds/specimens). |
| `PROJECT_EDIT_PHASES` | Créer/modifier les phases. |
| `PROJECT_EDIT_CONTAINERS` | Créer/modifier les contenants. |

> `ORGANIZATION_LIST_ACCESS` a été supprimée : elle n'était jamais assignée à un profil ni vérifiée nulle part.

## Profils (`ProfileConstants`)

Les profils combinent des permissions et sont assignés aux personnes (`PersonProfileAssignment`), avec la
portée correspondante :

| Profil | Portée | Permissions |
|---|---|---|
| `SUPERADMIN` | Instance | `INSTANCE_MANAGE_SETTINGS`, `ORGANIZATION_CREATE`, `ORGANIZATION_ACCESS` |
| `ORGANIZATION_MANAGER` | Organisation | `ORGANIZATION_MANAGE_SETTINGS`, `ORGANIZATION_MANAGE_ACTIONS`, `ORGANIZATION_MANAGE_PLACES`, `ORGANIZATION_ACCESS` |
| `ORGANIZATION_PROJECT_MANAGER` | Organisation | `ORGANIZATION_MANAGE_ACTIONS`, `ORGANIZATION_MANAGE_PLACES`, `ORGANIZATION_ACCESS` |
| `ORGANIZATION_MEMBER` | Organisation | `ORGANIZATION_ACCESS` |
| `PROJECT_MANAGER` | Projet | `PROJECT_MANAGE_SETTINGS`, `PROJECT_EDIT_RECORDING_UNITS`, `PROJECT_EDIT_FINDS`, `PROJECT_EDIT_PHASES`, `PROJECT_EDIT_CONTAINERS` |
| `PROJECT_MEMBER` | Projet | `PROJECT_EDIT_RECORDING_UNITS`, `PROJECT_EDIT_FINDS`, `PROJECT_EDIT_PHASES`, `PROJECT_EDIT_CONTAINERS` |

Défini dans `ProfileService` (`fr.siamois.domain.services.permissions.ProfileService`).

## Règles "dernier titulaire" — hors système de permission

Trois règles empêchent de retirer le dernier titulaire d'un profil de gestion, pour éviter qu'une
institution/un projet/l'instance se retrouve sans administrateur :

- `PersonProfileAssignmentService.isNotLastSuperAdmin`
- `PersonProfileAssignmentService.isNotLastOrganizationManager`
- `PersonProfileAssignmentService.isNotLastProjectManager`

Ces règles restent basées sur `ProfileConstants` (profil nommé), pas sur `PermissionConstants` : le concept
"dernier titulaire de X" n'a pas d'équivalent direct côté permission cumulable (plusieurs profils peuvent
donner la même permission par cascade, donc "compter les titulaires d'une permission" n'a pas de sens univoque).
Elles sont donc explicitement hors du périmètre de la migration vers le système de permissions.

De même, `ActionUnitService.isManagerOf(action, person)` (compare uniquement l'auteur de l'action) est un
contrôle d'ownership fragile préexistant, pas un contrôle de rôle — laissé tel quel, documenté ici comme dette
technique connue plutôt que migré silencieusement (son retrait changerait un vrai comportement métier : un
créateur d'action peut ajouter du mobilier même sur une action clôturée, ce que `PROJECT_EDIT_FINDS` seul ne
permet pas).

## Garde des pages de settings

Toutes les pages de settings (institution et projet, listes et détails) sont protégées par un `f:event
type="preRenderView"` qui empêche le contournement du contrôle d'accès par URL directe :

1. **État manquant** (aucune institution/aucun projet sélectionné en session — ex. accès direct à une
   sous-page sans être passé par la liste) → redirection vers la page de sélection
   (`/settings/organisation` ou `/settings/project`).
2. **Permission manquante** → redirection vers `/error/404`, pour ne pas révéler l'existence de la page à un
   utilisateur non autorisé (à la différence d'un 403, qui confirme que la ressource existe mais est interdite).

Implémentation :

- Institution : `InstitutionDetailsBean.checkInstitutionOrRedirect()`, branché sur `institutionSettings.xhtml`
  et ses 3 sous-pages (`institutionInfoSettings.xhtml`, `institutionMembersSettings.xhtml`,
  `institution/thesaurusSettings.xhtml`). `InstitutionListSettingsBean.checkAccessOrRedirect()` protège la
  liste elle-même.
- Projet : `ProjectDetailsBean.checkProjectOrRedirect()`, branché sur `projectSettings.xhtml` et ses 4
  sous-pages (membres, thésaurus, tables, upload).
- Administration : `ApplicationMembersListBean.checkAccessOrRedirect()`, branché sur
  `userManagementSettings.xhtml` — c'était la seule page où le contrôle n'existait qu'au niveau du
  `@GetMapping` Spring MVC (`SettingsController`), donc entièrement contournable par accès direct à l'URL
  `.xhtml` (les vues JSF sont servies directement par le `FacesServlet`, indépendamment des routes Spring MVC).

Le pattern de redirection réutilise `RedirectBean.redirectTo(HttpStatus)`, déjà utilisé côté panels métier
(`RecordingUnitPanel`, `ActionUnitPanel`, etc.) pour les cas 403/404/500.

## Dette technique connue (hors périmètre de cette itération)

- `DELETE /api/v1/documents/{id}` réutilise la règle de lecture (appartenance à l'institution) au lieu d'une
  règle d'écriture — potentiel bug de sécurité, à traiter séparément.
- Route Spring Security `permitAll()` sur `/api/recording-units/**` sans contrôleur associé — configuration
  morte à nettoyer.
- `Person.getAuthorities()` retourne toujours une liste vide : le système de permissions n'est pas branché sur
  Spring Security (`@PreAuthorize`/`hasRole` sont donc inutilisables tels quels). Écarté explicitement du
  périmètre par choix produit — tout le contrôle d'accès passe par des appels manuels à
  `ProfilePermissionService`.
