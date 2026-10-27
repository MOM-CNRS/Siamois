# Champs et références qui ne sont pas (encore) branchés sur le th332

Le thésaurus système est le **th332** (« Socle commun », [siamois-socle-commun.rdf](siamois-socle-commun.rdf)). Les concepts des
champs système y sont lus par la classe `SystemConcepts` à partir de `src/main/resources/thesaurus-system-concepts.properties`
(clé `TABLE.propriété` ou `TABLE.CODE`, par exemple `SIAAU.name`, `SIARU.TYPE`). Ce document liste ce qui n'en profite pas encore.
Rien ici n'est bloquant : à traiter plus tard.

## 1. Champs système que le th332 ne décrit pas

Ces champs existent dans les formulaires, mais le thésaurus n'a pas de concept pour eux. Ils gardent un identifiant symbolique
sous le th332 (`SystemConcepts.id(table, binding, repli)`), par exemple `th332|study.reportCode` : la clé existe côté code, pas dans
le thésaurus. Pour les migrer, il faut d'abord ajouter le champ au thésaurus (notation `SIAMOISFIELD#<table>.<propriété>`), régénérer
le `.rdf`, puis régénérer le fichier `.properties`.

### Étude (`StudyForm`, table `SIAST`)

| Binding | Remarque |
|---|---|
| `identifier` | identifiant de l'étude |
| `reportCode` | feuille « traitement » |
| `studyDate` | |
| `treatmentStart`, `treatmentEnd` | feuille « traitement » |
| `treatmentPlace`, `treatmentManager`, `treatmentIdentifier`, `treatmentComments` | feuille « traitement » |

Le type d'étude (`type`) est branché sur `SIAST.TYPE`.

### Document (`DocumentForm`, table `SIAD`)

| Binding | Remarque |
|---|---|
| `identifier`, `otherIdentifiers` | |
| `comments` | |
| `crs`, `externalUrl`, `originalPath`, `rights` | |

Alias déjà appliqués : `title` → `SIAD.name`, `finds` → `SIAD.specimens`, `description` → `SIAD.caption`,
`moldComment` → `SIAD.castComment`, `type` → `SIAD.TYPE`, `format` → `SIAD.FORMAT`.

### Unité d'enregistrement (`RecordingUnit`)

Deux constantes inutilisées gardent leur ancien identifiant : `recordingUnitSecondaryTypeConcept` (4286196) et
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

## 3. Anciens modèles d'export

Un modèle enregistré avant la migration désigne les champs système par leur identifiant `th230`. Il reste exportable : le résolveur
(`ExportFieldResolver`) traduit l'ancien identifiant grâce aux lignes `legacy.<table>.<ancien id>` du fichier `.properties`. L'éditeur,
lui, les affiche comme « non résolus ». Pour les basculer, supprimer le modèle livré dans la base et rouvrir la liste : il est recréé
depuis les fichiers `export-templates/*.json`, déjà écrits sur le th332.
