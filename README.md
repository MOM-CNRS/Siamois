# Siamois

[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=MOM-CNRS_Siamois)](https://sonarcloud.io/summary/new_code?id=MOM-CNRS_Siamois)

> SIAMOIS est une base de données dédiée à la gestion de la documentation associée aux données archéologiques tout au long de la chaîne opératoire allant de son enregistrement sur le terrain à sa publication et à son archivage. SIAMOIS contrôle en temps réel la cohérence logique des relations stratigraphiques lors de l’opération de terrain et les représente sous forme de graphes. SIAMOIS gère également le cycle de vie complet des mobiliers archéologiques et vestiges anthropobiologiques pour en assurer le suivi depuis leur découverte (études, analyses, restaurations, etc.).

# Environnement utilisé
* Java 17
* PostgreSQL 16/17

# Installation
1. Télécharger le fichier JAR de la dernière release
2. Créer une base de données PostgresSQL ainsi qu'un utilisateur pour SIAMOIS
3. Copier le fichier src/main/resources/application.yml dans le même dossier que le fichier JAR
4. Modifier les valeurs du fichier, soit par des variables d'environnement, soit en dur.
## Variables d'environnement
| Variable d'environnement | Description                                                                                                                     |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| SIAMOIS_PORT             | Port sur lequel l'application sera exposée. Par défaut, le port est le `8099`.                                                    |
| CONTEXT_PATH             | Contexte web dans lequel l'application sera accessible. ex: `http://monDomaine.net/contexte`. Par défaut, le contexte est `/siamois` |
| DEFAULT_LANG             | Langue par défaut de l'application. Sans modification, elle est définie sur français (fr). La valeur peut être fr ou en         |
| SIAMOIS_ADMIN_LOGIN      | Nom d'utilisateur du superadministrateur de l'instance.                                                                         |
| SIAMOIS_ADMIN_EMAIL      | Adresse email du superadministrateur de l'instance.                                                                             |
| SIAMOIS_DOCUMENTS_PATH   | Indique le dossier où les documents seront stockés.                                                                             |
| SIAMOIS_JWT_SECRET       | Valeur secrète du Json Web Token. **À remplacer par une clé d'au moins 256 bits**.                                                  |
| DB_URL                   | Adresse JDBC permettant de se connecter à la base de données.                                                                   |
| DB_USERNAME              | Nom d'utilisateur pour l'instance sur la base de données.                                                                       |
| DB_PASSWORD              | Mot de passe de l'utilisateur de l'instance sur la base de données.                                                             |

# Démarrage de l'application
Une fois les variables renseignées, et que le jar et le fichier application.yaml se trouvent dans le même dossier, il faut exécuter :
```sh
java -jar siamois.jar
```

# Auteurs
* [Miled ROUSSET](https://github.com/miledrousset)
* [Grégory BLIAULT](https://github.com/gregblt)
* [Julien LINGET](https://github.com/DvLogys)

# License
Le projet SIAMOIS est distribué sous license [CeCILL_C](https://cecill.info/licences/Licence_CeCILL-C_V1-en.html), license libre de droit français compatible avec la license GNU GPL.
