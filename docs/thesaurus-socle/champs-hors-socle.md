# Champs et références qui ne sont pas (encore) branchés sur le thésaurus système

Le thésaurus système est décrit par [siamois-socle-commun-v2.rdf](siamois-socle-commun-v2.rdf). Chaque champ système porte son concept dans sa
définition (`CustomField`, avec son binding) ; les modèles d'export désignent un champ par ce concept. Ce document liste ce qui n'en profite pas encore.
Rien ici n'est bloquant : à traiter plus tard.

## 1. Champs ajoutés au thésaurus après la première passe

Ces champs n'avaient pas de concept ; ils en ont un depuis l'ajout dans th337 (identifiants attribués par OpenTheso, hors de la
plage du générateur). Chacun est écrit dans la définition du champ (`CONCEPT_IDS` des formulaires, `GEOM_CONCEPT` des tables).

| Concept (id th337) | Libellé | Notation | Champ |
|---|---|---|---|
| 4291327 | Géométrie du projet | `SIAMOISFIELD#SIAAU.geometry` | `ActionUnitForm.GEOM_FIELD` |
| 4291328 | Géométrie de l'UE | `SIAMOISFIELD#SIARU.geometry` | `RecordingUnitForm.GEOM_FIELD` |
| 4291329 | Géométrie du mobilier (point) | `SIAMOISFIELD#SIAS.geometry` | `SpecimenForm.geomField` |
| 4291330 | Identifiant du document | `SIAMOISFIELD#SIAD.identifier` | `DocumentForm` `identifier` (colonne `identifiant_documentation` des modèles livrés) |
| 4291331 | Autres identifiants | `SIAMOISFIELD#SIAD.otherIdentifiers` | `DocumentForm` `otherIdentifiers` |
| 4291332 | Système de coordonnées | `SIAMOISFIELD#SIAD.crs` | `DocumentForm` `crs` |
| 4291333 | Adresse externe | `SIAMOISFIELD#SIAD.externalUrl` | `DocumentForm` `externalUrl` |
| 4291334 | Chemin d'origine | `SIAMOISFIELD#SIAD.originalPath` | `DocumentForm` `originalPath` |
| 4291335 | Droits | `SIAMOISFIELD#SIAD.rights` | `DocumentForm` `rights` |
| 4291336 | Identifiant de l'étude/traitement | `SIAMOISFIELD#SIAST.identifier` | `StudyForm` `identifier` |
| 4291337 | Date de l'étude | `SIAMOISFIELD#SIAST.studyDate` | `StudyForm` `studyDate` |

Le script `generate_thesaurus_socle.py` déclare ces champs (bloc final) : s'il est relancé, il leur donne les ids qui suivent 1939, pas
ceux d'OpenTheso. Le `.rdf` livré dans le dépôt ne les contient pas encore ; l'export d'OpenTheso (« Siamois DEMO_th337.rdf ») fait foi.

Tous les champs système des tables du socle ont maintenant un concept ; la colonne `identifiant_documentation` des modèles livrés
est mappée.

### Unité d'enregistrement (`RecordingUnit`)

Deux constantes inutilisées gardent un ancien identifiant du thésaurus précédent (à supprimer) : `recordingUnitSecondaryTypeConcept` (4286196) et
`recordingUnitIdentificationConcept` (4286197). Elles sont à supprimer, ou à raccorder si un champ les utilise un jour.

### Alias choisis à relire

Quelques rapprochements entre le code et le thésaurus sont des choix à confirmer : `excavators` → `SIARU.contributors`,
`creationTime` → `SIARU.openingDate`, `endDate` → `SIARU.closingDate`, `specimenType` → `SIAS.CAT`,
les relations stratigraphiques → `SIARU.STRATI.ANTERIOR / POSTERIOR / SYNCHRONOUS`.

## 2. Autres thésaurus écrits en dur

Il n'en reste pas : les lieux (`GeoApiService`, `InseeCommunesProvider`, `GeoplatformeAddressProvider`), les unités
(`UnitDefinitionInitializer`), le thésaurus par défaut (`SiamoisThesaurus.DEFAULT_URL`), les types de départ des tables
(`siamois.default-types.*`, `DefaultProjectIdentifierConfigSeeder`) et la condition d'érosion de `form-layouts/UE.json` pointent tous vers th337.
