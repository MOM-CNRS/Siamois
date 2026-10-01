# Qualité de code
[![Quality gate](https://sonarcloud.io/api/project_badges/quality_gate?project=MOM-CNRS_Siamois)](https://sonarcloud.io/summary/new_code?id=MOM-CNRS_Siamois)

# Siamois
SIAMOIS est une plateforme open source de gestion et de structuration des données et de la documentation archéologiques, conçue pour accompagner l’ensemble de la chaîne opératoire, de l’enregistrement sur le terrain à la publication.

Son principal objectif est de proposer un environnement configurable et adaptable aux différents contextes de production des données archéologiques. La structure des bases, les entités, les champs, les vocabulaires, les relations et les droits peuvent être configurés en fonction des besoins d’un projet, d’une opération ou d’une organisation, sans imposer un modèle de données unique. SIAMOIS est ainsi conçu pour prendre en compte la diversité des pratiques de l’archéologie préventive et programmée, en France comme à l’international.

La plateforme permet notamment de structurer et de mettre en relation les données relatives aux unités stratigraphiques, phases, mobiliers, contenants, documents et autres objets de l’étude archéologique. Les relations stratigraphiques peuvent être contrôlées afin de détecter les incohérences logiques et représentées sous forme de graphes, facilitant ainsi l’analyse et la compréhension des séquences stratigraphiques.

SIAMOIS intègre également la gestion de vocabulaires contrôlés et de thésaurus, notamment au moyen d’une connexion à des référentiels externes tels qu’OpenTheso. Cette approche permet d’associer les données produites dans les projets à des référentiels partagés et de favoriser leur normalisation, leur interopérabilité et leur réutilisation.
> Vous pouvez retrouver plus d'informations sur le site [siamois.eu](https://siamois.eu)

# Environnement utilisé
* Java 17
* PostgreSQL 16/17

# Installation
1. Télécharger le fichier JAR de la dernière release
2. Créer une base de données PostgresSQL ainsi qu'un utilisateur pour SIAMOIS
3. Copier le fichier src/main/resources/application.yml dans le même dossier que le fichier JAR
4. Modifier les valeurs du fichier, soit par des variables d'environnement, soit en dur.
## Variables d'environnement
| Variable d'environnement | Description                                                                                                                          |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| SIAMOIS_PORT             | Port sur lequel l'application sera exposée. Par défaut, le port est le `8099`.                                                       |
| CONTEXT_PATH             | Contexte web dans lequel l'application sera accessible. ex: `http://monDomaine.net/contexte`. Par défaut, le contexte est `/siamois` |
| DEFAULT_LANG             | Langue par défaut de l'application. Sans modification, elle est définie sur français (`fr`). La valeur peut être `fr` ou `en`        |
| SIAMOIS_ADMIN_LOGIN      | Nom d'utilisateur du superadministrateur de l'instance.                                                                              |
| SIAMOIS_ADMIN_EMAIL      | Adresse email du superadministrateur de l'instance.                                                                                  |
| SIAMOIS_DOCUMENTS_PATH   | Indique le dossier où les documents seront stockés.                                                                                  |
| SIAMOIS_JWT_SECRET       | Valeur secrète du Json Web Token. **À remplacer par une clé d'au moins 256 bits**.                                                   |
| DB_URL                   | Adresse JDBC permettant de se connecter à la base de données.                                                                        |
| DB_USERNAME              | Nom d'utilisateur pour l'instance sur la base de données.                                                                            |
| DB_PASSWORD              | Mot de passe de l'utilisateur de l'instance sur la base de données.                                                                  |
| SMTP_AUTH_ENABLED        | Booleen définissant si l'authentification est activée sur le serveur SMTP. Par défaut à `true`                                       |
| EMAIL_SENDER_ADDRESS     | Adresse mail qui sera l'adresse expéditrice des mails.                                                                               |
| SMTP_HOST                | Adresse du serveur SMTP                                                                                                              |
| SMTP_PORT                | Port du serveur SMTP                                                                                                                 |
| SMTP_USERNAME            | Nom d'utilisateur pour se connecter au serveur SMTP                                                                                  |
| SMTP_PASSWORD            | Mot de passe du serveur SMTP                                                                                                         |


# Démarrage de l'application
Une fois les variables renseignées, et que le jar et le fichier application.yaml se trouvent dans le même dossier, il faut exécuter :
```sh
java -jar siamois.jar
```

# Profils

Voici les profils existants :

| Profil    | Description                                                                                                        |
|-----------|--------------------------------------------------------------------------------------------------------------------|
| log-email | Lorsque ce profil est spécifié, les emails ne sont plus envoyés par le serveur SMTP mais sont passés dans les logs |

Pour spécifier les profils lors de l'exécution du JAR, il faut utiliser le paramètre `Dspring.profiles.active`
```shell
java -jar -Dspring.profiles.active=log-email siamois.jar
```



# Auteurs
* [Grégory BLIAULT](https://github.com/gregblt)
* [Julien LINGET](https://github.com/DvLogys)
* [Miled ROUSSET](https://github.com/miledrousset)
* [Firas GABSI](https://github.com/Firas8)

## Crédits

**Conceptualisation et conception:** Consortium SIAMOIS

**Développement :** Grégory Bliault, Julien Linget, Miled Rousset, Firas Gabsi

**Design :** Éric Lacombe et César Lacombe


# License
Le projet SIAMOIS est distribué sous license [CeCILL_C](https://cecill.info/licences/Licence_CeCILL-C_V1-en.html), license libre de droit français compatible avec la license GNU GPL.
