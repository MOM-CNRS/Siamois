# Modèles d'export — champs et chemins par entité

Généré depuis le code (champs système) le 2026-10-26. Les champs **additionnels** (configurés par projet et par type) s'ajoutent à ces listes : ils ne sont pas connus à l'avance.

Un champ se lit par son concept ; un chemin de jointure suit des navigations séparées par un point (`project.name`, `recordingUnit.spatialUnit.name`).


## Unité d’enregistrement (`RECORDING_UNIT`)

**Navigations (point)** : `project` → PROJECT, `spatialUnit` → SPATIAL_UNIT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|
| `spatialUnit` | Lieu(s) | SelectOneSpatialUnit |
| `parents` | Fait partie de | SelectMultipleRecordingUnit |
| `children` | Contient | SelectMultipleRecordingUnit |
| `anteriorRelationships` | Antérieur | StratigraphicRelationships |
| `posteriorRelationships` | Postérieur | StratigraphicRelationships |
| `synchronousRelationships` | Synchrone | StratigraphicRelationships |
| `specimenList` | Mobilier | SelectMultipleSpecimen |
| `type` | Type | SelectOneFromFieldCode |
| `geomorphologicalCycle` | Nature | SelectOneFromFieldCode |
| `geomorphologicalAgent` | Agent géomorphologique | SelectOneFromFieldCode |
| `normalizedInterpretation` | Interprétation normalisée | SelectOneFromFieldCode |
| `matrixColor` | Couleur de la matrice | Text |
| `actionUnit` | Projet | SelectOneActionUnit |
| `fullIdentifier` | Identifiant | Text |
| `erosionShape` | Forme de l'érosion | SelectOneFromFieldCode |
| `erosionProfile` | Profil de l'érosion | SelectOneFromFieldCode |
| `erosionOrientation` | Orientation de l'érosion | SelectOneFromFieldCode |
| `description` | Description | Text |
| `comments` | Commentaires | Text |
| `chronologicalPhase` | Attribution chronologique | SelectOneFromFieldCode |
| `tpq` | TPQ | Integer |
| `taq` | TAQ | Integer |
| `phases` | Phases | SelectMultiplePhase |
| `zInf` | Altitude inférieure | Measurement |
| `zSup` | Altitude supérieure | Measurement |
| `openingDate` | Date d'ouverture | DateTime |
| `closingDate` | Date de fermeture | DateTime |
| `author` | Auteur scientifique/technique | SelectOnePerson |
| `contributors` | Contributeur(s) scientifique(s)/technique(s) | SelectMultiplePerson |


## Mobilier (`SPECIMEN`)

**Navigations (point)** : `project` → PROJECT, `recordingUnit` → RECORDING_UNIT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|


## Phase (`PHASE`)

**Navigations (point)** : `project` → PROJECT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|
| `identifier` | Identifiant | Text |
| `type` | Type | SelectOneFromFieldCode |
| `title` | Titre | Text |
| `actionUnit` | Projet | SelectOneActionUnit |
| `orderNumber` | Numéro d'ordre | Integer |
| `keywords` | Mots-clés | SelectMultipleFromFieldCode |
| `description` | Description | Text |
| `recordingUnits` | Unités d'enregistrement | SelectMultipleRecordingUnit |
| `periods` | Périodes | SelectMultipleFromFieldCode |
| `lowerBound` | Borne inférieure | Integer |
| `upperBound` | Borne supérieure | Integer |


## Contenant (`CONTAINER`)

**Navigations (point)** : `project` → PROJECT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|
| `identifier` | Identifiant | Text |
| `type` | Type | SelectOneFromFieldCode |
| `spatialUnit` | Lieu | SelectOneSpatialUnit |
| `actionUnit` | Projet | SelectOneActionUnit |
| `specimens` | Mobilier | SelectMultipleSpecimen |
| `length` | Longueur | Measurement |
| `width` | Largeur | Measurement |
| `height` | Hauteur | Measurement |
| `weight` | Poids | Measurement |


## Étude (`STUDY`)

**Navigations (point)** : `project` → PROJECT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|
| `actionUnitField` |  | SelectOneActionUnit |
| `typeField` |  | SelectOneFromFieldCode |
| `recordingUnitsField` |  | SelectMultipleRecordingUnit |
| `specimensField` |  | SelectMultipleSpecimen |


## Document (`DOCUMENT`)

**Navigations (point)** : `project` → PROJECT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|
| `actionUnitField` |  | SelectOneActionUnit |
| `typeField` |  | SelectOneFromFieldCode |
| `authorsField` |  | SelectMultiplePerson |
| `contributorsField` |  | SelectMultiplePerson |
| `productionDateField` |  | DateTime |
| `itemCountField` |  | Integer |
| `sizeMbField` |  | Decimal |
| `file` |  | File |
| `recordingUnitsField` |  | SelectMultipleRecordingUnit |
| `findsField` |  | SelectMultipleSpecimen |
| `placesField` |  | SelectMultipleSpatialUnit |
| `phasesField` |  | SelectMultiplePhase |
| `containersField` |  | SelectMultipleContainer |


## Projet (action unit) (`PROJECT`)

**Navigations (point)** : `mainLocation` → SPATIAL_UNIT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|
| `name` | Nom | Text |
| `identifier` | Identifiant | Text |
| `type` | Type | SelectOneFromFieldCode |
| `beginDate` | Date de début | DateTime |
| `endDate` | Date de fin | DateTime |
| `mainLocation` | Commune ou localisation principale | SelectOneSpatialUnit |
| `spatialContext` | Localisation précise | SelectMultipleSpatialUnitTree |
| `oaCode` | Code OA | Text |
| `status` | Statut du projet | SelectOneFromFieldCode |
| `fieldStatus` | Statut du terrain | SelectOneFromFieldCode |
| `regionMaritimeFacade` | Région / façade maritime | Text |
| `georeferencingDocument` | Plan de géoréférencement | SelectOneDocument |
| `scientificManager` | Responsable scientifique | Text |
| `hostStructure` | Structure porteuse / Opérateur | Text |
| `developer` | Aménageur | Text |
| `prescriptionOrderNumber` | Numéro de l'arrêté de prescription | Text |
| `prescriptionOrderDate` | Date de l'arrêté de prescription | DateTime |
| `prescribedArea` | Surface prescrite | Decimal |
| `designationOrderNumber` | Numéro de l'arrêté de désignation | Text |
| `designationOrderDate` | Date de l'arrêté de désignation | DateTime |
| `developmentNature` | Nature de l'aménagement | SelectOneFromFieldCode |
| `system` | Système | SelectOneFromFieldCode |
| `zmin` | Zmin | Decimal |
| `zmax` | Zmax | Decimal |
| `excavatedArea` | Surface fouillée | Decimal |
| `accessibleArea` | Surface accessible | Decimal |
| `openingRate` | Taux d'ouverture | Decimal |
| `periods` | Périodes | SelectMultipleFromFieldCode |
| `subjects` | Sujets | SelectMultipleFromFieldCode |
| `volumeCount` | Nombre de volumes | Integer |
| `pageCount` | Nombre de pages | Integer |
| `figureCount` | Nombre de figures | Integer |
| `appendixCount` | Nombre d'annexes | Integer |
| `scientificNotice` | Notice scientifique | Text |
| `comments` | Commentaires | Text |
| `recordingUnitList` | Unités d'enregistrement | SelectMultipleRecordingUnit |


## Lieu (spatial unit) (`SPATIAL_UNIT`)

**Navigations (point)** : `project` → PROJECT

| Champ (valueBinding) | Libellé | Type |
|---|---|---|
| `name` | Nom | Text |
| `type` | Type | SelectOneFromFieldCode |
| `code` | Code | Text |
| `address` | Adresse | SelectOneAddress |
| `placeNumber` | Identifiant | Integer |
| `inseeCode` | Code INSEE | Text |
| `localityName` | Lieu-dit | Text |
| `shomMapNumber` | Numéro de carte Shom | Text |
| `comments` | Commentaires | Text |


## Tables techniques (source d'une feuille)

| Clé | Extrémités (chemin, 1re étape) | Valeurs lisibles |
|---|---|---|
| `STRATIGRAPHIC_RELATIONSHIP` | `unit1`, `unit2` → RECORDING_UNIT | `relationType`, `asynchronous`, `uncertain` |
| `RECORDING_UNIT_HIERARCHY` | `parent`, `child` → RECORDING_UNIT | — |

Exemple : depuis une relation stratigraphique, `unit1.project.name` ; depuis une UE, `spatialUnit.name`.
