# Modifier le schéma de la base de données avec Liquibase

Hibernate tourne en `ddl-auto: validate` : il ne crée ni ne modifie plus aucune table, il vérifie seulement
au démarrage que les entités JPA correspondent au schéma. **Toute modification du schéma passe donc par un
changelog Liquibase.** Si une entité change sans changelog associé, l'application refuse de démarrer.

Le point d'entrée est `src/main/resources/changelog/db.changelog.xml`, chargé au démarrage
(`spring.liquibase.change-log`).

> Référence : [How to structure a complex changelog](https://support.liquibase.com/hc/en-us/articles/29383071573659-How-to-Structure-a-Complex-Changelog)

## Arborescence

```
src/main/resources/changelog/
├── db.changelog.xml          # changelog maître : inclut les trois dossiers ci-dessous, dans cet ordre
├── init/                     # création initiale de la base — NE PAS MODIFIER
│   ├── init.changelog.xml
│   └── dll-init.sql
├── pgplsql/                  # fonctions et triggers PL/pgSQL, un fichier .sql par fonction
│   └── identifier_nextval.sql
└── versions/                 # toutes les évolutions du schéma
    └── 2026/
        └── 10/
            └── 2026-10-28-1.changelog.xml
```

## Règles

### Changelogs de `versions/`

- Emplacement et nom : `changelog/versions/AAAA/MM/AAAA-MM-JJ-<n>.changelog.xml`, où `<n>` s'incrémente à
  partir de 1 pour les changelogs créés le même jour.
- Un changelog contient un ou plusieurs `changeSet` ; **un changeSet = un seul changement**
  (une table créée, une colonne ajoutée…). Liquibase exécute chaque changeSet dans une transaction : en
  cas d'échec, seul ce changement est annulé et l'erreur est facile à localiser.
- L'`id` d'un changeSet doit être unique dans tout le projet, et `author` doit porter votre nom ou votre
  identifiant.
- **Ne jamais modifier un changelog déjà poussé sur une autre branche.** Liquibase enregistre une somme de
  contrôle de chaque changeSet exécuté (table `databasechangelog`) ; si le contenu change, le démarrage
  échoue sur toutes les bases où il a déjà tourné. Pour corriger, ajoutez un nouveau changeSet.
- Ne pas toucher au dossier `init/`.

### Procédures PL/pgSQL de `pgplsql/`

Contrairement aux changelogs, ces fichiers **peuvent être modifiés** : ils sont déclarés
`runOnChange:true`, donc rejoués au démarrage dès que leur contenu change. Conséquences :

- Chaque fichier doit commencer par cet en-tête, sans quoi Liquibase l'ignore :

  ```sql
  --liquibase formatted sql
  --changeset siamois:<nomDeLaFonction> runOnChange:true splitStatements:false
  ```

- Les scripts doivent être rejouables sans erreur : `CREATE OR REPLACE FUNCTION`,
  `DROP TRIGGER IF EXISTS` avant `CREATE TRIGGER`, etc.

## Générer un changelog avec IntelliJ IDEA

IntelliJ sait comparer deux bases et produire le changelog correspondant à leur différence. Il faut pour
cela deux bases PostgreSQL locales :

- **BD Ref** : le schéma *avant* votre modification (celui de `main`) ;
- **BD Dev** : le schéma *après* votre modification.

### Configuration (une seule fois)

Dans *Settings > Tools > Database Versioning > Liquibase* :

| Champ             | Valeur                                         |
|-------------------|------------------------------------------------|
| Changeset author  | votre nom et prénom, ou votre nom d'utilisateur      |
| Primary directory | `changelog/versions/#date("yyyy")/#date("MM")` |
| Primary name      | `#date("yyyy-MM-dd")-#increment(1, 1, "0").changelog` |

### Pas à pas

Exemple : ajouter une colonne à `person`.

1. Démarrez l'application sur la **BD Ref** pour qu'elle soit à jour avec les changelogs existants.
2. Ajoutez le champ dans l'entité JPA (Il est possible de faire plusieurs modifs). _À ce stade l'application ne démarre plus : la validation Hibernate
   échoue, c'est normal._
3. Dans l'outil *Database* d'IntelliJ, ajoutez la colonne dans la **BD Dev**, par une requête SQL ou par
   l'interface graphique. Avec une requête, pensez à resynchroniser la source de données ensuite
   (*Refresh*), sinon IntelliJ ne verra pas la colonne.
4. Clic droit sur la BD Dev > *Create Liquibase Changelog*.
5. Choisissez la **BD Dev comme source** et la **BD Ref comme cible** : le changelog décrit ce qu'il faut
   appliquer à Ref pour obtenir Dev.
6. Vérifiez la liste des différences. Glissez dans *Ignore* tout ce qui ne relève pas de votre
   modification, puis validez.
7. Relisez le fichier généré dans `changelog/versions/…` : un changeSet par changement, rien d'inattendu.
8. Redémarrez l'application sur la **BD Ref** : Liquibase applique le nouveau changelog et la validation
   Hibernate doit passer. C'est la preuve que le changelog est complet.

## Pièges courants

- **Entités auditées (Envers)** : une vingtaine d'entités annotées `@Audited` ont une table `<table>_AUD`.
  Ajouter, renommer ou supprimer une colonne sur une telle entité impose le même changement sur la table
  `_AUD`, sinon la validation échoue.
- **Différences parasites** : si la BD Dev contient des données de test ou des objets créés à la main,
  l'outil de comparaison les proposera aussi. Écartez-les à l'étape 6.
- **Changelog déjà appliqué en local** : pour retravailler un changelog qui n'a pas encore quitté votre
  branche, supprimez sa ligne dans `databasechangelog` (et annulez son effet sur le schéma) avant de
  redémarrer, sinon Liquibase signalera une somme de contrôle invalide.
