# Importer des données dans un projet

SIAMOIS importe un fichier Excel (`.xlsx`) de plusieurs feuilles dans un projet existant : lieux, unités d'enregistrement (UE), mobilier, phases et leurs relations. Un modèle prêt à remplir est téléchargeable depuis l'écran d'import.

Le principe est simple : **chaque feuille et chaque colonne porte le nom de la table ou du champ correspondant dans SIAMOIS**. Si vous gardez ces noms, il n'y a rien à configurer. Si votre fichier utilise d'autres noms, la feuille facultative `_meta` explique à SIAMOIS comment les relier.

---

## Ce que l'import fait (et ne fait pas) dans un projet

- Les feuilles **Institution**, **Personne**, **Code** et **Unité d'action** ne sont pas lues dans un import de projet, même si elles sont dans le fichier.
- Les colonnes **Institution** et **Projet** des autres feuilles sont ignorées : SIAMOIS y met toujours le projet dans lequel vous importez.
- Les **valeurs de liste** (types, natures, matières, périodes…) s'écrivent **avec leur libellé**, tel qu'on le voit dans SIAMOIS. Il n'y a plus de colonnes d'adresse (URI) à remplir. Le libellé est cherché dans le thésaurus configuré pour ce champ dans le projet. La casse n'a pas d'importance, mais les accents oui. Un libellé alternatif du thésaurus est accepté comme le libellé principal.
- Si un libellé n'est pas trouvé, l'import l'indique avec deux liens : un vers la configuration du champ dans le projet, un vers le thésaurus.
- Au plus 200 erreurs sont détaillées pour un même fichier.

---

## Les feuilles

Les noms ci-dessous sont ceux que SIAMOIS reconnaît tout seul. Les majuscules et les accents n'ont pas d'importance dans les noms de colonnes.

| Feuille | Contenu | Obligatoire pour importer cette table |
|---|---|---|
| **Lieu** | Les lieux (communes, parcelles, lieux-dits…) | Intitulé |
| **Relations entre lieux** | Quel lieu contient quel lieu | Parent et Enfant |
| **Unité d'enregistrement** | Les UE | Identifiant ou Description |
| **Relations entre UE** | Quelle UE contient quelle UE | Parent et Enfant |
| **Stratigraphie** | Les relations stratigraphiques entre UE | Unité 1 et Unité 2 |
| **Mobilier** | Le mobilier (objets, lots, prélèvements) | Identifiant |
| **Phase** | Les phases | Identifiant |

Une feuille absente est simplement ignorée. Les anciens noms (`Unité spatiale`, `UE`, `Prelev`, `UE_rel`, `Strati_Rel`, `Lieu_rel`) fonctionnent toujours.

### Lieu

| Colonne | Ce qu'on y met | Obligatoire |
|---|---|---|
| Intitulé | Le nom du lieu | Oui |
| Type | Le type de lieu, par son libellé (Commune, Parcelle, Lieu-dit…) | Oui |
| Enfants | Les noms des lieux contenus, séparés par `&&` | Non |
| Institution | Ignorée dans un projet | Non |

Tous les lieux de cette feuille sont rattachés au projet, dans sa **Localisation précise**.

### Relations entre lieux et Relations entre UE

| Colonne | Ce qu'on y met | Obligatoire |
|---|---|---|
| Parent | Le lieu (ou l'UE) qui contient | Oui |
| Enfant | Le lieu (ou l'UE) contenu | Oui |

Si l'une des deux colonnes manque, la feuille entière est ignorée. Une ligne dont l'une des cases est vide est ignorée.

### Unité d'enregistrement

Une ligne est importée si **Identifiant** ou **Description** est renseigné.

| Colonne | Ce qu'on y met |
|---|---|
| Identifiant | Numéro ou code de l'UE |
| Type | Type d'UE (libellé) — obligatoire |
| Description | Description libre |
| Nature | Nature de l'UE (libellé) |
| Cycle géomorphologique | Libellé |
| Agent géomorphologique | Libellé |
| Interprétation normalisée | Libellé |
| Forme de l'érosion, Orientation de l'érosion, Profil de l'érosion | Libellés |
| Attribution chronologique | Libellé |
| Auteur scientifique/technique | Adresse email de l'auteur |
| Contributeur(s) scientifique(s)/technique(s) | Adresses email, séparées par `;` ou `,` |
| Date d'ouverture, Date de fermeture | Dates (`AAAA-MM-JJ` ou date Excel) |
| Lieu(x) | Intitulé du lieu de rattachement |
| Phases | Identifiants des phases, séparés par `;` ou `,` |
| Couleur de la matrice, Texture de la matrice, Composition de la matrice | Texte libre |
| Informations complémentaires | Commentaire libre |
| TAQ, TPQ | Terminus ante quem, terminus post quem |
| Institution, Projet | Ignorées dans un projet |

### Stratigraphie

| Colonne | Ce qu'on y met | Obligatoire |
|---|---|---|
| Unité 1 | Identifiant de la première UE | Oui |
| Unité 2 | Identifiant de la seconde UE | Oui |
| Type de relation | Libellé du type de relation, si on veut en donner un | Non |
| Asynchrone | `True` si la relation est asynchrone | Oui |
| Incertain | `True` si la relation est incertaine | Non |

Pour une relation asynchrone, **l'unité 1 est la plus ancienne** : l'unité 2 lui est postérieure. Sans type de relation, SIAMOIS range la relation selon cet ordre.

### Mobilier

Une ligne est importée si **Identifiant** est renseigné.

| Colonne | Ce qu'on y met |
|---|---|
| Identifiant | Identifiant unique du mobilier — obligatoire |
| Type | Type de mobilier (libellé) — obligatoire |
| Matière | Libellé |
| Classe | Libellé de la classe de matière |
| Détermination | Libellé |
| Attribution chronologique | Libellé |
| État sanitaire | Libellé |
| Nature du prélèvement | Libellé. Cette colonne est reconnue mais **n'est pas encore importée** |
| Auteurs | Adresse email de l'auteur de la fiche |
| Collecteurs | Adresses email, séparées par `;` ou `,` |
| Unité d'enregistrement | Identifiant de l'UE de rattachement |
| Description, Informations complémentaires | Texte libre |
| Identifiant d'isolation | Numéro d'isolat |
| Autre identifiant | Identifiant secondaire |
| Nombre d'éléments | Nombre d'éléments du lot |
| Date de collecte | Date (`AAAA-MM-JJ` ou date Excel) |
| TAQ, TPQ | Terminus ante quem, terminus post quem |
| Institution | Ignorée dans un projet |

### Phase

Une ligne est importée si **Identifiant** est renseigné.

| Colonne | Ce qu'on y met |
|---|---|
| Identifiant | Identifiant de la phase, unique dans le projet — obligatoire |
| Type | Type de phase (libellé) — obligatoire |
| Titre | Nom de la phase |
| Description | Texte libre (5000 caractères au plus) |
| Numéro d'ordre | Ordre d'affichage |
| Borne inférieure, Borne supérieure | Bornes chronologiques |
| Auteur | Adresse email de l'auteur |
| Périodes | Libellés, séparés par `;` ou `,` |
| Mots-clés | Libellés, séparés par `;` ou `,` |
| Projet, Institution | Ignorées dans un projet |

---

## La feuille `_meta` (facultative)

Elle sert à **dire à SIAMOIS comment lire un fichier dont les noms sont différents**. Le modèle téléchargeable en contient une, **déjà remplie avec toutes les correspondances, y compris celles qui sont exactes** : copiez-la et adaptez-la.

| Colonne | Ce qu'on y met |
|---|---|
| **Table dans SIAMOIS** | La table concernée : Lieu, Unité d'enregistrement, Mobilier, Phase, Relations entre lieux, Relations entre UE ou Stratigraphie |
| **Nom de la feuille dans votre fichier** | Le nom de la feuille telle qu'elle s'appelle dans votre classeur |
| **Colonne dans votre fichier** | L'en-tête de colonne tel que vous l'avez écrit. Laisser vide pour déclarer seulement la feuille |
| **Champ dans SIAMOIS** | Le champ de SIAMOIS que cette colonne alimente (un nom de la liste ci-dessus) |

Quelques règles :

- Une table peut être alimentée par **plusieurs feuilles** : écrivez une ligne par feuille.
- Une correspondance de colonne n'est prise en compte que si la table **et** le nom de la feuille sont remplis sur la même ligne.
- Les colonnes dont le nom est déjà celui de SIAMOIS sont reconnues toutes seules : inutile de les lister.
- Vous pouvez mélanger : une feuille déclarée dans `_meta` et d'autres reconnues par leur nom habituel.
- Les anciens titres de colonnes (`sheet_id`, `sheet_name`, `column_alias`, `column_canonical`) sont toujours lus.

**Exemple** : votre fichier a une feuille « Contextes » avec une colonne « Couche » qui contient l'identifiant des UE.

| Table dans SIAMOIS | Nom de la feuille dans votre fichier | Colonne dans votre fichier | Champ dans SIAMOIS |
|---|---|---|---|
| Unité d'enregistrement | Contextes | | |
| Unité d'enregistrement | Contextes | Couche | Identifiant |

---

## Bon à savoir

- **Séparateurs.** Les listes de personnes, de phases, de périodes et de mots-clés se séparent par `;` ou `,`. Les listes de lieux (colonne Enfants) se séparent par `&&`.
- **Dates.** `AAAA-MM-JJ` ou une vraie date Excel.
- **Anciens fichiers.** Un fichier qui utilise encore les colonnes d'adresse (`type uri`, `nature uri`…) est lu comme avant. Ces colonnes ne sont plus dans le modèle, et la résolution par libellé est la voie à privilégier.
