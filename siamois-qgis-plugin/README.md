# Plugin QGIS SIAMOIS (v1, expérimental)

Charge un projet SIAMOIS dans un **GeoPackage local** (3 couches : projet, unités d'enregistrement, mobilier),
permet l'édition classique QGIS ou via la fiche, puis **synchronise** vers l'API. Voir
[`docs/CONCEPTION_CHARGEMENT_COUCHES.md`](docs/CONCEPTION_CHARGEMENT_COUCHES.md).

## Installer
- Dev / mise à jour : `./install_dev.sh` (remplace le dossier installé, SDK à jour inclus dans `vendor/`). **Ne copiez pas `siamois_qgis/` à la main par-dessus l'ancien** : un ancien `vendor/` resterait.
- Zip : `./build.sh` → `dist/siamois_qgis.zip` (SDK embarqué) → *Extensions ▸ Installer depuis un ZIP*.

## Utiliser
Menu **SIAMOIS** : *Ouvrir un projet…* (connexion, organisation, projet) ▸ éditer ▸ *Ouvrir la fiche* ▸
*Vérifier mes modifications* ▸ *Synchroniser avec SIAMOIS*.

**Créer un élément** : ajouter une ligne dans la couche UE (type obligatoire, géométrie optionnelle) ou mobilier (type + UE),
enregistrer la couche, puis synchroniser : l'identifiant est attribué par le serveur. Créer l'UE avant ses mobiliers.

## Tests (sans QGIS)
`python -m unittest discover -s tests -t .` — couvre chargement, détection des changements, envoi, conflits, fichier annexe.
Le code PyQGIS (`core/qgis_layers.py`, `ui/`, `plugin.py`) est compilé mais **n'a pas encore été exécuté dans QGIS**.
