# Champs et références qui ne sont pas (encore) branchés sur le thésaurus système

Le thésaurus système est décrit par [siamois-socle-commun-v2.rdf](siamois-socle-commun-v2.rdf). Chaque champ système porte son concept dans sa
définition (`CustomField`, avec son binding) ; les modèles d'export désignent un champ par ce concept. Ce document liste ce qui n'en profite pas encore.
Rien ici n'est bloquant : à traiter plus tard.

## 1. Champs système sans concept — à traiter après la première passe

Ces champs existent dans les formulaires, mais le thésaurus (v2) n'a pas de concept pour eux. Leur définition (`CustomField`) n'a donc
**pas de concept** : un modèle d'export ne peut pas les désigner, et la colonne correspondante du référentiel reste sans mapping.

Pour les rendre exportables, dans l'ordre :
1. ajouter le champ au thésaurus (notation `SIAMOISFIELD#<table>.<propriété>`, dans `generate_thesaurus_socle.py`) ;
2. régénérer le `.rdf` et l'importer dans OpenTheso ;
3. écrire l'identifiant du concept dans la table `CONCEPT_IDS` du formulaire (`StudyForm`, `DocumentForm`) ;
4. rebrancher la règle dans les modèles livrés `export-templates/*.json` et retirer l'exception des tests
   `NationalTemplateResourceTest` et `UnderwaterTemplateResourceTest`.

### Étude (`StudyForm`, table `SIAST`)

| Binding | Remarque |
|---|---|
| `identifier` | identifiant de l'étude |
| `studyDate` | |

Les champs de traitement (`reportCode`, `treatmentStart`, `treatmentEnd`, `treatmentPlace`, `treatmentManager`, `treatmentIdentifier`,
`treatmentComments`) et le type (`type` → `SIAST.TYPE`) sont décrits par le thésaurus.

### Document (`DocumentForm`, table `SIAD`)

| Binding | Remarque |
|---|---|
| `identifier` | colonne `identifiant_documentation` du référentiel : **seule colonne des modèles livrés sans mapping** |
| `otherIdentifiers` | |
| `crs`, `externalUrl`, `originalPath`, `rights` | |

Rapprochements déjà appliqués : `title` → `SIAD.name`, `finds` → `SIAD.specimens`, `description` → `SIAD.caption`,
`moldComment` → `SIAD.castComment`, `type` → `SIAD.TYPE`, `format` → `SIAD.FORMAT`, `category` → `SIAD.CATEGORY`,
`supportNatures` → `SIAD.NATURE`.

### Unité d'enregistrement (`RecordingUnit`)

Deux constantes inutilisées gardent un ancien identifiant du thésaurus précédent (à supprimer) : `recordingUnitSecondaryTypeConcept` (4286196) et
`recordingUnitIdentificationConcept` (4286197). Elles sont à supprimer, ou à raccorder si un champ les utilise un jour.

### Alias choisis à relire

Quelques rapprochements entre le code et le thésaurus sont des choix à confirmer : `excavators` → `SIARU.contributors`,
`creationTime` → `SIARU.openingDate`, `endDate` → `SIARU.closingDate`, `specimenType` → `SIAS.CAT`,
les relations stratigraphiques → `SIARU.STRATI.ANTERIOR / POSTERIOR / SYNCHRONOUS`.

## 2. Autres thésaurus écrits en dur

| Fichier | Référence | Usage |
|---|---|---|
| `GeoApiService` | `th252`, concept `4287976` | concept recherché pour les lieux |
| `InseeCommunesProvider`, `GeoplatformeAddressProvider` | `th252` | suggestions de lieux (INSEE, GéoPlateforme) |
| `UnitDefinitionInitializer` | `th252` | définitions d'unités |
| `SiamoisThesaurus.DEFAULT_URL` | `https://thesaurus.mom.fr/?idt=th252` | thésaurus par défaut proposé |
| `DefaultProjectIdentifierConfigSeeder` | `th1295`, concepts 4287627 et 4287628 | formats d'identifiant `US{NUM_UE:00}` et `F{NUM_UE:00}` |
