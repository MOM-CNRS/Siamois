# Construire le thésaurus « Socle commun » de Siamois

Ce document est autonome : il explique ce qu'est Siamois pour ce besoin, comment l'application utilise un thésaurus, et comment en
construire un. Il s'adresse à une personne ou à Claude, et ne suppose pas d'avoir accès au code de l'application. Il ne traite ni
de l'import dans OpenTheso ni du déploiement.

## Contexte : tables, types et listes dans Siamois

**Siamois** est un logiciel de gestion des données de fouille archéologique. Ses données sont rangées en **tables** (une par sorte de fiche).
Chaque table a un code de deux ou trois lettres après `SIA` :

| Table | Code | Ce que c'est |
|---|---|---|
| Projet | `SIAAU` | l'opération (unité d'action) qui regroupe les fiches |
| Lieu | `SIASU` | un lieu (unité spatiale) |
| Enregistrement | `SIARU` | l'unité d'enregistrement (US, fait, ensemble…) |
| Stratigraphie | `SIARU.STRATI.TABLE` | les relations stratigraphiques entre enregistrements |
| Mobilier | `SIAS` | un objet, un lot, un prélèvement |
| Étude | `SIAST` | une étude (sur du mobilier, une unité, un traitement) |
| Document | `SIAD` | une pièce de la documentation (plan, photo, rapport…) |
| Contenant | `SIAC` | une caisse, un sachet, une boîte |
| Phase | `SIAPHASE` | un regroupement chronologique d'enregistrements |
| Personne | `SIAP` | une personne de l'équipe (son rôle) |
| Définition de mesure | `SIAMD` | la nature des mesures prises |

**Le type d'une fiche.** Chaque table a un champ « type » (le **type pilote**). Ses valeurs sont les types de fiche : *US, fait, ensemble*
pour un enregistrement ; *céramique, métal, faune…* pour du mobilier ; *carte, plan, photographie…* pour un document.
Choisir le type d'une fiche décide de **sa forme** : quels champs elle montre, dans quel ordre, avec quelles règles, quel format de
numéro (`US{NUM_UE:000}` donne US001, US002…) et quelles listes de valeurs.

**Les types sont choisis projet par projet.** Le thésaurus liste les types **candidats** (les sous-concepts du champ type de la table).
Chaque projet choisit ceux qu'il utilise, et Siamois garde, pour chaque triplet (projet, table, type), une configuration de fiche.
Le projet par défaut part d'un modèle qui nomme les types et les listes à utiliser : il les cherche dans le thésaurus, d'où
l'importance des noms (§6).

**Les champs à vocabulaire.** Un champ à vocabulaire (par exemple la classe d'un mobilier) offre une liste de valeurs prise dans le
thésaurus, à choix unique ou multiple. La liste d'un champ peut être définie de **deux façons**, au choix de chaque projet et de
chaque type :

1. **tout ce qui est sous le champ** : le champ a un code (`SIAS.CLASS`), repéré par une notation dans le thésaurus ; sa liste est
   l'ensemble de ses sous-concepts (§1) ;
2. **une collection** : un sous-ensemble choisi. C'est nécessaire dès que la liste d'**un même champ** doit varier selon le **type** de
   la fiche. Exemple : les classes d'un mobilier de type « Métal » (ferreux, alliage cuivreux, monétaire…) ne sont pas celles d'un mobilier de type « Céramique ».
   Le champ porte alors toutes les classes une fois chacune, et une collection par type donne celles qui conviennent (§5, §6).

Une liste peut aussi dépendre de la **valeur d'un autre champ** (l'interprétation d'une unité selon sa nature) : même mécanisme, par
des liens `skos:related`.

## 1. Ce que l'application lit

L'application ne lit que **la branche de tête de notation `SIAMOIS#SIAAUTO`**, et dans cette branche :

1. tout concept dont une **notation commence par `SIAMOIS#`** : ce qui suit le préfixe est un **code de champ** (`SIAS.CLASS`) ;
2. pour chaque concept dont le code est un code que l'application connaît, **le concept et tout son sous-arbre** : ce sont le champ et
   ses valeurs ;
3. les liens `skos:related` entre concepts de cette branche (listes qui dépendent d'une autre valeur, champs qui ne s'appliquent qu'à un type) ;
4. les collections dont le nom suit le format du §6, quand on règle la liste d'un type dans un projet.

Tout le reste est ignoré : les concepts hors de la branche, les notations d'un autre préfixe, les concepts dont le code est inconnu de
l'application (signalé dans les logs comme un avertissement, sans bloquer).

## 2. Règles dures

| Règle | Pourquoi |
|---|---|
| Une seule branche de tête, de notation exactement `SIAMOIS#SIAAUTO` | sans elle le thésaurus « ne définit aucun code de champ » |
| Un champ vocabulaire = un concept de notation `SIAMOIS#<CODE>`, **code identique à celui de l'application**, comparé en majuscules | c'est la seule clé de rattachement |
| Les valeurs d'un champ sont ses **sous-concepts** (à n'importe quelle profondeur) | le sous-arbre entier est chargé |
| Un code n'apparaît qu'**une fois** dans le thésaurus | l'application garde un seul concept par (thésaurus, code) |
| Un champ sans vocabulaire se note `SIAMOISFIELD#<TABLE>.<propriété>` | le préfixe `SIAMOISFIELD#` n'est pas lu : le champ est documenté sans être chargé |
| Une valeur est portée **une seule fois** par champ, même si plusieurs types l'autorisent | les restrictions passent par des liens et des collections (§5, §6) |
| Les libellés d'un même champ sont **uniques** | la saisie et le rebranchement les retrouvent par leur libellé |
| Libellés en français **et** en anglais | l'interface choisit selon la langue de l'utilisateur |

Ne jamais inventer un code : il vient de l'application (la liste du §3 en est la référence). Un code qui n'existe
pas côté application reste sans effet ; un code que l'application attend et que le thésaurus n'a pas est signalé comme manquant.

## 3. L'arbre à produire

```
SIAMOIS#SIAAUTO                         « Siamois - Concepts système »   (tête, seul concept sans parent)
 └─ SIAMOIS#SIAS                        table : un concept par entité
     ├─ SIAMOIS#SIAS.CAT                champ vocabulaire « Type »  ← le type pilote de la table
     │    ├─ Céramique                  valeur
     │    └─ Métal
     ├─ SIAMOIS#SIAS.CLASS              champ vocabulaire « Classe »
     │    └─ … toutes les classes, une fois chacune
     └─ SIAMOISFIELD#SIAS.sanitaryAlert champ sans vocabulaire (documenté, non chargé)
```

Niveaux : tête → **table** (notation `SIAMOIS#<TABLE>`) → **champ** → **valeurs**. Un concept de table ou de champ a une définition
qui dit à quoi il sert (type de donnée, code, équivalent dans le référentiel national).

### Tables et champs vocabulaire

Le type pilote d'une table est le champ qui sélectionne la configuration de la fiche. Il est marqué (T).

| Table | Notation | Champs vocabulaire (code) |
|---|---|---|
| Projet | `SIAAU` | `TYPE` (T), `STATUS`, `DEVELOPMENT_NATURE`, `PERIODS`*, `SUBJECTS`*, `SYSTEM`, `FIELD_STATUS` |
| Lieu | `SIASU` | `TYPE` (T) |
| Enregistrement | `SIARU` | `TYPE` (T), `GEOMORPHO`, `GEOMORPHOAGENT`, `INTERPRETATION`, `EROSIONSHAPE`, `EROSIONPROFILE`, `EROSIONORIENTATION`, `CHRONO` |
| Stratigraphie | `SIARU.STRATI.TABLE` | `STRATI` (type de relation) ; `STRATI.ANTERIOR`, `STRATI.POSTERIOR`, `STRATI.SYNCHRONOUS` (relations) |
| Mobilier | `SIAS` | `CAT` (T), `MATIERE`*, `CLASS`*, `INTERPRETATION`, `METHOD`, `CHRONO`, `SANITARY`, `TRANSFER`, `SELECTIVECONSERVATION` |
| Étude | `SIAST` | `TYPE` (T), `METHODE`, `TYPETRAIT` |
| Document | `SIAD` | **`TYPE` (T)**, `CATEGORY`, `NATURE`*, `FORMAT`, `SCALE`, `LANGUAGE`, `KEYWORD`* |
| Contenant | `SIAC` | `TYPE` (T) |
| Phase | `SIAPHASE` | `TYPE` (T), `PERIOD`*, `KEYWORD`* |
| Personne | `SIAP` | `ROLE` |
| Définition de mesure | `SIAMD` | `TYPE`, `NATURE` |

Le code complet d'un champ est `<TABLE>.<CODE>` : `SIAS.CLASS`, `SIAD.TYPE`. (*) choix multiple ; les autres sont à choix unique. Ce choix
est connu de l'application, le thésaurus le rappelle seulement dans la définition du champ.

**Document : le pilote est `SIAD.TYPE`, pas `SIAD.CATEGORY`.** La catégorie est une liste ordinaire qui dépend du type.

## 4. Les valeurs

- **Identifiant stable** : chaque valeur a un libellé court, français et anglais, et une définition quand le référentiel en donne une.
- **Alignement** : une valeur qui a un équivalent dans le référentiel national (th326) porte un `skos:exactMatch` vers son URI ; si le
  libellé national diffère, il va en `altLabel`.
- **Pas de doublon par parent** : une valeur appartient à un seul champ. Une même notion utile à deux champs (la chronologie sous
  `SIAAU.PERIODS`, `SIARU.CHRONO`, `SIAS.CHRONO`, `SIAPHASE.PERIOD`) est **dupliquée** sous chacun, jamais partagée entre parents.
- **Listes de démonstration** : les valeurs fournies illustrent le champ, elles ne remplacent pas un vocabulaire métier.

## 5. Listes qui dépendent du type

Cas : la liste « Classe » d'un mobilier ne propose que des classes de métal quand le type est « Métal ».

1. Le champ porte **toutes** les valeurs possibles, une fois chacune.
2. Pour chaque valeur autorisée par un type : un lien **`skos:related`** entre le type (la valeur pilote) et la valeur. Le lien est
   symétrique. Une valeur commune à plusieurs types (« Indéterminé ») est reliée à chacun d'eux.
3. Un champ qui **ne s'applique qu'à certains types** (« Échelle » pour un document graphique, « Méthode de prélèvement » pour un
   prélèvement) : un `skos:related` entre le type et le **champ** lui-même.
4. Une valeur qui dépend d'une **autre valeur que le type** (la catégorie d'un document selon son type) suit le même schéma : le pilote
   est la valeur choisie dans le champ pilote.
5. Une collection technique `SIAMOIS – Dépendance logique` regroupe les valeurs pilotes.

**Règle pour un champ dont le pilote est le type d'une table.** Si le référentiel range les types dans des catégories, un type
reprend les valeurs de sa catégorie (ou de **chacune** de ses catégories s'il en a plusieurs : le « diagramme stratigraphique » est
à la fois écrit et graphique).

## 6. Collections : une liste pour un couple (champ, type)

Pour chaque champ dont la liste dépend d'un pilote et pour chaque pilote, une **collection** contient exactement les valeurs que ce
pilote autorise. Elle sert à régler, projet par projet, la liste d'un champ pour un type donné (source « Collection »).

**Nom, à respecter au caractère près** (deux espaces avant le crochet, tiret cadratin `—`, point médian `·`) :

```
<Table> · <Champ> — <Libellé du pilote>  [<CODE DU CHAMP> @ <CODE DU CHAMP PILOTE>:<slug du pilote>]
```

Exemples :

```
Mobilier · Classe — Métal  [SIAS.CLASS @ SIAS.CAT:metal]
Document · Échelle — carte  [SIAD.SCALE @ SIAD.TYPE:map]
Document · Catégorie — diagramme stratigraphique  [SIAD.CATEGORY @ SIAD.TYPE:strat-diagram]
```

- Le **libellé du pilote** est celui du type dans le thésaurus (français). Il sert à retrouver la collection d'un type ; s'il change, la
  collection doit être renommée.
- Le **slug** est le libellé en minuscules sans accents, mots séparés par des tirets, ou un identifiant court stable.
- **Les membres sont des concepts de la branche**, pas des copies : une collection ne crée aucun concept.
- Ce nom sert aussi, tel quel, à **retrouver la même collection dans un autre thésaurus** quand un champ est rebranché. Deux
  thésaurus qui suivent la même convention de nom se rebranchent automatiquement.
- Une collection n'est créée que pour un couple (champ, pilote) qui a au moins une valeur ; pas de collection vide.

## 7. Procédure pour construire (à la main ou par un outil)

1. **Fixer les tables** (§3) avec leur notation `SIAMOIS#<TABLE>`, un libellé et une définition. Tous sous la tête `SIAMOIS#SIAAUTO`.
2. **Pour chaque table, créer ses champs**, dans cet ordre : le type pilote d'abord, puis les autres listes, puis les champs sans vocabulaire
   (`SIAMOISFIELD#…`). Les codes viennent de l'application, pas de la mémoire.
3. **Remplir les valeurs** de chaque champ, français et anglais, une fois chacune, avec définitions et alignements (§4).
4. **Déclarer les dépendances** : pour chaque champ qui dépend d'un pilote, relier le pilote à ses valeurs (§5), et le pilote aux champs
   qu'il rend pertinents.
5. **Générer les collections** (§6) à partir des liens de l'étape 4 : une par couple (champ, pilote), dans le format exact.
6. **Contrôler** (§8).

Si le thésaurus est produit par un script, toute modification se fait dans le script, jamais dans le fichier produit.

## 8. Liste de contrôle

- [ ] Une seule tête `SIAMOIS#SIAAUTO` ; chaque table est directement dessous.
- [ ] Chaque table a **un** type pilote et un seul (document : `SIAD.TYPE`).
- [ ] Chaque code `SIAMOIS#…` figure dans le tableau du §3. Tous les codes du tableau sont présents ; les champs sans vocabulaire
      (par exemple `SIASU.COMMENTS`, `SIASU.INSEE_CODE`, `SIASU.LOCALITY_NAME`, `SIASU.PLACE_NUMBER`, `SIASU.SHOM_MAP_NUMBER`) se notent
      `SIAMOISFIELD#…`.
- [ ] Aucun libellé en double dans un champ ; tous les libellés en français et en anglais.
- [ ] Chaque lien `skos:related` est présent dans les deux sens.
- [ ] Chaque collection suit le format du §6 ; ses membres sont des concepts de la branche ; aucune collection vide.
- [ ] Toute valeur qu'un pilote autorise est dans la collection (champ, pilote) correspondante, et inversement.
- [ ] Les notions partagées (chronologie) sont dupliquées, pas rattachées à deux parents.

## 9. Pour Claude

1. Lire ce document en entier, puis, si un script de génération est fourni, la section de la table concernée.
2. Ne rien inventer : ni code de champ, ni libellé « officiel », ni équivalence avec le référentiel. Si une source manque, le dire.
3. Produire le thésaurus, puis vérifier la liste de contrôle du §8 point par point.
4. Dire ce qui n'a pas été vérifié. Ne pas committer.
