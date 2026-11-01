# Kition-Pervolia : import par l'API

> Rapport du 2026-11-01. Migration unique des deux exports Kition-Pervolia (CIDOC) vers Siamois, par l'API REST, dans un projet
> créé avec le gabarit « Kition-Pervolia ». Le rapport brut de l'import sur `siamois2.mom.fr` est dans
> [rapport-import-siamois2.md](rapport-import-siamois2.md). Ce que le module d'import Excel devrait savoir faire pour importer les
> mêmes fichiers est dans [import-excel-ecarts.md](import-excel-ecarts.md).

## Sources

| Source | Contenu |
|---|---|
| `US_170207_(1).xlsx` | 153 US, 32 colonnes (modèle CIDOC : le libellé de chaque colonne porte la classe, par exemple `E42_Identifier`) |
| `Locus_170207_(1).xlsx` | 70 loci, 23 colonnes |
| API du site d'origine (`apichypre.mom.fr/KP`, lecture seule) | titre, description, mots-clés, auteur, URL pérenne Nakala et identifiant ARK de 1545 documents |
| Thésaurus Kition (`th182`, OpenTheso) | types, chronologie, nature du locus, interprétation d'une US, types de document |
| Thésaurus socle commun (`th338`) | nature de l'US (positive, négative…) |

## Ce qui est créé dans Siamois

Un projet **Kition-Pervolia**, créé avec le gabarit `kition` (`POST /projects`, `templateId: "kition"`). Le gabarit configure :

- les types d'UE (Sondage, Tombe, Locus, Unité stratigraphique) et de document (Cahier de fouille, Relevé, Photographie de fouille,
  Photographie d'objet), lus dans `th182` ;
- les listes de l'attribution chronologique (branche *Chronologie*), de la nature du locus (*Typologie des locus*) et de l'interprétation
  d'une US (sous-concepts de *Unité stratigraphique*) ;
- les champs ajoutés aux types : voir « Champs ajoutés » ci-dessous.

| Objet source | Objet Siamois |
|---|---|
| Sondage (`S2` à `S5`, colonnes `F` et `K`) | UE de type *Sondage*, parent des US et des loci du sondage |
| Tombe (colonne `B` des US) | UE de type *Tombe*, parent des US de la tombe |
| Locus | UE de type *Locus*, identifiant « Locus N » |
| US | UE de type *Unité stratigraphique*, identifiant d'origine (`S4-03`) |
| « S4 et S5 » (une US dans deux sondages) | une UE avec **deux parents** (hiérarchie multiple) |
| `dans locus 16`, `contient …` | hiérarchie des UE (parents) |
| `sous`, `sur`, `recoupe`, `va avec`, `=` | relations stratigraphiques (postérieur, antérieur, synchrone) |
| Codes `KIT12-NIMFO-0087`, `…-NIMAR-…`, `…-NIMRD-…` | documents sans fichier, liés à leurs UE ; type déduit du code |

### Où va chaque colonne

| Donnée | Champ Siamois |
|---|---|
| Description du locus, sédiment, composition | Description |
| Date d'ouverture, de fermeture (US) ; date de création du locus | Date d'ouverture, Date de fermeture |
| Datation | **Attribution chronologique** (concept de `th182`) ; la chaîne d'origine reste dans la description (« Datation (source) ») |
| Nature du locus | **Interprétation normalisée** (concept de `th182`) et « Interprétation (texte libre) » |
| Interprétation d'une US | **Interprétation normalisée** (concept) quand `th182` en a un, et « Interprétation (texte libre) » toujours |
| US positive ou négative | Nature (concept de `th338`) |
| Couleur | Couleur de la matrice (texte) |
| Homogénéité, fiabilité | champs texte « Homogénéité », « Fiabilité » |
| Commentaire de datation | champ texte « Commentaire de datation » (US) |
| Types de matériel, inventaire du matériel | champs texte « Types de matériel », « Inventaire du matériel » |
| Renvois aux pages du cahier de fouille | champ texte « Renvois au cahier de fouille » |
| Auteurs de la fiche | **Auteur scientifique/technique** (le premier), **Contributeurs** (tous) ; texte « Auteurs de la fiche » |
| Date de réalisation et de modification de la fiche | champs date « Date de création de la fiche », « Date de modification de la fiche » |
| Z inférieur 1 et 2, Z supérieur 1 et 2 | quatre champs de mesure, en mètres (les champs d'altitude du socle commun ne sont pas utilisés) |
| Emprise, localisation précise, observations, commentaires | description (« Emprise : … ») |
| Titre, description d'un document | titre et description ; les mots-clés et l'auteur s'ajoutent à la description |
| URL pérenne Nakala, identifiant ARK d'un document | URL externe, autres identifiants |

### Champs ajoutés par le gabarit

Texte : *Interprétation (texte libre)* (US et Locus), *Homogénéité*, *Fiabilité*, *Commentaire de datation* (US), *Types de matériel*,
*Inventaire du matériel*, *Renvois au cahier de fouille*, *Auteurs de la fiche* (US et Locus). Date : *Date de création de la fiche*,
*Date de modification de la fiche*. Mesure en mètres : *Z inférieur 1*, *Z inférieur 2*, *Z supérieur 1*, *Z supérieur 2*.
Les textes de matériel atteignent près de 7000 caractères : la réponse d'un champ texte n'est plus limitée à 255 caractères
(`value_as_text` devient `text`, migration Liquibase `2026-11-01-1`).

## Résultat de l'import sur `siamois2`

| | |
|---|---|
| UE | **240** : 153 US, 70 loci, 11 tombes, 6 sondages |
| Relations stratigraphiques | **311** : 202 « sous », 57 « sur », 26 « = », 20 « va avec », 6 « recoupe » |
| Liens de hiérarchie (`dans`, `contient`) | 156 |
| Documents | **1555** : 779 photos de terrain, 699 photos d'objet, 71 relevés, 6 carnets |
| Documents avec titre, description, URL Nakala | 1397 ; 158 sans métadonnées (code inconnu de l'API du site d'origine) |
| Erreurs | **0** |
| Appels à l'API | 2352 `POST`, 2259 `PUT`, 14 `GET` |

## Ce qui n'est pas repris, ou repris approximativement

| Point | Effet |
|---|---|
| Mobilier : 315 objets du site d'origine | **non repris** ; l'API d'origine ne donne qu'une description libre, un numéro KEF et un lien vers l'US ou le locus. Les colonnes « Inventaire du matériel » et « Types de matériel » des Excel restent en texte. |
| Datation `?` | 52 fiches sans période (champ vide) |
| Plages et périodes multiples (`CAII-CCI`, `CCII-hell.`, `CC et contemporain`…) | environ 20 fiches ; l'attribution chronologique n'accepte qu'**une** valeur : la première (la plus ancienne) est retenue, la chaîne d'origine reste dans la description |
| Interprétation d'une US sans concept dans `th182` | 14 US (« formation naturelle ou comblement anthropique ? », démontage, rectification de coupe, prélèvement) : seulement dans « Interprétation (texte libre) » |
| Nature de locus sans concept | « Fossé » (1) et « Ligne d'arrachement de la pelle mécanique en 1958 » (1) : seulement en texte |
| Cotes `NR` (non renseigné) | laissées vides (26 cellules, côté locus) |
| 3 références de relation non résolues | `Locus 12 : sous US S4-06`, `S4-19 : sous US S4-06`, `S2-03 : sur locus 4` : l'UE visée n'existe pas dans les exports |
| Auteurs | des personnes Siamois, retrouvées par prénom et nom (Anna Cannavò, Nathalia Denninger, Sabine Fourrier) : comptes désactivés, à e-mail factice (`@kition-pervolia.invalid`), créés au préalable (`scripts/kition/auteurs_kition.sql`) |
| Sondages, tombes | pas de fiche source : l'auteur est le compte qui a fait l'import |
| Sondage « Sg » | une US en porte la valeur ; il est créé comme sondage |
| Retours à la ligne des textes longs | conservés dans la donnée, mais un champ texte supplémentaire s'affiche sur une seule ligne |
| Attribution chronologique dans les listes | l'API renvoie `null` pour ce champ dans les listes (alors que la fiche détaillée le donne) : défaut à corriger |

## Rejouer l'import

Les scripts sont dans `scripts/kition/` (Python 3, bibliothèque standard seulement).

1. **Serveur** : version qui contient le gabarit « Kition-Pervolia » ; `th338` et `th182` joignables (`thesaurus.mom.fr`). Régler le délai
   de lecture du proxy à 120 s ou plus : la création du projet charge des branches de `th182`.
2. **Auteurs** : jouer `auteurs_kition.sql` sur la base (il crée les trois comptes désactivés et les rattache à l'organisation, par son nom).
3. **Import** (crée le projet, importe les données, puis enrichit les documents) :

   ```bash
   SIAMOIS_URL=https://serveur/siamois/api/v1 SIAMOIS_EMAIL=… \
   ./scripts/kition/import_kition.sh US.xlsx Locus.xlsx "Chypre" "Fouille programmée"
   ```

   Le mot de passe est demandé sans écho. L'organisation et le type de projet se donnent par leur nom ou leur identifiant.
4. **Plusieurs fois** : l'import crée toujours un projet. Un projet du même nom dans l'organisation fait refuser la création (409) ; changer le
   nom avec `KITION_PROJECT_NAME`. L'import s'arrête avant d'écrire si l'organisation, le type de projet ou un auteur est introuvable.

La correspondance valeur d'origine → concept `th182` (datations, natures de locus, interprétations, types de document) est
générée par `kition_build_mapping.py` dans `kition_mapping_th182.json`, relue dans [kition_mapping_th182.md](../../scripts/kition/kition_mapping_th182.md).

## Changements du produit apportés avec ce chantier

- Le gabarit de projet « Kition-Pervolia » (`kition-project/definition.json`, `KitionProjectTemplateApplier`).
- La réponse d'un champ texte n'est plus limitée à 255 caractères.
- L'import d'une branche de thésaurus ne casse plus quand un concept a deux synonymes qui ne diffèrent que par la casse (`CHA` et `ChA`
  dans `th182`).
