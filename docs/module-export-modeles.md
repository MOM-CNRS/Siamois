# Module d'export et modèles d'export — décisions de conception

Document de reprise : synthèse de la discussion de conception (2026-10-02), à relire après un `/clear`.
Aucune implémentation n'existe encore. Le plan par lots reste à écrire.

## 1. Objectif

Un module d'export générique piloté par des **modèles** (templates). Un modèle décrit un classeur
tableur : ses feuilles, leurs sources de données, leurs colonnes et les règles qui alimentent chaque
colonne. Le module est **générique** : le premier modèle d'exemple est l'export au format du
**référentiel national relatif au rapport d'opération d'archéologie préventive** (Ministère de la
Culture, DGPA/SDA, 2025), mais ce n'est qu'un modèle produisible par le module, pas un cas codé en dur.

Sources de référence :

- Maquette Claude Design : projet `d42dec9c-309a-4a2b-bb33-077dac3ee31e`, fichiers
  `Paramètres.dc.html` (liste des modèles, fiche) et `Export Templates.dc.html` (éditeur de mapping
  plein écran, aperçu par projet). Le runtime `support.js` n'apporte rien.
- Référentiel : PDF « Référentiel national relatif au rapport d'opération d'archéologie préventive »
  (octobre 2025). Chapitre IV = inventaire détaillé, 9 feuilles ; chapitre III = modalités de
  transmission (nom de fichier, formats).

## 2. Ce que demande le référentiel (pour le premier modèle)

Un classeur `.xlsx` / `.ods` unique. Neuf feuilles nommées : `OA`, `situation`, `UE`, `relation`,
`mobilier`, `VAB`, `prelevement`, `traitement`, `documentation`. Une feuille sans données n'est pas
créée. En-têtes de colonne imposés (sans accents ni ponctuation, même ordre), valeurs seulement (pas de
formule, pas de cellule fusionnée), 32 767 caractères maximum par cellule. Nom de fichier :
`OA` + `code_OA_NAT` + `_` + date `aaaammjj` + `_inventaire detaille`. Dates ISO 8601.

Granularité des feuilles lues (1 à 5) :

| Feuille | Granularité |
|---|---|
| `OA` | une seule ligne (une opération par fichier) |
| `situation` | une ligne par parcelle (milieu terrestre) |
| `UE` | une ligne par unité d'enregistrement, ordonnée par type d'UE |
| `relation` | une ligne par relation entre deux UE (`code_UE1`, `type_relation_UE`, `code_UE2`) |
| `mobilier` | une ligne par objet ou lot |

Les feuilles 6 à 9 (VAB, prélèvement, traitement, documentation) et le chapitre V n'ont pas encore été
lues en détail.

## 3. Périmètre

### Dans le périmètre

- En-têtes, ordre et type de sortie **éditables** par colonne. Le modèle SDA n'est qu'un modèle
  parmi d'autres, le module ne verrouille rien.
- Type de sortie par colonne : texte, nombre, date.
- Types de règle de colonne : **direct** (champ, éventuellement via un chemin de jointure),
  **constante**, **concaténation**.
- **Concaténation** étendue : chaque partie peut porter un libellé (`nom du champ: valeur & …`) et peut
  venir d'une autre entité par chemin de jointure. Le séparateur est configurable.
- **Jointure par chemin** : une colonne `code OA` prend la valeur `projet.code_oa`. Cela remplace tout
  besoin d'un type de règle « contexte » dédié.
- Trois familles de **sources de feuille** :
  1. entité + types (par exemple unités d'enregistrement de type X ou Y) ;
  2. **table technique** (hiérarchies, relations…) pour des feuilles comme `relation` ;
  3. **projet** (un projet = une seule ligne), pour la feuille `OA`.
  La feuille `situation` est une source « lieux » **filtrée par type** (parcelle).
- Plusieurs règles par colonne, chacune ciblant un sous-ensemble de sources de la feuille (modèle déjà
  présent dans la maquette).
- Option « omettre la feuille si elle est vide » (le référentiel l'exige, c'est peu coûteux).
- Un modèle appartient à une **institution** ; un utilisateur voit les modèles de son institution.
- Un **projet de référence** sert à l'éditeur (choix des champs, aperçu). Le modèle peut être appliqué à
  un autre projet.
- Le **périmètre d'un export est un projet**.
- Interface dans les **paramètres, en JSF**. Pas d'API REST pour l'instant.

### Hors périmètre (volontairement)

- Validation de conformité et rapport d'erreurs (niveaux gras / normal / italique du référentiel, règles
  croisées « au moins l'un de… »). Prévu dans un autre sprint, éventuellement via JSON Schema en sortie.
- Table de correspondance des vocabulaires (valeur locale → terme national). C'est de la validation, pas
  le rôle de l'export.
- Séparateurs normalisés `&` / `|`, qualité des données.
- Champs absents du modèle Siamois (`versement`, `etat_sanitaire`, …) : quelqu'un d'autre y travaille.
- API REST des modèles.
- Export asynchrone (job, statut, téléchargement différé) : synchrone au début.

## 4. Stockage : colonnes + JSON partageable

Un modèle est enregistré en base en **deux parties** :

- **Colonnes locales** (non partagées) : identifiant, institution, projet de référence, dates, auteur,
  statut. On les filtre et on les liste, elles ne vont pas dans le JSON.
- **JSON partageable** : uniquement la structure du modèle : nom, `schemaVersion`, identifiant global et
  version du modèle, patron du nom de fichier, feuilles (nom, source, granularité, tri,
  « omettre si vide »), colonnes (en-tête, type de sortie, règles).

Objectif : sérialiser et partager les modèles via une **instance centrale** qui les liste, et les
importer dans une autre instance.

### Références portables

Pas d'identifiant de base de données dans le JSON.

- **Champs système** : par code stable (`FieldCode`).
- **Champs configurables** : par **concept**. Le concept est la clé du champ. Il vit dans un système
  externe (**Opentheso**). La référence contient : l'**URI du concept**, son **identifiant externe**, et
  l'**identifiant externe de son thésaurus**.
- **Sources de type table technique** : par clé de source **enregistrée côté code** (voir règle de
  sécurité ci-dessous), jamais par nom de table.

À l'import ou à l'application sur un projet, chaque référence est **résolue localement**. Une référence
non résolue donne une cellule vide et un **avertissement** dans l'écran d'export (pas d'erreur
bloquante, pas de rapport de validation).

### Le JSON est déclaratif, jamais exécutable

Un JSON qui vient d'une instance centrale est une donnée non fiable.

- Seuls les types de règle fermés ci-dessus sont acceptés (direct, constante, concaténation).
- **Aucun SQL, aucun nom de table ou de colonne, aucune expression libre**. Les tables techniques sont
  une liste fermée de sources enregistrées dans le code (par exemple `recording_unit_hierarchy`), chacune
  exposant ses colonnes nommées (parent, enfant) et ses jointures vers les entités.
- Le JSON est validé (schéma, `schemaVersion`) avant d'être enregistré.

## 5. Grammaire du chemin de jointure (v1)

Un chemin part de la ligne de la feuille et suit des relations pour atteindre un champ
(`projet.code_oa`, `ue.parent.numero`).

- v1 : seuls les chemins à **cardinalité 1** sont autorisés, plus une **liste terminale** (concaténer,
  trier) comme dans la maquette (`isList`, `concatenate`, `sorted`, `separator`).
- Un chemin multi-valué au **milieu** est refusé par l'éditeur.
- À vérifier avec le modèle SDA : si `phase.periode` ou un chemin similaire est nécessaire, la grammaire
  devra être étendue (hypothèse à tester avant de figer).

## 6. Exécution d'un export

Entrée : un modèle et un projet. Sortie : un classeur `.xlsx` (Apache POI et poi-ooxml sont déjà dans le
`pom.xml`).

1. Résoudre les références du modèle pour le projet cible.
2. Pour chaque feuille : lire la source, appliquer le filtre de types, évaluer les règles de chaque
   colonne, trier.
3. Omettre la feuille si elle est vide et que l'option est active.
4. Écrire les valeurs sans formule, tronquer ou signaler au-delà de 32 767 caractères.
5. Nommer le fichier selon le patron du modèle.

Les avertissements de résolution sont affichés avec le fichier produit.

## 7. Risques et points ouverts

- **Éditeur en JSF.** L'éditeur de la maquette (plein écran, règles par source, aperçu) est lourd en
  JSF/PrimeFaces. Décision : on essaie quand même. Plan B : livrer d'abord liste, import/export JSON et
  export, puis l'éditeur visuel.
- **Grammaire de chemin** (§5) : à confirmer sur le modèle SDA réel.
- **Droits** : l'export lit des données en masse. Les droits de lecture de l'utilisateur doivent filtrer
  les lignes (y compris celles traversées par jointure). À aligner avec `docs/permissions.md`.
- **Maquette vs référentiel** : les données d'exemple de la maquette (4 feuilles) ne sont
  qu'illustratives. Le vrai modèle SDA a 9 feuilles. Il est à écrire à la main en JSON pour valider le
  schéma, puis livré comme modèle amorcé.
- **Application à un autre projet** : les types et champs sont configurés par projet. Un champ absent
  dans le projet cible produit une cellule vide et un avertissement.
- **Feuilles 6 à 9 du référentiel** : à lire avant de figer le schéma de sources.

## 8. Ordre de livraison envisagé

1. Schéma JSON du modèle, avec le modèle SDA écrit à la main.
2. Moteur d'export (lecture du JSON, résolution des références, évaluation des règles, écriture POI).
3. Persistance des modèles par institution (colonnes locales + JSON), import / export du JSON.
4. Écrans JSF dans les paramètres : liste, fiche, export.
5. Éditeur de mapping.

Ce découpage sera repris dans le plan détaillé.
