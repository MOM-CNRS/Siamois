# Demande d'évolution SIAMOIS — support du plugin QGIS (modèle spatial de Quentin Verriez)

*Version 1 · comparée à `origin/main` au commit `26ad5bc1` · à destination de l'équipe SIAMOIS*

## Contexte

Le plugin QGIS produit un GeoPackage au format « Schéma de données spatiales SIAMOIS » (tables `ut_*`, `uo_*`, `ur_poly`, `ud_*`, `lien_*`, `projet`, `concept`, etc.) et le synchronise avec l'API `/api/v1`.
Trois évolutions côté SIAMOIS sont nécessaires. Le reste du besoin est déjà couvert par `main` (voir en fin de document).

---

## 1. Champ `table_modele_spatial` sur les types (bloquant)

### Besoin
Savoir dans quelle table du modèle spatial une unité d'enregistrement ou un mobilier doit être rangé. Le plugin s'en sert pour répartir les UE dans le GeoPackage, générer la table `concept` et filtrer les listes de types par table.

### État sur `main`
Absent. `RecordingUnitType` et `FindType` n'exposent que `concept`, `id`, `formBundle` et `identifierConfig`.

### Proposition
- Nouveau champ de configuration d'un type, à côté de `identifierConfig`, **par projet**. Une valeur par défaut au niveau de l'institution est possible.
- Valeurs autorisées :

| Valeur | Table du modèle |
|---|---|
| `ut_poly`, `ut_ligne`, `ut_point` | unités topographiques |
| `uo_poly`, `uo_point` | unités d'observation |
| `ur_poly` | unités de reconnaissance |
| `ud_poly`, `ud_ligne` | unités de documentation |
| `aucune` (valeur par défaut) | type non classé, non chargé dans le GeoPackage |

- Exposition dans l'API : champ `spatialModelTable` (nom à valider) dans `RecordingUnitType` et `FindType`, donc dans `GET /projects/{id}/recording-unit-types` et `/find-types`.
- Écriture : par l'écran de configuration des types (et l'endpoint de réglages du projet, `ProjectSettingsControllerApi`, si c'est là que se règlent les types).

### Règles de validation
- Valeur hors liste : refusée (400).
- Un mobilier ne peut pas être classé dans une table d'UE (un type de mobilier est `aucune` ou une table de points à définir avec Quentin).
- Changer la valeur d'un type déjà utilisé : autorisé, mais le plugin ne reclasse pas les entités déjà exportées (à documenter).

---

## 2. Écriture de la géométrie des mobiliers (bloquant)

### Besoin
Éditer la position d'un mobilier dans QGIS et la créer avec la position saisie sur la carte.

### État sur `main`
- L'entité `Specimen` a déjà une colonne `geom` (n'importe quelle géométrie, en pratique un point).
- `FindResource` expose `geom` en lecture (`PointDTO`).
- `FindPatchRequest` et `FindCreateRequest` **n'ont pas** de champ `geom`.

### Proposition
Ajouter `geom` aux deux requêtes, sur le modèle de `RecordingUnitPatchRequest` :
- `geom` (GeoJSON + `srid`) ; l'absence du champ ne change rien, `null` explicite efface la géométrie (technique `geomPresent` déjà utilisée pour les UE) ;
- même validation que les UE (SRID, géométrie valide) ;
- décider si `geom` reste un point (`PointDTO`) ou accepte toute géométrie (comme la colonne).

---

## 3. Révision et gestion de conflit sur le mobilier (important)

### Besoin
Détecter qu'un mobilier a été modifié sur le serveur depuis le chargement dans QGIS, comme pour les UE.

### État sur `main`
`syncRevision` (`@Version`) et `expectedRevision` n'existent que pour `RecordingUnit`. `Specimen` n'a pas de version, `FindResource` n'expose pas de révision, `FindPatchRequest` n'a pas `expectedRevision`.

### Proposition
- Ajouter une colonne de version à `Specimen` (migration).
- Exposer `syncRevision` dans `FindResource`.
- Accepter `expectedRevision` dans `FindPatchRequest`.
- Répondre 409 avec `SyncConflictResponse` (déjà en place pour les UE) en cas d'écart.

---

## Hors périmètre de cette demande (déjà couvert ou différé)

| Sujet | État |
|---|---|
| Projet parent d'une entité | Déjà exposé (`projectId`, `project`) |
| Identifiant à la création | `FindCreateRequest.identifier` existe ; à vérifier sur la création d'UE |
| Relations entre UE | Endpoints unitaires existants (`/parents`, `/children`, `/stratigraphic-relationships`, `/relations`) |
| Lecture en masse des relations d'un projet | Souhaitable (sinon un appel par UE) ; non bloquant pour la v1 |
| Synchronisation incrémentale (« modifié depuis ») | Différé |
| Renouvellement du jeton | Différé |
| Géométrie des documents | Hors v1 |

## Ordre proposé
1. Point 2 (le plus petit : la colonne existe déjà).
2. Point 3 (migration + réutilisation du mécanisme des UE).
3. Point 1 (nouveau champ de configuration, à valider avec Quentin pour les valeurs et la règle des mobiliers).
