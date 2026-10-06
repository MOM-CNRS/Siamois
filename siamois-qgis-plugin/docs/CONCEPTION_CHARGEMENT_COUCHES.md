---
title: Conception du chargement des couches — plugin QGIS SIAMOIS
version: 1.1 (v1 recadrée)
statut: en révision
---

# Plugin QGIS SIAMOIS — conception v1

Révision du document 1.0 après cadrage : **3 tables, une couche par table, pas de style par type**, connexion
utilisateur/mot de passe, tables « à plat » façon tableur, synchronisation manuelle.

## 1. Objectifs v1
1. Se connecter (email + mot de passe), choisir une **organisation** puis un **projet**, l'ouvrir.
2. Obtenir **3 couches** dans un **GeoPackage local** (`siamois_<idProjet>.gpkg`) : liste des éléments + **fiche**.
3. Éditer **attributs et géométrie** comme dans QGIS classique (table d'attributs, outils de numérisation, calculatrice)
   ou via la **fiche** ; les deux modes valident de la même façon.
4. **Synchroniser** à la demande vers SIAMOIS, avec rapport d'erreurs et gestion des conflits.

Hors v1 : suppression d'éléments depuis QGIS, création de projet, relations stratigraphiques, documents, styles par type,
champs de référence éditables (personnes, UE liées, lieux), édition multi-valeurs via widget dédié, fusion champ par champ.

## 2. Couches

| Couche (table GPKG) | Contenu | Géométrie |
|---|---|---|
| `projet` | le projet ouvert (1 ligne) | éditable |
| `unites_enregistrement` | UE du projet | éditable (types mélangés, `GEOMETRY`) ; NULL possible |
| `mobilier` | mobilier du projet | **lecture seule** (l'API ne l'accepte pas) |

Les UE/mobilier **sans géométrie restent dans la même couche** (géométrie NULL) : plus de table séparée.

### 2.1 Colonnes « à plat »
Une colonne par **champ du formulaire serveur** (champs système **et** additionnels), nommée par son **libellé**
(dédoublonné `Libellé (id)` si besoin). Les formulaires dépendant du type, la couche contient l'**union** des champs ;
un champ absent du formulaire du type de la ligne est ignoré (avertissement) à la synchronisation.
Colonnes techniques : `siamois_id`, `siamois_revision` (masquées), `Identifiant complet` (lecture seule), `Statut`.

| Type de champ serveur | Colonne QGIS | Widget natif |
|---|---|---|
| TEXT | texte | texte |
| INTEGER / DECIMAL / MEASUREMENT | entier / réel | **plage** (min/max du champ, unité en suffixe) |
| DATETIME | texte ISO | **sélecteur de date** |
| SELECT_ONE_FROM_FIELD_CODE | **libellé** du concept | **liste déroulante** (ValueMap, entrée « (vide) ») |
| SELECT_MULTIPLE_FROM_FIELD_CODE | libellés séparés par `;` | texte (v2 : widget dédié) |
| Références (personne, UE, lieu…) | libellé | **lecture seule** |
| `Statut` | « En cours / Terminé / Annulé » (« Validé » = lecture seule) | liste déroulante |

Champ obligatoire du formulaire → contrainte **NotNull (souple)**. Champ `readOnly` → lecture seule.

### 2.2 Fiche
La fiche est le **formulaire QGIS natif** de la couche, organisé en **onglets = panneaux du formulaire serveur**
(action « Ouvrir la fiche »). Elle partage colonnes, listes et contraintes avec la table d'attributs.
Limite v1 : les **règles conditionnelles** serveur (`enabledWhen`, `requiredWhen`) ne sont pas évaluées dans QGIS ;
elles sont revérifiées par le serveur (erreur 400 affichée ligne par ligne).

## 3. Chargement
1. `POST /api/v1/auth/login` (JWT, pas de refresh : 401 ⇒ reconnexion ; le mot de passe n'est jamais conservé).
2. `GET /organizations`, `GET /projects?organizationId=` (pagination automatique).
3. En tâche de fond (QgsTask, annulable, progression) : projet (`fields=all`), formulaires
   (`/organizations/{id}/project-types`, `/projects/{id}/recording-unit-types`, `/find-types`), vocabulaires
   (`/projects/{id}/concepts?fieldCode=`, toutes pages), UE et mobilier paginés (`limit=100`, `valuesLimit=200`).
4. Mise à plat : identifiants de vocabulaire → **libellés**.
5. Écriture du GeoPackage + **fichier annexe** `*.siamois.db` (SQLite) contenant la **copie de référence** de chaque
   ligne, le schéma et les vocabulaires. Ajout des couches dans un groupe « SIAMOIS – <projet> ».
6. Recharger un projet écrase le GPKG : confirmation si des modifications ne sont pas synchronisées.

## 4. Édition et synchronisation (inspirées de l'import xlsx)
**Détection** : on compare les cellules actuelles à la copie de référence — pas d'écoute du tampon d'édition. Seules les
cellules modifiées sont traitées (12 = 12,0 n'est pas une modification).

**Résolution des libellés** (comme l'en-tête/valeurs de l'import xlsx) : comparaison normalisée (casse, accents,
espaces) ; libellé **inconnu** ou **ambigu** = erreur, jamais de choix au hasard. Nombres/dates illisibles, hors bornes,
champ obligatoire vidé = erreurs **bloquantes** ; colonne en lecture seule modifiée, champ obligatoire vide inchangé =
**avertissements**. Une cellule **vidée** efface la valeur (action explicite, contrairement à l'import xlsx).

**Envoi** : `PATCH /recording-units/{id}` (réponses par `fieldId`, `geom` GeoJSON + SRID de la couche, `validated`,
`expectedRevision` = `syncRevision` lue), `PATCH /finds/{id}`, `PATCH /projects/{id}`. Multi-valeurs lues de façon
incomplète (`complete:false`) : écriture en `add`/`remove` uniquement.

**Quand ?** Les modifications sont **locales** (GeoPackage) ; l'envoi est une **action manuelle** « Synchroniser » (ou
« Vérifier mes modifications » pour le rapport seul). Un message propose de synchroniser après chaque enregistrement
de couche. Raison : réseau faible sur le terrain, PATCH par modification fragile.

**Création (UE et mobilier)** : une ligne ajoutée dans la couche (sans `siamois_id`) est envoyée par
`POST /recording-units` ou `POST /finds` à la synchronisation. L'**identifiant complet est généré par le serveur** ;
la ligne locale est ensuite réécrite avec l'identifiant, la révision et les valeurs serveur. Règles :
- le **type** (colonne « type », liste déroulante) est obligatoire ; seuls les champs du formulaire **de ce type** sont envoyés ;
- un **mobilier** exige une **UE** (liste déroulante des UE du projet) ; l'UE doit donc exister côté serveur : créer et
  synchroniser l'UE d'abord, puis ses mobiliers (une UE neuve n'a pas d'identifiant avant sa création) ;
- champs obligatoires du formulaire du type = erreurs bloquantes ; une ligne totalement vide est ignorée ;
- la **géométrie** est envoyée pour une UE ; l'API ne l'accepte pas pour un mobilier (avertissement, ignorée) ;
- **type** et **UE** ne sont plus modifiables après la création (modification ignorée avec avertissement).

**Conflits** : UE `409` + `currentRevision` ⇒ tableau « Garder ma version / Garder la version serveur » par ligne.
« Garder ma version » renvoie avec la révision serveur ; « serveur » réécrit la ligne locale. (Le `last_updated`/`source`
du document 1.0 est remplacé par `syncRevision`.) Les projets/mobiliers n'ont pas de contrôle de révision en v1
(dernier écrit gagne).

**Erreurs** : 401 « session expirée » (reconnexion), 403, 404, 400 « erreur de validation : … », 5xx, délai dépassé :
messages en français (SDK `errors.py`), affichés ligne par ligne dans le rapport.

## 5. Architecture
- `siamois-sdk-python` (`siamois_sdk`) : client API, formulaires, mise à plat/résolution — **sans QGIS**, testé.
- `siamois-qgis-plugin` : `core/loader` + `core/syncplan` + `core/store` (purs, testés) ; `core/qgis_layers`, `ui/`,
  `plugin.py` (PyQGIS). Réseau hors fil principal (QgsTask). Packaging : `build.sh` (SDK embarqué dans `vendor/`).
- Compatibilité : QGIS ≥ 3.22.

## 6. Hypothèses à valider sur une instance réelle
1. `fields=all` d'une **liste** renvoie aussi les valeurs des champs **système** (sinon : repli sur le détail par élément).
2. L'écriture d'un champ système passe bien par `answers[fieldId]` (comme la fiche React).
3. `GET /projects?organizationId=` et `/projects/{id}/concepts` couvrent tous les `fieldCode` des formulaires.
4. Géométries d'un même projet dans un SRID unique (sinon : SRID dominant, les autres sont à reprojeter).
5. Écriture MEASUREMENT par nombre brut (unité = celle du champ, confirmé dans `FieldAnswerPatchService`).

## 7. Sécurité
Aucun secret écrit sur disque (URL et email mémorisés dans QgsSettings, jamais le mot de passe ni le jeton).
Le jeton reste en mémoire pour la session QGIS.
