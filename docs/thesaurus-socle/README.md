# Thésaurus « Socle commun »

> Généré le 2026-10-23 par [generate_thesaurus_socle.py](generate_thesaurus_socle.py) à partir du th326 (référentiel national)
> et du th230 (ancien thésaurus SIAMOIS). Résultat : [siamois-socle-commun.rdf](siamois-socle-commun.rdf).
> **Non testé à l'import dans OpenTheso** : le fichier suit le format d'export du th230, mais l'import n'a pas été rejoué.

## Contenu

11 tables, 164 champs dont 46 à vocabulaire, 724 concepts, 142 collections. Tout champ du socle (formulaires `*Form.java`,
`SpatialUnit`) y est décrit, avec son type de donnée, son code et son équivalent dans le référentiel national quand il existe.
Les 46 codes de champ de l'application (`SIARU.TYPE`, `SIAS.CLASS`…) sont tous présents. Les listes sont des **démonstrations**,
pas des vocabulaires exhaustifs.

| Table | Notation | Champs à vocabulaire (code) |
|---|---|---|
| Projet | `SIAAU` | `TYPE`, `STATUS`, `FIELD_STATUS`, `SYSTEM`, `DEVELOPMENT_NATURE`, `PERIODS`, `SUBJECTS` |
| Lieu | `SIASU` | `TYPE` |
| Enregistrement | `SIARU` | `TYPE`, `NATURE`, `GEOMORPHO`, `GEOMORPHOAGENT`, `INTERPRETATION`, `EROSIONSHAPE`, `EROSIONPROFILE`, `EROSIONORIENTATION`, `CHRONO` |
| Stratigraphie | `SIARU.STRATI.TABLE` | `STRATI`, `STRATI.ANTERIOR`, `STRATI.POSTERIOR`, `STRATI.SYNCHRONOUS` |
| Mobilier | `SIAS` | `CAT`, `MATIERE`, `CLASS`, `INTERPRETATION`, `SAMPLINGNATURE`, `SANITARYALERT`, `CHRONO`, `SANITARY`, `TRANSFER`, `SELECTIVECONSERVATION` |
| Étude | `SIAST` | `TYPE` (type de traitement) |
| Document | `SIAD` | `TYPE` (pilote), `CATEGORY`, `NATURE`, `FORMAT`, `SCALE`, `LANGUAGE`, `KEYWORD` |
| Contenant, Phase, Personne, Mesure | `SIAC`, `SIAPHASE`, `SIAP`, `SIAMD` | `TYPE` / `PERIOD`, `KEYWORD` / `ROLE` / `TYPE`, `NATURE` |

## Modélisation

**Arbre.** Une seule branche de tête, `SIAMOIS#SIAAUTO` (celle que lit `ConceptApi.fetchFieldsBranch`), puis table → champ → valeurs.
Les champs à vocabulaire portent `SIAMOIS#<code>`, comme dans le th230. Les autres portent `SIAMOISFIELD#<table>.<propriété>`
(par exemple `SIAMOISFIELD#SIAS.sanitaryAlert`) : le chargeur ne retient que le préfixe `SIAMOIS#`, ils sont donc ignorés par
l'application, mais ils identifient le champ de façon stable.

**Une liste qui dépend du type.** Même mécanisme que le th230 (`Unité élémentaire` ↔ `Nature`, `Dépôt` ↔ ses interprétations), étendu :

1. Le champ porte **toutes** les valeurs possibles, une seule fois chacune.
2. Un lien `skos:related` (symétrique) relie le type, ou la valeur pilote, aux valeurs qu'il autorise. C'est ce que lit la règle
   `RELATED_CONCEPTS` (`fetchAutocompleteRelated`). Une valeur commune à plusieurs types (« Indéterminé ») est reliée à chacun.
3. **Aucune collection.** Elles ont été retirées (2026-10-31) : la règle de mise en page `RELATED_TO_TYPE` lit directement le lien du point 2.
   Un type relié à aucune valeur d'un champ laisse la liste complète du champ.
4. Un lien `skos:related` entre un type et un **champ** dit que le champ ne s'applique qu'à ce type (« Objectif du prélèvement »
   pour un prélèvement, « Échelle » pour un document graphique).
5. Le générateur affiche en fin de course, pour chaque liste par pilote, les pilotes sans aucun lien (ils retombent sur la liste complète).

Exemples livrés : interprétation d'une unité selon US / fait / ensemble / sépulture ; matière, classe et interprétation d'un
mobilier selon son type (céramique, métal, lithique, verre, faune, VAB, prélèvement) ; catégorie, support, format et échelle d'un
document selon son type (de document : un type reprend les valeurs de sa catégorie) ; nature d'une mesure selon son type de mesure.

**Alignement sur le référentiel.** Chaque champ ou valeur qui a un équivalent dans le th326 porte un `skos:exactMatch` vers son
URI, et l'altLabel garde le libellé national quand il diffère (`[métal] ferreux`). Une conversion à l'export peut s'appuyer dessus.

**Chronologie.** Reprise du th326 (`chronologie_OA`) sous les quatre champs concernés (`SIAAU.PERIODS`, `SIARU.CHRONO`, `SIAS.CHRONO`,
`SIAPHASE.PERIOD`). Elle est **dupliquée** quatre fois et non partagée : le chargeur ne descend que le sous-arbre de chaque code,
et je n'ai pas supposé qu'il supporte un concept à plusieurs parents.

## Décisions à relire

- **`SIARU.TYPE`** : j'ai mis les types du référentiel (US, fait, ensemble, UF, sépulture, sondage, tranchée, zone, secteur, carré)
  à la place d'« unité élémentaire / incluante / subdivision » du th230. Le rôle de ces trois anciennes valeurs (porter la
  visibilité de `Nature` et `Agent`) est repris par les liens type ↔ champ.
- **`SIAS.CAT`** mélange deux familles (nature du mobilier, conditionnement objet / lot / VAB / prélèvement) parce que l'export
  national lit « objet » et « lot » dans le type. Aucune collection ne les sépare (retirées).
- **Valeurs recopiées du th230** : nature, agent et interprétation d'une unité, avec leurs libellés et définitions (parfois en
  allemand dans l'original, les définitions « !!! » sont écartées).
- **Identifiants** : `th999`, concepts numérotés à partir de 1000, URI `https://thesaurus.mom.fr/?idc=…&idt=th999`. À régénérer avec
  `--th thNNN --base …` une fois le numéro réel connu ; OpenTheso peut aussi les réattribuer à l'import.
- Libellés en français et en anglais ; pas d'allemand sauf ce qui vient du th230.

## Régénérer

```bash
python3 -I docs/thesaurus-socle/generate_thesaurus_socle.py --ref "<th326>.rdf" --old "<th230>.rdf"
```

Le script s'arrête en erreur si un libellé du référentiel ou du th230 qu'il cite n'existe plus.

## Version 2 (temporaire, `siamois-socle-commun-v2.rdf`)

Le générateur écrit désormais `siamois-socle-commun-v2.rdf` (le th332 n'est pas touché). Changements par rapport à la version précédente :

- **Libellés** : projet `Nom` → `Intitulé`, `Périodes` → `Chronologie`, `Sujets` → `Interprétation`, `Structure porteuse` → `Organisme de
  rattachement`, `Commentaires` → `Informations complémentaires` (projet, enregistrement, mobilier) ; lieu `Nom` → `Intitulé`,
  `Commentaires` → `Informations complémentaires parcelle` ; mobilier `Interprétation` → `Détermination` ; document `Nom du document` → `Titre`.
  Les codes de champ (`SIAAU.PERIODS`, `SIAS.INTERPRETATION`…) ne changent pas.
- **Nouveaux champs** : projet `Date de début de terrain` / `Date de fin de terrain` (rapprochées de `date_*_terrain_OA`, les dates du projet
  ne le sont plus) et `Parcelles` (lien vers des lieux, rapproché de `identifiant_parcelle` de la feuille 2) ; lieu `Géométrie` (le modèle
  d'export la convertit en x / y) ; mobilier `Objet / lot` (case à cocher, rapprochée de `objet-lot`), `Volume` et `Nature du prélèvement`
  (liste `SIAS.SAMPLINGNATURE`, uniquement pour le type Prélèvement, valeurs du référentiel telles quelles) ; document `Informations complémentaires`.
- **Types de mobilier** : « objet » et « lot » ne sont plus des types de `SIAS.CAT`.
- **Interprétation du mobilier** : plus de liste pour le type Prélèvement (ses valeurs sont dans `Nature du prélèvement`).
- **Étude = traitement** : la table garde son nom, mais elle correspond à la feuille « traitements » du référentiel. Son type (`SIAST.TYPE`) est
  le type de traitement du référentiel (25 valeurs) ; les anciens types (traitement, datation, analyse, étude spécialisée) disparaissent. Les
  champs de traitement (code du rapport, début et fin, lieu, responsable, identifiant lié, informations complémentaires) s'appliquent à toute la table.
- **Plus de méthode** : les champs « Méthode de prélèvement » (`SIAS.METHOD`) et « Méthode » de l'étude (`SIAST.METHODE`) sont supprimés, sans remplacement.

### Vocabulaire du référentiel complété (instance de test des collègues)

Le générateur lit par défaut `Referentiel_th326_instance_test*.rdf` (le th326 avec des valeurs en plus) et, à défaut, le référentiel
national d'origine. Les valeurs ajoutées sont rangées dans **les listes par type** existantes (fonction `adopt`) : une valeur déjà
présente reçoit seulement son `skos:exactMatch`, sinon elle est créée. Exemples : interprétation d'une unité (US / fait / ensemble / sépulture),
détermination d'un mobilier (par type de mobilier, VAB depuis `determination_VAB`), types de document avec leur catégorie, support et format,
types de traitement, mots-clés d'interprétation du projet.

- **Non repris** : les valeurs de `condition_preservation_prelevement` (le référentiel les décrit en texte libre) et « enduit peint »
  (aucun type de mobilier ne lui correspond).
- **Nature de l'unité** (`SIARU.NATURE`, nouveau code) : les 9 valeurs du référentiel (positive, négative, englobante, US construite, dépôt naturel,
  altération, unité artificielle, interface, anomalie), pour tous les types. « Cycle géomorphologique » (`SIARU.GEOMORPHO`) et « Agent
  géomorphologique » (`SIARU.GEOMORPHOAGENT`) sont des champs propres à Siamois, sans équivalent dans le référentiel, avec leurs 4 valeurs.
- **Alerte sanitaire** (`SIAS.SANITARYALERT`, choix multiple) : devenue une liste. Celle de l'objet / lot pour céramique, métal, lithique, verre,
  faune et terre cuite architecturale ; celle des vestiges anthropobiologiques pour le type VAB.
- **Matière et classe du mobilier** : le libellé de la classe est celui du référentiel, avec la matière entre crochets (« [métal] ferreux »),
  sans altLabel. Le lien avec la matière passe aussi par les concepts associés (`skos:related`).

### Définitions et notes

Un concept qui a un équivalent dans le référentiel national (un `skos:exactMatch`) **reprend sa définition et ses notes** (éditoriale,
d'application) telles quelles, sans les réécrire ; la définition écrite par le générateur (type de donnée, code de champ…) ne sert que
pour les concepts sans équivalent. Dans la dernière génération : 632 concepts concernés. Les notes éditoriales du référentiel sont des
remarques internes (« SIAMOIS 2026 (non reprise du référentiel) », « contacter… ») : elles sont reprises avec le reste.

### Siamois DEMO

Le thésaurus s'appelle « Siamois DEMO » (libellé du schéma). Changements de cette étape :

- **Types d'unité Log et USlog** (INRAP) ajoutés à `SIARU.TYPE`, sans équivalent dans le référentiel, sans définition ni liste propre.
- **Mesures** : `SIAMD.TYPE` est ce qui est mesuré (longueur, largeur, hauteur, épaisseur, diamètre, profondeur, altitude, poids, contenance,
  azimut, pendage) ; `SIAMD.NATURE` est la façon de prendre la valeur (maximale, minimale, moyenne, inférieure, supérieure, sèche), facultative et
  **indépendante du type** (plus de collections par type de mesure). « Altitude moyenne » devient le type Altitude avec la nature Moyenne, « Poids sec »
  le type Poids avec la nature Sèche.

### Sans collections, ids conservés (2026-10-31)

`python3 -I generate_thesaurus_socle.py --th th338 --keep-ids "<export OpenTheso>.rdf" --out ~/Downloads/siamois-demo.rdf`

- **`--keep-ids`** : chaque concept de l'export garde son identifiant (retrouvé par son parent et son libellé, sans accents ni casse) ; un
  concept nouveau reçoit `max(id)+1…`. Le script affiche les identifiants conservés, les nouveaux, et les concepts de l'export qu'il ne
  retrouve pas. Sur l'export du 2026-10-29 : 962 conservés, 0 nouveau, 0 perdu.
- **Collections** : plus aucune (179 retirées, y compris « Dépendance logique » et les deux familles de types de mobilier).
- **Ajouts faits à la main dans OpenTheso, repris par le script** : types d'UE `Entité archéologique` et `ensemble archéologique` (concepts à part,
  sans libellé alternatif sur Fait), interprétations d'UE `Terre végétale` et `Filin` (reliées à US), déterminations de mobilier `os animal` et
  `coquille d'huître` (reliées à Faune), libellé `Site patrimonial`.
- **Corrections par rapport à l'export** : `Géometrie` → `Géométrie` (2 concepts) ; Forme, Profil et Orientation d'érosion ne sont plus reliés au
  cycle `Erosion` (1225) mais à la nature `Négative` (1216), comme la règle de `UE.json`.
- `build_default_project_definition.py` ne lit plus les collections : il met à jour les identifiants de types de `default-project/definition.json`.
