#!/usr/bin/env sh
# Installe le plugin (avec le SDK à jour dans vendor/) dans le profil QGIS, en REMPLAÇANT l'ancienne version.
# Usage : ./install_dev.sh [dossier_plugins_QGIS]
set -e
cd "$(dirname "$0")"
if [ -n "$1" ]; then DEST="$1"
elif [ "$(uname)" = "Darwin" ]; then DEST="$HOME/Library/Application Support/QGIS/QGIS3/profiles/default/python/plugins"
else DEST="$HOME/.local/share/QGIS/QGIS3/profiles/default/python/plugins"; fi
[ -d "$DEST" ] || { echo "Dossier introuvable : $DEST (passez-le en argument)"; exit 1; }
rm -rf "$DEST/siamois_qgis"
cp -R siamois_qgis "$DEST/siamois_qgis"
mkdir -p "$DEST/siamois_qgis/vendor"
cp -R ../siamois-sdk-python/siamois_sdk "$DEST/siamois_qgis/vendor/siamois_sdk"
find "$DEST/siamois_qgis" -name __pycache__ -prune -exec rm -rf {} +
echo "Installé dans $DEST/siamois_qgis (SDK $(grep -o '__version__ = "[^"]*"' "$DEST/siamois_qgis/vendor/siamois_sdk/__init__.py"))"
echo "Redémarrez QGIS."
