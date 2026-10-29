# Import Chartres : passage au th337 et aux libellés

Fichier : `src/main/resources/datasets/Import_Chartres_Projet.xlsx` (exemple téléchargeable depuis l'import de projet).

## Ce qui a changé dans le classeur

- **Plus aucune colonne URI** (`… URI`, `URI Type`, et la colonne `Relation` de `Strati_Rel` qui était une URI) : chaque valeur de vocabulaire
  est donnée par son **libellé**, résolu par `ConceptService#resolveConceptByLabel` dans le thésaurus configuré du champ pour le projet
  (libellé préféré ou alternatif, sans tenir compte de la casse, avec les accents).
- Les colonnes qui n'avaient qu'une URI (Mobilier, Phase) ont maintenant leur libellé.
- La colonne « Cycle » devient **« Nature Label »**, branchée sur la nature d'une UE (`SIARU.NATURE`, attribut `RecordingUnit.nature`).
  Les colonnes `nature uri` / `nature label` sont lues par `OOXMLImportService` ; les colonnes cycle et agent restent lues.

## Types d'UE (feuille UE)

| Avant | Après | Résolution dans th337 |
|---|---|---|
| Unité élémentaire | US | US (1199) |
| Subdivision | Tranchée | Tranchée (1205) |
| EA | Entité archéologique | libellé **alternatif** de Fait (1200) : **à ajouter dans OpenTheso** (déjà dans `generate_thesaurus_socle.py`) |
| EAS | Entité archéologique structurée | libellé alternatif d'Ensemble (1201), déjà présent |

## Autres valeurs remplacées par une équivalence (validé)

| Feuille · colonne | Avant (th252) | Après (th337) |
|---|---|---|
| UE · Nature | Dépôt | Dépôt naturel |
| UE · Nature | Erosion | Négative (valeur qui active les champs d'érosion) |
| UE · Interprétation | « Interprétation normalisée » (8 lignes) | vidé : c'était le concept du champ lui-même ; ces UE n'avaient pas d'interprétation |
| Mobilier · Catégorie | Vrac (2 lignes) | Faune |
| Mobilier · Catégorie | Métal (catégorie) | Métal |
| Mobilier · Matière | ANImal / MEtal | organique / métal |
| Phase · Type | Occupation | Phase d'occupation |

## Relations stratigraphiques : sans type

Les relations « postérieur à » / « antérieur à » sont importées **sans type** : la feuille `Strati_Rel` n'a plus de colonne `Relation` ni
`Direction Vocabulaire`. Siamois range une relation selon l'ordre des deux unités et l'indicateur « asynchrone » : pour une relation
asynchrone, **l'unité 1 est la plus ancienne** (l'unité 2 est postérieure à elle).

- Le sens a été **contrôlé contre `SysDA_ExportRelationsStrati.xlsx`** (export SysDA, 49 relations, « postérieure à » ×40 et « antérieure à » ×9),
  qui fait foi : toutes les lignes du classeur sont en (ancienne, récente). Les 12 lignes « postérieur à » étaient déjà dans le bon sens une fois
  US1 et US2 inversés ; **les 8 lignes « antérieur à » du classeur étaient à l'envers** de l'export et ont été inversées aussi.
- La paire 1000 / 1001 existait en double et en contradiction (« 1000 postérieur à 1001 » et « 1000 antérieur à 1001 ») ; l'export dit que 1001
  est antérieure à 1000. Un seul exemplaire est conservé : le classeur compte **19 relations** (au lieu de 20).
- L'export en contient 48 distinctes. **29 ne sont pas reprises** : elles citent des UE (1047, 1048, 1103…) qui ne sont pas dans la feuille UE, et
  l'import refuserait la relation (« Recording unit introuvable »). Le classeur garde les 19 relations dont les deux UE existent.
- `RecordingUnitStratiRelSeeder` importe maintenant une relation sans type (avant, une ligne sans type était ignorée sans message).
  Le sens du type (`concept_direction`) est alors vide, comme quand l'API enregistre une relation sans type.

## Valeurs à ajouter à la main dans th337 (OpenTheso)

Valeurs que l'import refusera tant qu'elles n'existent pas (aucune n'est dans `generate_thesaurus_socle.py`).

| Feuille · colonne | Valeur | Lignes | Lien au type |
|---|---|---|---|
| UE · Interprétation | Terre végétale | 4 | US |
| UE · Interprétation | Filin | 3 | US |
| Mobilier · Désignation | os animal | 1 | Faune (1429) |
| Mobilier · Désignation | coquille d'huître | 1 | Faune (1429) |
| UE · Type | libellé alternatif « Entité archéologique » sur Fait (1200) | 13 | |
