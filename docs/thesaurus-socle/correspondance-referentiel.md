# Correspondance référentiel national (th326) ↔ socle commun

Les **105 champs normalisés** du chapitre IV du [référentiel national](https://thesaurus.mom.fr/?idt=th326) (« Liste des champs
normalisés de l'inventaire détaillé », feuilles 1 à 9) rapprochés des champs du [socle commun](siamois-socle-commun.rdf). Le
chapitre V (inventaires simplifiés) reprend un sous-ensemble des mêmes champs, il n'est pas détaillé à part.

- ✅ le champ du socle porte un `skos:exactMatch` vers le champ du référentiel (rapprochement déjà dans le RDF) ;
- ≈ rapprochement que j'ai fait à la main (champ proche, fusion de feuilles ou de types) : **à valider** ;
- ❌ aucun champ dans le socle.

Un champ du référentiel qui revient dans plusieurs feuilles (`code_OA_NAT`, `matiere`, `lieu_de_conservation`…) est une ligne.
`identifiant_parcelle` figure deux fois : ce sont deux champs distincts du référentiel (feuilles 2 et 5).

**Bilan : 68 ✅, 25 ≈, 12 ❌ sur 105.**

| Champ du référentiel | Feuille(s) | Type | Champ(s) du socle (table · libellé, code) | | Remarque |
|---|---|---|---|---|---|
| `code_OA_NAT` | 1 Opération, 2 Géographie (terrestre), 2 Géographie (sous-marin), 3 UE, 4 Relations, 5 Mobilier, 6 Anthropo., 7 Prélèvements, 8 Traitements, 9 Documentation | libre | Projet · Code OA (`SIAAU.oaCode`) | ✅ |  |
| `region_facade_maritime_OA` | 1 Opération | libre | Projet · Région / façade maritime (`SIAAU.regionMaritimeFacade`) | ✅ |  |
| `type_OA` | 1 Opération | liste | Projet · Type (`SIAAU.TYPE`) | ✅ |  |
| `intitule_OA` | 1 Opération | libre | Projet · Nom (`SIAAU.name`) | ✅ |  |
| `responsable_scientifique_OA` | 1 Opération | libre | Projet · Responsable scientifique (`SIAAU.scientificManager`) | ✅ |  |
| `date_debut_terrain_OA` | 1 Opération | libre | Projet · Date de début (`SIAAU.beginDate`) | ✅ |  |
| `date_fin_terrain_OA` | 1 Opération | libre | Projet · Date de fin (`SIAAU.endDate`) | ✅ |  |
| `chronologie_OA` | 1 Opération | liste | Projet · Périodes (`SIAAU.PERIODS`), Phase · Périodes (`SIAPHASE.PERIOD`) | ✅ |  |
| `interpretations_concernees_OA` | 1 Opération | libre | Projet · Sujets (`SIAAU.SUBJECTS`) | ✅ |  |
| `code_document_georeferencement_OA` | 1 Opération | libre | Projet · Plan de géoréférencement (`SIAAU.georeferencingDocument`) | ✅ |  |
| `informations_complementaires_OA` | 1 Opération, 2 Géographie (sous-marin) | libre | Projet · Commentaires (`SIAAU.comments`) | ✅ |  |
| `organisme_rattachement_OA` | 1 Opération | libre | Projet · Structure porteuse / Opérateur (`SIAAU.hostStructure`) | ✅ |  |
| `identifiant_parcelle` | 2 Géographie (terrestre) | libre | Lieu · Nom (`SIASU.name`) / Lieu · Code (`SIASU.code`) d'un lieu de type parcelle | ≈ | Pas de champ « identifiant de parcelle » dédié |
| `nom_commune_OA` | 2 Géographie (terrestre), 2 Géographie (sous-marin) | libre | Lieu · Nom (`SIASU.name`) d'un lieu de type commune | ≈ | Le lieu porte le nom ; le code INSEE est à part |
| `numero_INSEE_commune_OA` | 2 Géographie (terrestre), 2 Géographie (sous-marin) | libre | Lieu · Code INSEE (`SIASU.inseeCode`) | ✅ |  |
| `lieu_dit_OA` | 2 Géographie (terrestre) | libre | Lieu · Lieu-dit (`SIASU.localityName`) | ✅ |  |
| `adresse_OA` | 2 Géographie (terrestre) | libre | Lieu · Adresse (`SIASU.address`) | ✅ |  |
| `informations_complementaires_parcelle` | 2 Géographie (terrestre) | libre | Lieu · Commentaires (`SIASU.comments`) | ✅ |  |
| `carte_SHOM_numero` | 2 Géographie (sous-marin) | libre | Lieu · Numéro de carte Shom (`SIASU.shomMapNumber`) | ✅ |  |
| `intitule_lieu_OA` | 2 Géographie (sous-marin) | libre | Lieu · Nom (`SIASU.name`) | ≈ | Intitulé du lieu de l'opération en mer |
| `latitude_OA` | 2 Géographie (sous-marin) | libre | – (géométrie du lieu, non décrite dans le thésaurus) | ❌ | Coordonnées : la géométrie existe côté application, pas de concept de champ |
| `longitude_OA` | 2 Géographie (sous-marin) | libre | – (géométrie du lieu, non décrite dans le thésaurus) | ❌ | Idem latitude |
| `profondeur_OA` | 2 Géographie (sous-marin) | libre | Projet · Zmin (`SIAAU.zmin`) | ≈ | « profondeur maximale en milieu sous-marin » (définition de zmin) |
| `type_UE` | 3 UE | liste | Enregistrement · Type (`SIARU.TYPE`) | ✅ |  |
| `identifiant_UE` | 3 UE | libre | Enregistrement · Identifiant (`SIARU.fullIdentifier`) | ✅ |  |
| `nature_UE` | 3 UE | liste | Enregistrement · Nature (`SIARU.GEOMORPHO`) | ✅ |  |
| `description_UE` | 3 UE | libre | Enregistrement · Description (`SIARU.description`) | ✅ |  |
| `interpretation_UE` | 3 UE | libre | Enregistrement · Interprétation normalisée (`SIARU.INTERPRETATION`) | ✅ |  |
| `Zsup_UE` | 3 UE | libre | Enregistrement · Altitude supérieure (`SIARU.zSup`) | ✅ |  |
| `Zinf_UE` | 3 UE | libre | Enregistrement · Altitude inférieure (`SIARU.zInf`) | ✅ |  |
| `chronologie_UE` | 3 UE | libre | Enregistrement · Attribution chronologique (`SIARU.CHRONO`) | ✅ |  |
| `TPQ_UE` | 3 UE | libre | Enregistrement · TPQ (`SIARU.tpq`) | ✅ |  |
| `TAQ_UE` | 3 UE | libre | Enregistrement · TAQ (`SIARU.taq`) | ✅ |  |
| `informations_complementaires_UE` | 3 UE | libre | Enregistrement · Commentaires (`SIARU.comments`) | ✅ |  |
| `code_UE1` | 4 Relations | libre | Enregistrement · Relation vers une unité antérieure (`SIARU.STRATI.ANTERIOR`) / postérieure (`SIARU.STRATI.POSTERIOR`) / synchrone (`SIARU.STRATI.SYNCHRONOUS`) (unité source) | ≈ | Le socle sépare la relation en trois champs selon le sens |
| `type_relation_UE` | 4 Relations | liste | Enregistrement · Type de relation (`SIARU.STRATI`) | ✅ |  |
| `code_UE2` | 4 Relations | libre | Enregistrement · Relation vers une unité antérieure (`SIARU.STRATI.ANTERIOR`) / postérieure (`SIARU.STRATI.POSTERIOR`) / synchrone (`SIARU.STRATI.SYNCHRONOUS`) (unité cible) | ≈ | Idem |
| `informations_complementaires_relation` | 4 Relations | libre | – | ❌ | Pas de commentaire sur une relation stratigraphique |
| `identifiant_objet-lot` | 5 Mobilier | libre | Mobilier · Identifiant (`SIAS.fullIdentifier`) | ✅ |  |
| `objet-lot` | 5 Mobilier | liste | Mobilier · Type (`SIAS.CAT`) | ≈ | Objet ou lot : mêlé dans SIAS.CAT aux types de mobilier (cf. README) |
| `matiere` | 5 Mobilier, 6 Anthropo. | liste | Mobilier · Matière (`SIAS.MATIERE`) | ✅ |  |
| `classe` | 5 Mobilier, 6 Anthropo. | liste | Mobilier · Classe (`SIAS.CLASS`) | ✅ |  |
| `determination_objet-lot` | 5 Mobilier | libre | Mobilier · Interprétation (`SIAS.INTERPRETATION`) | ✅ |  |
| `chronologie_objet-lot` | 5 Mobilier | libre | Mobilier · Attribution chronologique (`SIAS.CHRONO`) | ✅ |  |
| `TPQ_objet-lot` | 5 Mobilier | libre | Mobilier · TPQ (`SIAS.tpq`) | ✅ |  |
| `nombre_element_objet-lot` | 5 Mobilier | libre | Mobilier · Nombre d'éléments (`SIAS.numberOfElements`) | ✅ |  |
| `poids_objet-lot` | 5 Mobilier | libre | Mobilier · Poids (`SIAS.weight`) | ✅ |  |
| `unite_poids_objet-lot` | 5 Mobilier | liste | unité de Mobilier · Poids (`SIAS.weight`) | ≈ | Mesure avec unité : l'unité fait partie de la valeur |
| `etat_sanitaire_objet-lot` | 5 Mobilier | liste | Mobilier · État sanitaire (`SIAS.SANITARY`) | ✅ |  |
| `alerte_sanitaire_objet-lot` | 5 Mobilier | libre | Mobilier · Alerte sanitaire (`SIAS.sanitaryAlert`) | ✅ |  |
| `versement_objet-lot` | 5 Mobilier | liste | Mobilier · Versement (`SIAS.TRANSFER`) | ✅ |  |
| `proposition_conservation_selective_objet-lot` | 5 Mobilier | liste | Mobilier · Conservation sélective (`SIAS.SELECTIVECONSERVATION`) | ✅ |  |
| `commentaire_proposition_conservation_selective_objet-lot` | 5 Mobilier | libre | Mobilier · Commentaire de conservation sélective (`SIAS.selectiveConservationComment`) | ✅ |  |
| `code_UE` | 5 Mobilier, 6 Anthropo. | libre | Mobilier · Unité d'enregistrement (`SIAS.recordingUnit`) | ✅ |  |
| `interet_particulier_objet-lot` | 5 Mobilier | libre | Mobilier · Intérêt particulier (`SIAS.particularInterest`) | ✅ |  |
| `date_decouverte_objet-lot` | 5 Mobilier | libre | Mobilier · Date de collecte (`SIAS.collectionDate`) | ✅ |  |
| `identifiant_parcelle` | 5 Mobilier | libre | – | ❌ | Parcelle de découverte du mobilier : pas de champ |
| `identifiant_contenant` | 5 Mobilier, 6 Anthropo., 7 Prélèvements, 9 Documentation | libre | Contenant · Identifiant (`SIAC.identifier`), Mobilier · Contenant (`SIAS.containers`) | ✅ |  |
| `lieu_de_conservation` | 5 Mobilier, 6 Anthropo., 7 Prélèvements, 9 Documentation | libre | Contenant · Lieu (`SIAC.spatialUnit`), Document · Lieu de conservation (`SIAD.storageLocation`), Mobilier · Lieu de conservation (`SIAS.storageLocation`) | ✅ |  |
| `informations_complementaires_objet-lot` | 5 Mobilier | libre | Mobilier · Commentaires (`SIAS.comments`) | ✅ |  |
| `identifiant_VAB` | 6 Anthropo. | libre | Mobilier · Identifiant (`SIAS.fullIdentifier`) (type VAB) | ≈ |  |
| `code_objet-lot` | 6 Anthropo. | libre | Mobilier · Fait partie de (`SIAS.parents`) | ≈ | Objet contenant (vase, sarcophage) : lien « fait partie de » |
| `determination_VAB` | 6 Anthropo. | libre | Mobilier · Interprétation (`SIAS.INTERPRETATION`) (type VAB) | ≈ |  |
| `nombre_element_VAB` | 6 Anthropo. | libre | Mobilier · Nombre d'éléments (`SIAS.numberOfElements`) (type VAB) | ≈ |  |
| `poids_VAB` | 6 Anthropo. | libre | Mobilier · Poids (`SIAS.weight`) (type VAB) | ≈ |  |
| `unite_poids_VAB` | 6 Anthropo. | liste | unité de Mobilier · Poids (`SIAS.weight`) | ≈ |  |
| `prelevement_VAB` | 6 Anthropo. | libre | Mobilier · Contient (`SIAS.children`) / Mobilier · Méthode de prélèvement (`SIAS.METHOD`) | ≈ | Prélèvements faits sur le VAB : fiches de type prélèvement liées |
| `alerte_sanitaire_VAB` | 6 Anthropo. | libre | Mobilier · Alerte sanitaire (`SIAS.sanitaryAlert`) (type VAB) | ≈ |  |
| `versement_VAB` | 6 Anthropo. | liste | Mobilier · Versement (`SIAS.TRANSFER`) (type VAB) | ≈ |  |
| `potentiel_etude_VAB` | 6 Anthropo. | liste | Mobilier · Potentiel d'étude (`SIAS.studyPotential`) | ✅ |  |
| `commentaire_potentiel_etude_VAB` | 6 Anthropo. | libre | Mobilier · Commentaire du potentiel d'étude (`SIAS.studyPotentialComment`) | ✅ |  |
| `informations_complementaires_VAB` | 6 Anthropo. | libre | Mobilier · Commentaires (`SIAS.comments`) (type VAB) | ≈ |  |
| `identifiant_prelevement` | 7 Prélèvements | libre | Mobilier · Identifiant (`SIAS.fullIdentifier`) (type prélèvement) | ≈ |  |
| `nature_prelevement` | 7 Prélèvements | libre | Mobilier · Interprétation (`SIAS.INTERPRETATION`) (type prélèvement) | ≈ | Charbon, sédiment, bois… : la liste de démonstration est dans le thésaurus |
| `auteur_prelevement` | 7 Prélèvements | libre | Mobilier · Collecteurs (`SIAS.collectors`) | ≈ |  |
| `objectif_prelevement` | 7 Prélèvements | libre | Mobilier · Objectif du prélèvement (`SIAS.samplingPurpose`) | ✅ |  |
| `poids_volume_prelevement` | 7 Prélèvements | libre | Mobilier · Poids (`SIAS.weight`) (type prélèvement) | ≈ | Poids, volume ou longueur : un seul champ de mesure |
| `unite_poids_volume_prelevement` | 7 Prélèvements | liste | unité de Mobilier · Poids (`SIAS.weight`) | ≈ |  |
| `code_element_provenance` | 7 Prélèvements | libre | Mobilier · Unité d'enregistrement (`SIAS.recordingUnit`) / Mobilier · Fait partie de (`SIAS.parents`) | ≈ | UE, objet, lot ou VAB de provenance |
| `condition_preservation_prelevement` | 7 Prélèvements | libre | Mobilier · Condition de préservation (`SIAS.preservationCondition`) | ✅ |  |
| `date_validite_prelevement` | 7 Prélèvements | libre | Mobilier · Date de validité (`SIAS.validityDate`) | ✅ |  |
| `informations_complementaires_prelevement` | 7 Prélèvements | libre | Mobilier · Commentaires (`SIAS.comments`) (type prélèvement) | ≈ |  |
| `code_element_traite` | 8 Traitements | libre | Étude · Mobilier (`SIAST.specimens`) | ✅ |  |
| `type_traitement` | 8 Traitements | liste | Étude · Type de traitement (`SIAST.TYPETRAIT`) | ✅ |  |
| `code_rapport` | 8 Traitements | libre | – (dans l'application : SIAST reportCode) | ❌ | Pas de concept de champ dans le thésaurus (cf. champs-hors-socle.md) |
| `periode_traitement` | 8 Traitements | libre | – (dans l'application : treatmentStart / treatmentEnd) | ❌ | Idem |
| `lieu_traitement` | 8 Traitements | libre | – (dans l'application : treatmentPlace) | ❌ | Idem |
| `nom_responsable_traitement` | 8 Traitements | libre | – (dans l'application : treatmentManager) | ❌ | Idem |
| `identifiant_lie_au_traitement` | 8 Traitements | libre | – (dans l'application : treatmentIdentifier) | ❌ | Idem |
| `informations_complementaires_traitement` | 8 Traitements | libre | – (dans l'application : treatmentComments) | ❌ | Idem |
| `identifiant_documentation` | 9 Documentation | libre | – (dans l'application : SIAD identifier) | ❌ | Champ présent dans le formulaire, absent du thésaurus |
| `titre_documentation` | 9 Documentation | libre | Document · Nom du document (`SIAD.name`) | ✅ |  |
| `categorie_documentation` | 9 Documentation | liste | Document · Catégorie (`SIAD.CATEGORY`) | ✅ |  |
| `type_documentation` | 9 Documentation | liste | Document · Type de document (`SIAD.TYPE`) | ✅ |  |
| `nature_support_documentation` | 9 Documentation | liste | Document · Nature du support (`SIAD.NATURE`) | ✅ |  |
| `format_documentation` | 9 Documentation | libre | Document · Format du fichier (`SIAD.FORMAT`) | ✅ |  |
| `auteur_documentation` | 9 Documentation | libre | Document · Auteurs (`SIAD.authors`) | ✅ |  |
| `contributeur_documentation` | 9 Documentation | libre | Document · Contributeurs (`SIAD.contributors`) | ✅ |  |
| `editeur_documentation` | 9 Documentation | libre | Document · Éditeur (`SIAD.publisher`) | ✅ |  |
| `date_realisation_documentation` | 9 Documentation | libre | Document · Date de réalisation (`SIAD.productionDate`) | ✅ |  |
| `description_legende_documentation` | 9 Documentation | libre | Document · Description / légende (`SIAD.caption`) | ✅ |  |
| `nombre_element_documentation` | 9 Documentation | libre | Document · Nombre d'éléments (`SIAD.itemCount`) | ✅ |  |
| `code_element_associe` | 9 Documentation | libre | Document · Unités d'enregistrement (`SIAD.recordingUnits`) | ✅ |  |
| `commentaire_moulage` | 9 Documentation | libre | Document · Commentaire de moulage (`SIAD.castComment`) | ✅ |  |
| `informations_complementaires_documentation` | 9 Documentation | libre | – (dans l'application : SIAD comments) | ❌ | Idem |

## Champs du socle sans équivalent dans le référentiel (chapitre IV)

81 des 162 champs du socle ne sont pas rapprochés d'un champ du chapitre IV. Une partie est normale : le chapitre IV
ne couvre que l'inventaire détaillé du rapport, alors que le socle décrit aussi la saisie (liens entre fiches, fichiers, équipes).

- **Projet** (21) : `SIAAU.identifier` (Identifiant) ; `SIAAU.STATUS` (Statut du projet) ; `SIAAU.mainLocation` (Commune ou localisation principale) ; `SIAAU.spatialContext` (Localisation précise) ; `SIAAU.developer` (Aménageur) ; `SIAAU.prescriptionOrderNumber` (Numéro de l'arrêté de prescription) ; `SIAAU.prescriptionOrderDate` (Date de l'arrêté de prescription) ; `SIAAU.designationOrderNumber` (Numéro de l'arrêté de désignation) ; `SIAAU.designationOrderDate` (Date de l'arrêté de désignation) ; `SIAAU.scientificNotice` (Notice scientifique) ; `SIAAU.SYSTEM` (Système) ; `SIAAU.zmax` (Zmax) ; `SIAAU.prescribedArea` (Surface prescrite) ; `SIAAU.excavatedArea` (Surface fouillée) ; `SIAAU.accessibleArea` (Surface accessible) ; `SIAAU.openingRate` (Taux d'ouverture) ; `SIAAU.volumeCount` (Nombre de volumes) ; `SIAAU.pageCount` (Nombre de pages) ; `SIAAU.figureCount` (Nombre de figures) ; `SIAAU.appendixCount` (Nombre d'annexes) ; `SIAAU.recordingUnits` (Unités d'enregistrement)
- **Lieu** (2) : `SIASU.TYPE` (Type) ; `SIASU.placeNumber` (Identifiant)
- **Enregistrement** (17) : `SIARU.author` (Auteur scientifique/technique) ; `SIARU.contributors` (Contributeur(s) scientifique(s)/technique(s)) ; `SIARU.openingDate` (Date d'ouverture) ; `SIARU.closingDate` (Date de fermeture) ; `SIARU.spatialUnit` (Lieu(x)) ; `SIARU.actionUnit` (Projet) ; `SIARU.GEOMORPHOAGENT` (Agent géomorphologique) ; `SIARU.EROSIONSHAPE` (Forme de l'érosion) ; `SIARU.EROSIONPROFILE` (Profil de l'érosion) ; `SIARU.EROSIONORIENTATION` (Orientation de l'érosion) ; `SIARU.matrixColor` (Couleur de la matrice) ; `SIARU.phases` (Phases) ; `SIARU.parents` (Fait partie de) ; `SIARU.children` (Contient) ; `SIARU.specimenList` (Mobilier) ; `SIARU.STRATI.POSTERIOR` (Relation vers une unité postérieure) ; `SIARU.STRATI.SYNCHRONOUS` (Relation vers une unité synchrone)
- **Mobilier** (7) : `SIAS.otherIdentifier` (Autre identifiant) ; `SIAS.isolationNumber` (Identifiant d'isolation) ; `SIAS.authors` (Auteurs) ; `SIAS.description` (Description) ; `SIAS.taq` (TAQ) ; `SIAS.phases` (Phases) ; `SIAS.actionUnit` (Projet)
- **Étude** (4) : `SIAST.TYPE` (Type) ; `SIAST.METHODE` (Méthode) ; `SIAST.actionUnit` (Projet) ; `SIAST.recordingUnits` (Unités d'enregistrement)
- **Document** (10) : `SIAD.SCALE` (Échelle) ; `SIAD.LANGUAGE` (Langue) ; `SIAD.KEYWORD` (Mots-clés) ; `SIAD.actionUnit` (Projet) ; `SIAD.sizeMb` (Taille (Mo)) ; `SIAD.file` (Fichier) ; `SIAD.specimens` (Mobilier) ; `SIAD.places` (Lieux) ; `SIAD.phases` (Phases) ; `SIAD.containers` (Contenants)
- **Contenant** (7) : `SIAC.TYPE` (Type) ; `SIAC.actionUnit` (Projet) ; `SIAC.length` (Longueur) ; `SIAC.width` (Largeur) ; `SIAC.height` (Hauteur) ; `SIAC.weight` (Poids) ; `SIAC.specimens` (Mobilier)
- **Phase** (10) : `SIAPHASE.TYPE` (Type) ; `SIAPHASE.identifier` (Identifiant) ; `SIAPHASE.title` (Titre) ; `SIAPHASE.description` (Description) ; `SIAPHASE.orderNumber` (Numéro d'ordre) ; `SIAPHASE.lowerBound` (Borne inférieure) ; `SIAPHASE.upperBound` (Borne supérieure) ; `SIAPHASE.KEYWORD` (Mots-clés) ; `SIAPHASE.actionUnit` (Projet) ; `SIAPHASE.recordingUnits` (Unités d'enregistrement)
- **Personne** (1) : `SIAP.ROLE` (Rôle dans l'équipe)
- **Mesure** (2) : `SIAMD.TYPE` (Type de mesure) ; `SIAMD.NATURE` (Nature de la mesure)

Pour mémoire, plusieurs champs du projet (`SIAAU.developer`, arrêtés de prescription et de désignation, surfaces, notice
scientifique…) citent le référentiel mais viennent des chapitres I à III (page de titre, notice, annexes), pas du chapitre IV.

## À relire

- Les 25 ≈ ci-dessus, en particulier le mobilier de type VAB et prélèvement : j'ai considéré que ce sont des types de `SIAS`
  qui reprennent les mêmes champs que l'objet/lot, ce que le référentiel sépare en trois feuilles.
- Les 12 ❌ sont surtout les **traitements** (feuille 8), la **documentation** (identifiant, commentaire), les **coordonnées** du
  milieu sous-marin et le **commentaire d'une relation stratigraphique**. Les champs de traitement et de documentation existent
  dans l'application mais pas dans le thésaurus (voir [champs-hors-socle.md](champs-hors-socle.md)).
