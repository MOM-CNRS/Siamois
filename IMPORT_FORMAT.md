# Format d'import dans un projet

Le module d'import de données dans un projet accepte un fichier OOXML (`.xlsx`) multi-feuille. Il est possible de spécifier les correspondances entre les feuilles et les tables SIAMOIS, ainsi que les colonnes et les champs, via une feuille de métadonnées (`_meta`). Si cette feuille est absente, les feuilles et colonnes doivent être nommées selon les noms standards indiqués ci-dessous.

Une future mise à jour permettra l'import de plusieurs fichiers à la fois, avec des formats différents (CSV, etc.).

## Portée de l'import (scope)

- **Import global** : les feuilles Institution, Personne, Code et Unité d'action sont importées, en plus des feuilles de contenu.
- **Import dans un projet** : ces quatre feuilles sont ignorées. Les colonnes `institution` et `unité d'action` des autres feuilles sont alors ignorées et remplacées automatiquement par le projet cible de l'import.

---

# Format des feuilles

## _meta

Feuille de métadonnées (optionnelle) permettant de spécifier :

- des correspondances entre les feuilles du tableur et les tables de Siamois (mapping sheet → table) ;
- des correspondances entre les en-têtes de colonnes du tableur et les champs canoniques de Siamois (alias de colonnes).

Si la feuille `_meta` est absente, l'import se base sur les noms de feuilles par défaut (section suivante) et les en-têtes de colonnes doivent porter directement les noms canoniques.

| Nom | Description | Obligatoire |
|---|---|---|
| `sheet_id` | Identifiant technique de la table cible (voir colonne « Identifiant technique » du tableau ci-dessous) | Oui |
| `sheet_name` | Nom de la feuille du tableur à associer à cette table | Oui |
| `column_alias` | Nom d'en-tête non standard présent dans la feuille | Non |
| `column_canonical` | Nom canonique du champ Siamois vers lequel `column_alias` doit être traduit | Non |

**Notes :**
- Une même table peut être alimentée par plusieurs feuilles (plusieurs lignes avec le même `sheet_id`).
- `column_alias`/`column_canonical` ne sont pris en compte que si `sheet_id` et `sheet_name` sont renseignés sur la même ligne.
- Les noms de colonnes (canoniques ou alias) sont normalisés avant comparaison : mise en minuscule, suppression des accents, espaces multiples réduits à un seul.
- En complément des alias explicites, toute colonne de la feuille dont le nom normalisé correspond déjà à un nom canonique attendu est reconnue automatiquement, sans qu'il soit nécessaire de la déclarer dans `_meta`.

**Exemple :**

| sheet_id | sheet_name | column_alias | column_canonical |
|---|---|---|---|
| person | Personnes | Courriel | email |
| person | Personnes | Login | identifiant |
| spatial_unit | Lieux | | |

## Identifiants techniques et noms de feuilles par défaut

| Identifiant technique | Feuille par défaut | Libellé | Importée en scope Projet |
|---|---|---|---|
| `institution` | Institution | Institution | Non |
| `person` | Personne | Personne | Non |
| `code` | Code | Code | Non |
| `action_unit` | Unite action | Unité d'action | Non |
| `spatial_unit` | Unité spatiale | Lieu | Oui |
| `recording_unit` | UE | UE | Oui |
| `specimen` | Prelev | Mobilier | Oui |
| `phase` | Phase | Phase | Oui |
| `recordingRel` | UE_rel | Relations UE | Oui |
| `stratiRel` | Strati_Rel | Stratigraphie | Oui |
| `spatialUnitRel` | Lieu_rel | Relations Lieu | Oui |

## Feuilles

### Institution

Feuille par défaut : **Institution**. Ligne ignorée si `nom` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| nom | Nom de l'institution | Oui |
| description | Description libre | Non |
| identifiant | Identifiant unique de l'institution | Non |
| email admins | Emails des administrateurs, séparés par `;` ou `,` | Non |
| thesaurus | URI du thésaurus, au format `https://.../api?idt=<vocab>&idc=<concept>` ; le paramètre `idt` fournit l'identifiant externe de vocabulaire, le domaine avant le `?` fournit l'instance de thésaurus | Non |

### Personne

Feuille par défaut : **Personne**. Ligne ignorée si `email` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| email | Adresse email de la personne, sert de clé d'identification (recherche insensible à la casse) | Oui |
| nom | Nom de famille | Non |
| prenom | Prénom | Non |
| identifiant | Identifiant de connexion | Non |

### Code

Feuille par défaut : **Code**. Feuille ignorée entièrement si l'une des deux colonnes est absente de l'en-tête ; ligne ignorée si `code` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| code | Code de l'action (ex. « FOU » pour Fouille) | Oui |
| type uri | URI du concept de type, au format `...?idt=<vocab>&idc=<concept>` | Oui |

### Unité d'action

Feuille par défaut : **Unite action**. Au moins un des champs `nom` ou `identifiant` doit être renseigné pour que la ligne soit importée (si `identifiant` est vide, `nom` sert d'identifiant complet).

| Nom | Description | Obligatoire |
|---|---|---|
| nom | Nom de l'unité d'action | Oui (nom ou identifiant) |
| identifiant | Identifiant unique de l'unité d'action | Oui (nom ou identifiant) |
| code | Code d'action associé (référence à la feuille Code) | Non |
| type uri | URI du concept de type | Non |
| createur | Email de la personne créatrice | Non |
| institution | Identifiant de l'institution rattachée | Non |
| contexte spatiale | Noms des unités spatiales formant le contexte spatial, séparés par `&&` | Non |
| date debut | Date de début (ISO `AAAA-MM-JJ` ou date Excel) | Non |
| date fin | Date de fin | Non |
| localisation principale | Nom de l'unité spatiale principale associée | Non |

### Unité spatiale (Lieu)

Feuille par défaut : **Unité spatiale**. Ligne ignorée si `nom` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| nom | Nom du lieu / de l'unité spatiale | Oui |
| uri type | URI du concept de type de lieu | Non |
| type label | Libellé du type, utilisé si `uri type` est vide ; résolution par libellé possible uniquement dans le contexte d'un projet (institution connue) | Non |
| institution | Identifiant de l'institution (ignoré, remplacé par celle du projet en import Projet) | Non |
| enfants | Noms des unités spatiales enfants, séparés par `&&` ; résolus par rapprochement de nom après persistance des unités spatiales (comme pour la feuille Relations Lieu ci-dessous). Un nom introuvable produit désormais une erreur de validation (au lieu d'être ignoré silencieusement) | Non |

> Les relations parent/enfant peuvent être renseignées via la colonne `enfants` ci-dessus **et/ou** via la feuille dédiée Relations Lieu ; les deux sources alimentent la même étape de import et peuvent être combinées librement.

### Relations Lieu

Feuille par défaut : **Lieu_rel**. Toute la feuille est ignorée si l'une des deux colonnes est absente de l'en-tête ; une ligne est ignorée si `parent` ou `enfant` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| parent | Nom de l'unité spatiale parente | Oui |
| enfant | Nom de l'unité spatiale enfant | Oui |

### Unité d'enregistrement (UE)

Feuille par défaut : **UE**. Au moins un des champs `identifiant` ou `description` doit être renseigné pour que la ligne soit importée.

| Nom | Description | Obligatoire |
|---|---|---|
| identifiant | Identifiant de l'UE (numérique ou alphanumérique) | Oui (identifiant ou description) |
| description | Description de l'UE | Oui (identifiant ou description) |
| type uri | URI du concept de type d'UE | Non |
| type label | Libellé du type, utilisé si `type uri` est vide (résolution par libellé nécessite un contexte projet) | Non |
| cycle uri | URI du concept de cycle géomorphologique | Non |
| cycle label | Libellé du cycle géomorphologique (repli) | Non |
| agent uri | URI du concept d'agent géomorphologique | Non |
| agent label | Libellé de l'agent géomorphologique (repli) | Non |
| interpretation uri | URI du concept d'interprétation | Non |
| interpretation label | Libellé d'interprétation (repli) | Non |
| author email | Email de l'auteur de l'enregistrement | Non |
| institution | Identifiant de l'institution (ignoré, remplacé par celle du projet en import Projet) | Non |
| contributeurs email | Emails des contributeurs/fouilleurs, séparés par `;` ou `,` | Non |
| date d'ouverture | Date d'ouverture de l'UE | Non |
| date de fermeture | Date de fermeture de l'UE | Non |
| unite spatiale | Nom de l'unité spatiale de rattachement | Non |
| unite d'action | Identifiant de l'unité d'action de rattachement (ignoré, remplacé par le projet courant en import Projet) | Non |
| couleur de la matrice | Couleur de la matrice sédimentaire | Non |
| texture de la matrice | Texture de la matrice sédimentaire | Non |
| composition de la matrice | Composition de la matrice sédimentaire | Non |
| phases | Identifiants des phases associées, séparés par `;` ou `,` | Non |

### Mobilier (Prélèvement)

Feuille par défaut : **Prelev**. Ligne ignorée si `identifiant` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| identifiant | Identifiant unique du mobilier | Oui |
| institution | Identifiant de l'institution (ignoré, remplacé par celle du projet en import Projet) | Non |
| auteur fiche email | Email de l'auteur de la fiche | Non |
| matiere | URI du concept de matière | Non |
| categorie | URI du concept de catégorie (lot, objet, échantillon…) | Non |
| designation | URI du concept de désignation / interprétation | Non |
| collecteurs emails | Emails des collecteurs, séparés par `;` ou `,` | Non |
| unite d'enregistrement | Identifiant de l'UE de rattachement | Non |

> Contrairement aux feuilles UE et Lieu, `matiere`, `categorie` et `designation` n'acceptent aujourd'hui que des URI de concept : il n'existe pas de colonne « libellé » de repli pour cette feuille.

### Phase

Feuille par défaut : **Phase**. Ligne ignorée si `identifiant` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| identifiant | Identifiant de la phase, unique au sein de son unité d'action | Oui |
| titre | Titre / nom de la phase | Non |
| type uri | URI du concept de type de phase | Non |
| description | Description textuelle (5000 caractères max.) | Non |
| ordre | Ordre d'affichage / chronologique | Non |
| borne inferieure | Borne chronologique inférieure | Non |
| borne superieure | Borne chronologique supérieure | Non |
| auteur | Email de l'auteur | Non |
| projet | Identifiant de l'unité d'action de rattachement (ignoré, remplacé par le projet courant en import Projet) | Non |
| institution | Identifiant de l'institution de l'unité d'action (ignoré en import Projet) | Non |

> Comme pour le Mobilier, `type uri` n'accepte qu'une URI de concept — pas de colonne « libellé » de repli.

### Relations UE

Feuille par défaut : **UE_rel**. Toute la feuille est ignorée si l'une des deux colonnes est absente de l'en-tête ; une ligne est ignorée si `parent` ou `enfant` est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| parent | Identifiant de l'UE parente | Oui |
| enfant | Identifiant de l'UE enfant | Oui |

### Stratigraphie

Feuille par défaut : **Strati_Rel**. Toute la feuille est ignorée si `us1` ou `us2` est absent de l'en-tête ; une ligne est ignorée si l'une des deux valeurs est vide.

| Nom | Description | Obligatoire |
|---|---|---|
| us1 | Identifiant de la première UE | Oui |
| us2 | Identifiant de la deuxième UE | Oui |
| relation | URI du concept de relation stratigraphique | Non |
| relation label | Libellé de la relation, utilisé si `relation` est vide (résolution par libellé nécessite un contexte projet) | Non |
| direction vocabulaire | Sens de lecture de la relation par rapport au vocabulaire (`True` = oui, toute autre valeur = non) | Non |
| asynchrone | Relation asynchrone (`True`/autre valeur) | Non |
| incertain | Relation incertaine (`True`/autre valeur) | Non |

---

## Notes techniques complémentaires

- **Normalisation des en-têtes** : les noms de colonnes sont mis en minuscule, débarrassés de leurs accents et de leurs espaces multiples avant d'être comparés aux noms canoniques ou aux alias `_meta`.
- **Listes** : les colonnes multi-valeurs (emails, phases, etc.) acceptent `;` ou `,` comme séparateur. Les colonnes de type « lien vers plusieurs unités spatiales » (`contexte spatiale`, `enfants`) utilisent `&&` comme séparateur.
- **Dates** : format ISO `AAAA-MM-JJ`, ou date native Excel.
- **URI de concept** : format `https://<domaine>/api?idt=<id_vocabulaire>&idc=<id_concept>[&autres_paramètres]`.
- **Résolution par libellé** : disponible uniquement pour les feuilles Lieu, UE et Stratigraphie, et uniquement lors d'un import dans le contexte d'un projet (l'institution du projet fournit le thésaurus de référence). Pour Mobilier et Phase, seule l'URI est acceptée.
- **Gestion des erreurs** : les erreurs sont collectées par ligne/colonne (`[colonne 'X'] : message`) sans interrompre l'import ; au-delà de 200 erreurs, les suivantes ne sont plus conservées.

## Références code

- `src/main/java/fr/siamois/infrastructure/dataimport/ImportSchema.java`
- `src/main/java/fr/siamois/infrastructure/dataimport/OOXMLImportService.java`
- `src/main/java/fr/siamois/infrastructure/dataimport/ExcelCellHelper.java`
