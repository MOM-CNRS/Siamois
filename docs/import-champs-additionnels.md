# Import Excel : remplissage des champs additionnels

> Note de conception (2026-09-25). Point de départ : la migration FileMaker, qui perd poids, objet dateur, année, etc. faute de support des champs additionnels à l'import.

## Périmètre

- **Remplissage uniquement.** L'import écrit des réponses dans des champs **déjà définis et actifs** dans le formulaire du projet. Il ne crée jamais de champ et ne modifie jamais la configuration d'un formulaire.
- **Tables concernées :** celles qui ont des formulaires configurables (`ConfigurableTable`) : UE, Mobilier, Phase. Contenant n'est pas encore importé. Lieu et Unité d'action n'ont pas de formulaire configurable et restent hors périmètre.
- **Portée :** import PROJECT uniquement (voir « Pourquoi seulement en portée PROJECT »).

## Ce que le modèle impose

| Fait | Où |
|---|---|
| Un formulaire (`FormConfig`) est rattaché à un **projet**, au concept du champ type de la table et à une valeur de ce type (ou `null` pour le formulaire par défaut). | `FormConfig`, `ConfigurableTable` |
| Le type qui choisit le formulaire est `SIARU.TYPE` pour une UE, `SIAS.CAT` (Lot / Individu / Échantillon) pour le mobilier, `SIAPHASE.TYPE` pour une phase. | `ConfigurableTable` |
| Les champs d'une entité sont ceux du formulaire par défaut, complétés par ceux du formulaire de son type. | `TableFieldConfigServiceImpl.storedFields` |
| **Il n'existe pas de formulaire au niveau de l'institution** : un projet nouvellement créé n'a aucun champ additionnel tant que personne ne les a configurés. | `FormConfigRepository` (requêtes par `actionUnitId`) |
| Toutes les réponses d'une entité à un formulaire sont regroupées dans un `FormConfigAnswer` (un par couple entité / formulaire), qui contient les `CustomFieldAnswer` (clé : ce regroupement + le champ). | `FormConfigAnswer`, `CustomFieldAnswer` |
| La sauvegarde depuis l'interface fait déjà tout le travail : champs actifs du type, création du `FormConfig` si besoin, création ou récupération du `FormConfigAnswer`, déplacement des réponses si le type a changé. | `CustomFieldAnswerService.save(AnswerOwner, …)` |

Conséquence directe : **les formulaires du projet doivent être configurés avant l'import.** Ça devient une étape de la procédure de migration, au même titre que la création du projet.

## Format Excel

### Désigner un champ

Une colonne vise un champ additionnel par le **libellé du champ** précédé du préfixe `champ:` :

| identifiant | categorie label | … | champ:Poids | champ:Objet dateur |
|---|---|---|---|---|
| 793-L1 | Lot | … | 109 | |

- Le préfixe évite toute collision avec les colonnes du socle (`EXPECTED_COLUMNS`) et rend l'intention explicite.
- Le libellé est comparé après `normalize()`, comme les autres en-têtes : casse, accents et espaces ne comptent pas.
- Pour garder l'en-tête d'origine d'un export, on peut passer par `_meta` : `column_alias = poids en g`, `column_canonical = champ:Poids`. Le mécanisme d'alias existant suffit.
- Le libellé sert déjà d'identifiant de champ dans la configuration (`applyFieldChange` cherche le champ par `getLabel()`). On reste donc cohérent avec l'existant.

### Types pris en charge (V1)

| Classe du champ | Cellule attendue | Réponse écrite |
|---|---|---|
| `CustomFieldText` | texte | `CustomFieldAnswerText` |
| `CustomFieldInteger` | entier | `CustomFieldAnswerInteger` |
| `CustomFieldDecimal` | nombre | `CustomFieldAnswerDecimal` |
| `CustomFieldDateTime` | date Excel ou `yyyy-MM-dd` | `CustomFieldAnswerDateTime` |
| `CustomFieldMeasurement` | nombre, **dans l'unité du champ** | `CustomFieldAnswerMeasurement` (unité recopiée depuis le champ) |

**Hors V1 :**
- **Concepts** (`CustomFieldConcept*`, `CustomFieldSelectMultiple*`) : la résolution d'un libellé dépend de la source de vocabulaire du champ (code de champ, branche, collection), qui est en cours de discussion dans [reflexion-vocabulaire-champs.md](reflexion-vocabulaire-champs.md). Il faut attendre ce modèle pour ne pas figer la résolution trop tôt.
- **Références** (personnes, lieux, UE, phases, mobilier, unités d'action, codes) : chacune demande sa propre règle de résolution. Ce sera une V2.

Une colonne `champ:` qui vise un type non pris en charge est une **erreur d'en-tête**, pas une colonne ignorée sans le dire.

## Validation (avant tout écriture)

Elle s'ajoute à l'écran de validation actuel, qui reste bloquant tant qu'il y a des erreurs :

1. **Au niveau de l'en-tête.** Le libellé doit correspondre à au moins un champ additionnel du projet pour cette table. Sinon : « champ inconnu ». S'il correspond à plusieurs champs de classes différentes : « libellé ambigu ».
2. **Au niveau de la ligne.** Si la cellule est remplie, le champ doit être actif pour le type de la ligne (formulaire par défaut + formulaire du type). Sinon, erreur sur la ligne : « champ Poids absent du formulaire Individu ».
   - L'interface, elle, **abandonne silencieusement** une réponse à un champ inactif (`save` → `filteredAnswers`). L'import doit au contraire la signaler : une valeur de migration qui disparaît sans rien dire est une perte de données.
3. **Au niveau de la valeur.** Nombre, entier ou date illisible : erreur sur la ligne. On ne met jamais `null` en silence (même défaut que `parseOffsetDateTime` aujourd'hui, voir « Questions ouvertes »).
4. **Champ obligatoire** (`FieldFormConfig.isMandatory`) : l'import le **signale sans bloquer**, parce qu'une migration arrive souvent avec des trous. Le formulaire le rappellera à la prochaine édition.

Les champs actifs se calculent **une fois par couple (table, type)** et non ligne par ligne. Un import compte quelques types et des milliers de lignes.

## Écriture

- **Où :** dans les seeders (`RecordingUnitSeeder`, `SpecimenSeeder`, `PhaseSeeder`), après l'enregistrement des entités, puisqu'il faut leur id pour créer le `FormConfigAnswer`.
- **Comment :** reprendre les règles de `CustomFieldAnswerService.save`, dans une version groupée :
  1. regrouper les lignes par (table, type) ;
  2. appeler `createOrGetFormConfig(projectId, table, typeConceptId)` une fois par groupe ;
  3. créer ou récupérer le `FormConfigAnswer` de chaque entité ;
  4. faire l'upsert des `CustomFieldAnswer` par lots, avec la même stratégie de flush que les seeders.

  Appeler `saveAdditionalFieldAnswers` entité par entité serait correct, mais ferait plusieurs requêtes par ligne.
- **Idempotence.** Réimporter le même classeur met à jour les réponses (clé `FormConfigAnswer` + champ) et n'en crée jamais de doublon. C'est ce qui permet d'importer le socle d'abord, puis de repasser le classeur enrichi des colonnes `champ:`.
- **Cellule vide = on ne touche à rien.** Dans l'interface, une réponse vide efface la réponse existante ; pas à l'import. Un classeur partiel ne doit pas effacer des données saisies entre-temps dans Siamois. Pour effacer, on passe par l'interface.
- **Changement de type à la réimport.** Si le type d'une entité change d'un import à l'autre, il faut appliquer la même réconciliation que l'interface (`reconcileTypeChange`) pour ne pas laisser de réponses rattachées à l'ancien formulaire.

## Écran de mapping

- Les colonnes `champ:` apparaissent comme reconnues, avec le libellé du champ, sa classe et les types pour lesquels il est actif.
- Un champ reconnu mais **inactif pour certains types présents dans le fichier** est affiché en avertissement dès l'étape de mapping, avant la validation ligne par ligne.
- Le résumé d'import ajoute un compteur « réponses aux champs additionnels : N créées / M mises à jour ».

## Pourquoi seulement en portée PROJECT

Les formulaires sont rattachés à un projet. En portée ALL, un même classeur couvre plusieurs projets, qui peuvent avoir des formulaires différents. La validation d'en-tête n'aurait plus de sens, et un même libellé pourrait désigner des champs différents selon le projet. On pourra l'envisager plus tard, en validant ligne par ligne selon le projet de la ligne, mais ce n'est pas nécessaire pour la migration FileMaker, qui fait un classeur par projet.

## Application à la migration FileMaker

| Donnée | Champ à configurer dans les formulaires Mobilier (Lot) | Remarque |
|---|---|---|
| `poids`, `poids en g` | mesure, unité g | à vérifier : `CustomFieldMeasurement` est-il configurable dans un formulaire Mobilier ? (voir « Questions ouvertes ») |
| `objet dateur` | texte en V1 (« oui ») | concept oui/non en V2 |
| `annee` (UF) | entier, formulaire UE | |
| catégorie céramique | concept | **V2**, elle reste en désignation d'ici là |

Le script `scripts/filemaker_to_siamois.py` n'aura qu'à ajouter les colonnes `champ:Poids`, etc. Les classeurs déjà importés seront mis à jour par la réimport.

## Questions ouvertes

1. **Mesures hors UE.** Aujourd'hui, `CustomFieldMeasurement` est rattaché aux UE (champ créé à la volée, `CustomFieldMeasurementService.findByRecordingUnit`). Peut-on le placer dans un formulaire Mobilier ? Si ce n'est pas le cas, le poids du mobilier sera un `CustomFieldDecimal` dont le libellé porte l'unité (« Poids (g) »).
2. **Unicité des libellés.** Deux champs additionnels d'une même table peuvent-ils avoir le même libellé dans un projet ? Si oui, faut-il un identifiant stable (id ou code) à la place du libellé dans l'en-tête ?
3. **Dates du socle.** `parseOffsetDateTime` renvoie `null` en silence quand une date est illisible. Il faut corriger ça en même temps, pour que la règle « jamais de `null` silencieux » s'applique partout.
4. **Champ obligatoire vide.** Faut-il seulement signaler (proposition ci-dessus), ou bloquer au choix de la personne qui importe ?
