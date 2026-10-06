# Plugin QGIS SIAMOIS (v1, expérimental)

Charge un projet SIAMOIS dans un **GeoPackage local** (3 couches : projet, unités d'enregistrement, mobilier),
permet l'édition classique QGIS ou via la fiche, puis **synchronise** vers l'API. Voir
[`docs/CONCEPTION_CHARGEMENT_COUCHES.md`](docs/CONCEPTION_CHARGEMENT_COUCHES.md).

## Installer
- Dev : lier `siamois_qgis/` dans le dossier des plugins QGIS (le SDK est trouvé dans `../siamois-sdk-python`).
- Zip : `./build.sh` → `dist/siamois_qgis.zip` (SDK embarqué) → *Extensions ▸ Installer depuis un ZIP*.

## Utiliser
Menu **SIAMOIS** : *Ouvrir un projet…* (connexion, organisation, projet) ▸ éditer ▸ *Ouvrir la fiche* ▸
*Vérifier mes modifications* ▸ *Synchroniser avec SIAMOIS*.

## Tests (sans QGIS)
`python -m unittest discover -s tests -t .` — couvre chargement, détection des changements, envoi, conflits, fichier annexe.
Le code PyQGIS (`core/qgis_layers.py`, `ui/`, `plugin.py`) est compilé mais **n'a pas encore été exécuté dans QGIS**.
