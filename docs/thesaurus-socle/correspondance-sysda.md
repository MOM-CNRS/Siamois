# Correspondance SysDA ↔ socle commun

Colonnes des exports `SysDA_Export*.xlsx` (EA, Mobilier, Phases, RelationsStrati, US/UnitesStratigraphiques) rapprochées des champs
du thésaurus socle ([siamois-socle-commun.rdf](siamois-socle-commun.rdf), notation `SIAxx.champ`).
`EA` = entité archéologique (fait), `EAS` = ensemble. Le rapprochement est fait sur les libellés et les exemples de valeurs des
fichiers ; les lignes marquées **?** sont à confirmer avec toi.

Légende : ✅ correspondance directe · ≈ proche (conversion ou fusion) · ❌ aucun champ dans le socle.

## US (`SysDA_ExportUS_V2`, `SysDA_ExportUnitesStratigraphiques`) → `SIARU` (Enregistrement), type US

| Colonne SysDA | Champ du socle | |
|---|---|---|
| `OPE_NumPDCSO_US::NumOperationArcheologique` | `SIAAU.oaCode` (code OA du projet, via le lien projet) | ≈ |
| `NumSite` | – (composante du code OA) | ❌ |
| `NumOperation` | – (composante du code OA) | ❌ |
| `NumZoneUS` | `SIARU.fullIdentifier` | ≈ |
| `SectionCadastrale` | – (parcelle : `SIASU` n'a pas de champ section) | ❌ |
| `NumParcelle` | `SIASU.name` / `SIASU.code` d'un lieu de type parcelle | ≈ |
| `SecteurTexte` | `SIASU.name` d'un lieu de type secteur / tranchée **?** | ≈ |
| `NatureUS` | `SIARU.GEOMORPHO` (Nature) | ✅ |
| `Interprétation` | `SIARU.INTERPRETATION` | ✅ |
| `Identification` | `SIARU.description` (courte désignation, ex. « Terre végétale ») **?** | ≈ |
| `ComposantsGeologiquesMorphologie` | `SIARU.description` | ≈ |
| `ComposantsCulturels` | `SIARU.description` (ou liens `SIARU.specimenList`) **?** | ≈ |
| `Commentaires` | `SIARU.comments` | ✅ |
| `PeriodeCulturelle` | `SIARU.CHRONO` | ✅ |
| `AnneeDebutPeriodeCulturelle` | `SIARU.tpq` | ✅ |
| `AnneeFinPeriodeCulturelle` | `SIARU.taq` | ✅ |
| `Fouilleur` | `SIARU.contributors` | ✅ |
| `DateEnregTerrain` | `SIARU.openingDate` | ✅ |
| `AuteurEnregTerrain` | `SIARU.author` | ✅ |
| `NumEA` | `SIARU.parents` (fait contenant l'US) | ≈ |
| `EA::TypeEAAbreviation` | type de l'unité liée (`SIARU.TYPE` = fait) | ≈ |
| `NumEAS` | `SIARU.parents` (ensemble contenant l'US) | ≈ |
| `EAS::TypeEASAbreviation` | type de l'unité liée (`SIARU.TYPE` = ensemble) | ≈ |
| `NumPhase` | `SIARU.phases` | ✅ |

## EA (`SysDA_ExportEA`) → `SIARU`, type fait

| Colonne SysDA | Champ du socle | |
|---|---|---|
| `OPE_NumPDCSO_EA::NumOperationArcheologique` | `SIAAU.oaCode` (via le projet) | ≈ |
| `NumSite`, `NumOperation` | – (composantes du code OA) | ❌ |
| `NumEA` | `SIARU.fullIdentifier` | ≈ |
| `TypeEAAbreviation` | `SIARU.TYPE` (fait) | ≈ |
| `TypeEA` | `SIARU.TYPE` (libellé du type) ou nature du fait **?** | ≈ |
| `InterpretationEA` | `SIARU.INTERPRETATION` (« Fosse », « Fossé »…) | ✅ |
| `DescriptionEA` | `SIARU.description` | ✅ |
| `AnneeDebutLaPlusAncienne` | `SIARU.tpq` | ✅ |
| `AnneeFinLaPlusRecente` | `SIARU.taq` | ✅ |
| `DateDeSaisie` | `SIARU.openingDate` **?** (sinon date technique d'audit) | ≈ |
| `AuteurDeSaisie` | `SIARU.author` | ✅ |

## Relations stratigraphiques (`SysDA_ExportRelationsStrati`) → `SIARU.STRATI.*`

| Colonne SysDA | Champ du socle | |
|---|---|---|
| `NumPaDeCoSiZoneUS` | identifiant composite de l'US source (pas de champ) | ❌ |
| `NumZoneUS` | unité source de la relation (`SIARU.STRATI.*`) | ≈ |
| `RelationStrati` | `SIARU.STRATI` (type de relation : postérieure à, antérieure à, synchrone…) | ✅ |
| `NumPaDeCoSiZoneUS_2` | identifiant composite de l'US cible (pas de champ) | ❌ |
| `NumZoneUS2` | unité cible de la relation (`SIARU.STRATI.ANTERIOR` / `POSTERIOR` / `SYNCHRONOUS`) | ≈ |

## Mobilier (`SysDA_ExportMobilier`) → `SIAS`

| Colonne SysDA | Champ du socle | |
|---|---|---|
| `OPE_NumPDCSO_MOB::NumOperationArcheologique` | `SIAAU.oaCode` (via le projet) | ≈ |
| `OPE_NumPDCSO_MOB::Code_NumSite_Operation` | – (code site + opération, ex. `C309_01`) | ❌ |
| `NumZoneUS` | `SIAS.recordingUnit` | ✅ |
| `NumInventaire` | `SIAS.fullIdentifier` | ✅ |
| `NumIsoTerrain` | `SIAS.isolationNumber` | ✅ |
| `TypeDeLot` | `SIAS.CAT` (objet, lot, VAB, prélèvement) | ✅ |
| `MatiereCalcule` | `SIAS.MATIERE` | ✅ |
| `FamilleCalcule` | `SIAS.CLASS` | ✅ |
| `Identification` | `SIAS.INTERPRETATION` | ✅ |
| `Quantification` | `SIAS.numberOfElements` | ✅ |
| `Poids` | `SIAS.weight` | ✅ |
| `UnitePoids` | unité de `SIAS.weight` (mesure avec unité) | ✅ |
| `NbSacs` | – | ❌ |
| `EtatDeTraitement` | – (ni `SIAS.SANITARY` ni une étude : état de traitement du mobilier) **?** | ❌ |
| `Observations` | `SIAS.comments` | ✅ |
| `NumContenant` | `SIAS.containers` | ✅ |
| `ContenantsMobilier::ArchivageCalcul` | `SIAS.storageLocation` (ou `SIAC.spatialUnit`) | ≈ |

## Phases (`SysDA_ExportPhases`) → `SIAPHASE`

| Colonne SysDA | Champ du socle | |
|---|---|---|
| `OPE_NumPDCSO_PHASE::NumOperationArcheologique` | `SIAAU.oaCode` (via le projet) | ≈ |
| `NumSite`, `NumOperation` | – (composantes du code OA) | ❌ |
| `NumPhase` | `SIAPHASE.identifier` / `SIAPHASE.orderNumber` | ✅ |
| `PhaseType` | `SIAPHASE.TYPE` (ex. « Occupation ») | ✅ |
| `PhaseTitre` | `SIAPHASE.title` | ✅ |
| `PhaseDescription` | `SIAPHASE.description` | ✅ |
| `PhaseDatationTexte` | – (datation en texte libre ; `SIAPHASE.PERIOD` est une liste) | ❌ |
| `AnneeDebutLaPlusAncienne` | `SIAPHASE.lowerBound` | ✅ |
| `AnneeFinLaPlusRecente` | `SIAPHASE.upperBound` | ✅ |
| `AuteurDeSaisie` | – (métadonnée de saisie) | ❌ |
| `DateDeSaisie` | – (métadonnée de saisie) | ❌ |

## Ce qu'il faut retenir

- Sur les **70 lignes de colonnes** de ce tableau (une colonne qui revient dans plusieurs fichiers compte à chaque fois),
  **34** ont un champ du socle direct (✅), **21** un champ proche (≈) et **15** n'en ont aucun (❌).
- **Codes composites** (`NumSite`, `NumOperation`, `Code_NumSite_Operation`, `NumPaDeCoSi…`) : ils servent à construire un
  identifiant d'opération, le socle n'a que `SIAAU.oaCode`. À traiter à l'import, pas par un champ.
- **Absents du socle** : `NbSacs`, `EtatDeTraitement` (mobilier), `PhaseDatationTexte` (phase), `SectionCadastrale` (lieu),
  `DateDeSaisie` / `AuteurDeSaisie` (hors US/EA).
- **Fait et ensemble** : dans les exports, ils n'ont pas de nature ni d'agent géomorphologique (cf. la remarque sur `SIARU.GEOMORPHO`) ;
  `InterpretationEA`, `DescriptionEA` et les dates suffisent.
- **Le ✅ ne dit pas que les valeurs coïncident** : les listes (`NatureUS`, `Interprétation`, `MatiereCalcule`, `FamilleCalcule`…)
  doivent encore être alignées valeur par valeur (via les `skos:exactMatch` du thésaurus).
