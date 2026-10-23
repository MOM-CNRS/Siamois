# Thésaurus « Socle commun »

> Généré le 2026-10-23 par [generate_thesaurus_socle.py](generate_thesaurus_socle.py) à partir du th326 (référentiel national)
> et du th230 (ancien thésaurus SIAMOIS). Résultat : [siamois-socle-commun.rdf](siamois-socle-commun.rdf).
> **Non testé à l'import dans OpenTheso** : le fichier suit le format d'export du th230, mais l'import n'a pas été rejoué.

## Contenu

11 tables, 164 champs dont 46 à vocabulaire, 724 concepts, 66 collections. Tout champ du socle (formulaires `*Form.java`,
`SpatialUnit`) y est décrit, avec son type de donnée, son code et son équivalent dans le référentiel national quand il existe.
Les 46 codes de champ de l'application (`SIARU.TYPE`, `SIAS.CLASS`…) sont tous présents. Les listes sont des **démonstrations**,
pas des vocabulaires exhaustifs.

| Table | Notation | Champs à vocabulaire (code) |
|---|---|---|
| Projet | `SIAAU` | `TYPE`, `STATUS`, `FIELD_STATUS`, `SYSTEM`, `DEVELOPMENT_NATURE`, `PERIODS`, `SUBJECTS` |
| Lieu | `SIASU` | `TYPE` |
| Enregistrement | `SIARU` | `TYPE`, `GEOMORPHO`, `GEOMORPHOAGENT`, `INTERPRETATION`, `EROSIONSHAPE`, `EROSIONPROFILE`, `EROSIONORIENTATION`, `CHRONO` |
| Stratigraphie | `SIARU.STRATI.TABLE` | `STRATI`, `STRATI.ANTERIOR`, `STRATI.POSTERIOR`, `STRATI.SYNCHRONOUS` |
| Mobilier | `SIAS` | `CAT`, `MATIERE`, `CLASS`, `INTERPRETATION`, `METHOD`, `CHRONO`, `SANITARY`, `TRANSFER`, `SELECTIVECONSERVATION` |
| Étude | `SIAST` | `TYPE`, `METHODE`, `TYPETRAIT` |
| Document | `SIAD` | `CATEGORY`, `TYPE`, `NATURE`, `FORMAT`, `SCALE`, `LANGUAGE`, `KEYWORD` |
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
3. Une **collection** est générée par couple (champ, pilote), nommée `Table · Champ — Pilote  [CODE @ CODE_DU_CHAMP_PILOTE:pilote]`
   (par exemple `Mobilier · Classe — Métal  [SIAS.CLASS @ SIAS.CAT:metal]`). Ce format est lu par le gabarit « Projet par défaut » de
   l'application: le libellé du pilote, après le tiret cadratin, doit être celui du type dans le thésaurus. Elle sert quand
   on règle la liste d'un type dans la config d'un projet (source « Collection »). Les membres sont dans la branche, donc chargés.
4. Un lien `skos:related` entre un type et un **champ** dit que le champ ne s'applique qu'à ce type (« Méthode de prélèvement »
   pour un prélèvement, « Échelle » pour un document graphique).
5. La collection `SIAMOIS – Dépendance logique` regroupe les pilotes, comme dans le th230.

Exemples livrés : interprétation d'une unité selon US / fait / ensemble / sépulture ; matière, classe et interprétation d'un
mobilier selon son type (céramique, métal, lithique, verre, faune, VAB, prélèvement) ; type, support, format et échelle d'un
document selon sa catégorie ; méthode d'une étude selon son type ; nature d'une mesure selon son type de mesure.

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
  national lit « objet » et « lot » dans le type. Deux collections les séparent.
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
