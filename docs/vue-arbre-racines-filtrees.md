# Vue arbre : racines filtrées (réflexion, non implémenté)

Statut : **réflexion en pause** (2026-10-22). Rien n'est construit. À reprendre quand un cas d'usage réel
l'exige. Contexte général : `vue-arbre-listes.md`.

## Le besoin

Un onglet de fiche (ou une vue enregistrée) filtré sur un type, par exemple « Les faits » = unités
d'enregistrement de type *Fait*. Hiérarchie de l'exemple : 006 (Ensemble) contient 002 (Fait), qui
contient 007 (UE par défaut).

**Comportement actuel** (règle 5 de `vue-arbre-listes.md`) : le filtre garde les lignes qui correspondent et
ajoute leurs ancêtres en contexte, en grisé. La vue « Faits » montre donc 006 (grisé) > 002. 006 n'est là que
pour situer 002 ; 007 n'apparaît pas (il ne correspond pas).

**Ce qu'on voudrait peut-être** : que les faits soient le point d'entrée de l'arbre. 002 en racine, son
contenu (007) dessous. Ce n'est plus un filtre sur le tableau, c'est un choix de **racines**.

## Règle retenue si on le fait

Un **filtre de racines**, distinct du filtre de lignes, propre à la vue arbre :

- **Candidats** : les lignes du contexte qui correspondent au filtre de racines (ex. `type = Fait`, et
  éventuellement `projet = …`).
- **Racines** : les candidats dont **aucun ancêtre**, direct ou plus haut, n'est lui-même candidat. Comme
  dans l'arbre classique, une ligne qui a un parent dans la hiérarchie n'est pas racine : un fait B situé
  dans « autre chose » situé dans un fait A n'apparaît que sous A, jamais en racine.
- **Enfants** : tout le contenu direct, dans le contexte, **sans filtre** (quel que soit le type).
- Recherche texte et filtres de lignes : inchangés, par-dessus (ancêtres en contexte).
- Compteur de la vue : le nombre de racines.

Avec l'exemple : 002 est racine, 007 est dessous, 006 n'apparaît pas (il n'est pas candidat). Si 007 était
aussi un fait, il resterait sous 002.

Variantes écartées en discussion :

- **Arbre contracté** (seuls les candidats affichés, parent = ancêtre candidat le plus proche) : une vue
  « Faits » ne contiendrait que des faits imbriqués. Écarté : on veut voir le contenu complet.
- **Racines filtrées pures, sans règle sur les ancêtres** : B serait à la fois racine et sous A. Écarté : ça
  contredit la règle de l'arbre classique.

## Implémentation envisagée

Serveur, `domain/services/tree/HierarchyTreeQuery.java` (aujourd'hui : racines = lignes du contexte dont
aucun parent n'est dans le contexte, ou, avec recherche/filtre, celles de la fermeture d'ancêtres) :

1. Candidats : `ids(contexte ET filtre de racines)`.
2. Remontée des ancêtres niveau par niveau avec `parentEdges`, comme `ancestorClosureInScope`, en gardant
   les liens enfant→parent en mémoire.
3. Pour chaque candidat, remonter en mémoire : s'il a un ancêtre candidat, il n'est pas racine.
4. Enfants d'un parent : `hasParent(parentId)` dans le contexte, **sans** la fermeture (aujourd'hui,
   `idIn(fermeture)` s'applique aussi aux enfants quand la liste est filtrée).

Coût : profondeur de la hiérarchie × nombre de candidats, sans parcours de sous-arbres, donc faisable avec
les requêtes existantes (pas de CTE récursive nécessaire).

Client : un second bloc de filtres, « Racines de l'arbre », visible seulement en vue arbre et seulement pour
les types qui ont une vue arbre (lieux, UE, projets) ; un champ dans l'état de la vue (donc dans les vues
enregistrées, voir `vues-et-etat-utilisateur.md`). L'arbre sait déjà se déplier : peu de changement côté
rendu.

## Pour

- Répond à « partir des faits et descendre vers ce qu'ils contiennent », ce que ni la liste plate ni le
  contexte grisé ne font.
- Cohérent avec l'arbre classique (une ligne n'est racine que si elle n'a pas d'ancêtre dans l'ensemble de
  départ).
- Peu de changement de rendu ; l'essentiel est une variante de requête côté serveur.
- Utile au-delà des faits : « les projets d'un type », « les lieux d'un niveau », etc.

## Contre

- Un nouveau concept à expliquer aux utilisateurs (filtre de racines ≠ filtre de lignes). Le risque est
  qu'ils confondent les deux.
- Pagination, compteurs et chargement paresseux : le compteur compte les racines, pas les lignes qui
  correspondent. Comportement différent de celui des autres vues arbre.
- Règle subtile (« aucun ancêtre candidat ») : la remontée coûte un peu, et un fait imbriqué sous un fait
  disparaît des racines, ce qui peut surprendre.
- Les enfants ne sont plus filtrés : un utilisateur qui s'attend à un filtre vrai peut être étonné de voir
  des lignes d'autres types.
- Valeur incertaine : le besoin est né d'un seul exemple (« Les faits »), et la règle a changé trois fois en
  discussion.

## Alternatives qui marchent déjà

- **Liste plate filtrée** : un onglet « Faits » en vue liste, avec les colonnes « Fait partie de » et
  « Contient ». Tous les faits, sans hiérarchie.
- **Arbre filtré actuel** : montre où se trouve chaque fait, avec ses ancêtres en contexte.

## Question à poser avant de s'y mettre

« À quoi sert cette vue ? »

- Retrouver et parcourir les faits : la liste plate suffit presque.
- Naviguer d'un fait vers ce qu'il contient : c'est là que le filtre de racines apporte quelque chose.

À reprendre avec un cas d'usage réel côté fouille (qui demande cette vue, et pour quoi faire).
