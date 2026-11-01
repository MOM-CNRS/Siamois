# Import Excel : ce qu'il faudrait pour importer Kition-Pervolia comme par l'API

> Note du 2026-11-01. Les deux exports Kition-Pervolia ont été importés **par l'API** (voir [import-api.md](import-api.md)), faute de
> pouvoir passer par le module d'import Excel (`OOXMLImportService`). Cette note liste ce qui lui manque pour obtenir le **même résultat**
> (240 UE, 311 relations, 1555 documents, mêmes champs) à partir d'un classeur. Elle se lit avec
> [import-champs-additionnels.md](../import-champs-additionnels.md), qui conçoit déjà la première des demandes.

## Ce que le module sait faire aujourd'hui

- **Feuilles** : Institution, Personne, Code, Unité d'action, Unité spatiale, **UE**, Mobilier (`Prelev`), Phase, **UE_rel**
  (`parent`, `enfant`), **Strati_Rel** (`us1`, `us2`, `relation`, `direction vocabulaire`, `asynchrone`, `incertain`), Lieu_rel.
- **UE** : identifiant, description, type, nature, interprétation, chronologie, auteur et contributeurs **par e-mail**, dates d'ouverture
  et de fermeture, couleur, texture et composition de la matrice, phases, commentaire, TAQ, TPQ, érosion.
- **Concepts** : une colonne `… uri` (adresse `…?idc=…&idt=…`) ou une colonne `… label`. Le libellé est cherché dans le thésaurus de
  **l'institution**, pour le code de champ de la colonne (`resolveConceptByLabel(institution, code, libellé)`).
- **Hiérarchie multiple** : la feuille `UE_rel` accepte plusieurs parents pour un même enfant.

## Les écarts, du plus bloquant au plus confortable

### Bloquants : sans eux, un classeur Kition ne donne pas le même résultat

| # | Manque | Ce que fait l'import aujourd'hui | Ce qu'il faut |
|---|---|---|---|
| 1 | **Champs ajoutés au type** | rien : aucune colonne ne vise un champ additionnel (la note [import-champs-additionnels.md](../import-champs-additionnels.md) est une conception, non implémentée dans `OOXMLImportService`) | Colonnes `champ:<libellé>` pour les champs **texte, date et mesure**. Kition en a 14 (voir plus bas) ; les mesures doivent être lues dans l'unité du champ (mètres). Les textes dépassent 255 caractères : la colonne `value_as_text` est déjà élargie par la PR du gabarit. |
| 2 | **Documents** | aucune feuille : les documents ne s'importent que par le dépôt de fichiers (`DocumentBulkImportBean`) | Une feuille **Documents** : identifiant, titre, description, type, **URL externe**, **autres identifiants**, UE liées (liste), sans fichier. Un document peut être lié à plusieurs UE. |
| 3 | **Résolution des concepts par la configuration du projet** | cherche le libellé dans le thésaurus de l'**institution** pour le code de champ ; ne connaît ni la branche, ni la collection, ni le thésaurus choisis **pour ce projet et ce type** | Résoudre un libellé ou une adresse dans la **source du champ pour le type de la ligne** (code de champ, branche ou collection, tels que le projet les a réglés). Sinon `Locus` ou `Chypro-Classique` (`th182`) ne sont pas retrouvés par libellé, ou le sont dans le mauvais thésaurus. |
| 4 | **Concepts absents de la base** | `Concept … introuvable` si le concept n'a pas été chargé au préalable | Charger la branche du thésaurus concernée (ce que fait déjà le gabarit à la création du projet) ou charger le concept à la volée. |
| 5 | **Dates** | accepte une cellule au format date ou un texte `aaaa-mm-jj` ; **toute autre valeur donne `null` sans rien dire** (`parseOffsetDateTime`) | Accepter `jj/mm/aaaa` et le numéro de série Excel d'une cellule non formatée ; **signaler** une valeur illisible. Les exports Kition donnent `10/10/2012` et `41200.67…`. |
| 6 | **Auteurs par nom** | l'auteur et les contributeurs sont des **e-mails** | Retrouver une personne par « prénom nom » (les fiches Kition n'ont pas d'e-mail), avec une erreur claire si elle manque. |

### Importants : le résultat serait dégradé sans eux

| # | Manque | Ce qu'il faut |
|---|---|---|
| 7 | **Relations en texte libre** : une cellule dit `sous US S4-11; sur US S4-18; dans locus 16` | Soit une feuille `Strati_Rel` déjà éclatée (une ligne par relation, avec le verbe traduit en concept et en sens), soit une règle de lecture déclarée. Le verbe (`sous`, `sur`, `recoupe`, `va avec`, `=`) devient un concept de relation **et** un sens ; `dans` et `contient` deviennent des lignes `UE_rel`. |
| 8 | **Valeurs « vides » propres au fichier** | `NR` (non renseigné) et `?` doivent donner une cellule vide, pas une erreur ni une valeur : à déclarer par colonne. |
| 9 | **Plusieurs valeurs dans une cellule pour un champ à une valeur** | `CAII-CCI`, `CC et contemporain` : choisir la première, la dernière, ou signaler. Aujourd'hui rien n'existe. |
| 10 | **Une valeur sans concept** | Un compte rendu clair, par valeur : `'démontage' ×2, '?' ×52…`, avec le choix de laisser la cellule vide ou de bloquer. L'import par l'API produit ce compte rendu. |
| 11 | **Identifiants alphanumériques** | l'identifiant complet est gardé tel quel (`S4-03`, `Locus 23`), mais le numéro de l'UE (`identifier`) reste **vide** quand la valeur n'est pas un entier (`parseIntegerSafe`). À vérifier : effet sur la numérotation automatique et sur le compteur du format du type (`US{NUM_UE:000}`, `Locus {NUM_UE:0}`). |
| 12 | **UE créées à partir d'une colonne** | Les sondages et les tombes n'ont pas de ligne dans les exports : ils sont déduits des colonnes `Sondage` et `Tombe`. À fournir comme lignes d'`UE` (voir « Transformations »). |

### Confort

| # | Manque | Ce qu'il faut |
|---|---|---|
| 13 | **Mapping réutilisable** | Une table de correspondance enregistrée (colonne → champ ; valeur d'origine → concept), comme le module d'export a ses modèles, au lieu d'une feuille à construire à la main. La correspondance Kition existe déjà : [kition_mapping_th182.md](../../scripts/kition/kition_mapping_th182.md). |
| 14 | **Rapport d'import** | Le même que celui de l'API : nombres par type, valeurs sans concept, références non résolues, erreurs par ligne. |
| 15 | **Création du projet avec le gabarit** | L'import cible un projet existant (portée `PROJECT`) : la procédure doit dire de le créer d'abord avec le gabarit « Kition-Pervolia », puisque les champs et les types viennent de lui. |

## Les 14 champs ajoutés de Kition

| Champ | Type | US | Locus |
|---|---|:-:|:-:|
| Interprétation (texte libre) | texte | ✓ | ✓ |
| Homogénéité | texte | ✓ | |
| Fiabilité | texte | ✓ | |
| Commentaire de datation | texte | ✓ | |
| Types de matériel | texte (jusqu'à ~7000 caractères) | ✓ | ✓ |
| Inventaire du matériel | texte (jusqu'à ~3700 caractères) | ✓ | ✓ |
| Renvois au cahier de fouille | texte | ✓ | ✓ |
| Auteurs de la fiche | texte | ✓ | ✓ |
| Date de création de la fiche | date | ✓ | ✓ |
| Date de modification de la fiche | date | ✓ | ✓ |
| Z inférieur 1, Z inférieur 2, Z supérieur 1, Z supérieur 2 | mesure, mètres | ✓ | ✓ |

## Classeur cible

Ce que le module devrait lire pour reproduire l'import (colonnes en plus de celles qu'il connaît déjà).

| Feuille | Colonnes utiles pour Kition |
|---|---|
| **UE** | `identifiant`, `type uri` (`th182`), `chronologie uri`, `interpretation uri` (nature du locus ou interprétation de l'US), `nature uri` (`th338`), `date d'ouverture`, `date de fermeture`, `description`, `couleur de la matrice`, `author`, `contributeurs`, `champ:Homogénéité`, `champ:Fiabilité`, `champ:Types de matériel`, `champ:Z inférieur 1`… |
| **UE_rel** | `parent`, `enfant` : une ligne par lien, y compris les deux sondages d'une US « S4 et S5 » |
| **Strati_Rel** | `us1`, `us2`, `relation` (concept), `direction vocabulaire` : une ligne par relation |
| **Documents** (nouvelle) | `identifiant`, `titre`, `description`, `type uri`, `url externe`, `autres identifiants`, `ue liées` |

## Transformations qui peuvent rester hors du module

Ce sont des règles propres à ces deux fichiers. Un petit script de préparation (comme `scripts/filemaker_to_siamois.py`) produit le classeur
cible : lire les libellés CIDOC des colonnes, lire la grammaire des cellules de relation (`sous|sur|recoupe|va avec|dans|contient`), déduire
les UE sondage et tombe, repérer les codes de document (`KIT12-NIMFO-0087`) et en tirer le type, et aller chercher les métadonnées des
documents sur le site d'origine. Le module n'a pas à les connaître : il lui faut seulement de pouvoir lire un classeur qui les a déjà résolues.

## Ordre conseillé

1. **Champs ajoutés** (#1), avec les dates (#5) : c'est ce qui couvre le plus de données.
2. **Résolution par la configuration du projet** (#3, #4) : sans elle, aucun type ni aucune période ne sont retrouvés par libellé.
3. **Feuille Documents** (#2).
4. **Auteurs par nom** (#6), puis les valeurs vides et multiples (#8, #9) et le compte rendu (#10, #14).
5. Le reste (#7, #11, #12, #13, #15) selon l'usage.

Critère de réussite : importer les deux exports Kition (via le script de préparation) dans un projet créé avec le gabarit donne
les mêmes nombres que [rapport-import-siamois2.md](rapport-import-siamois2.md), sans erreur.
