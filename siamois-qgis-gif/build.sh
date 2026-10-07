#!/usr/bin/env sh
# Produit dist/layout_gif.zip (Extensions ▸ Installer depuis un ZIP).
set -e
cd "$(dirname "$0")"
rm -rf dist build_tmp && mkdir -p dist build_tmp
cp -r layout_gif build_tmp/layout_gif
find build_tmp -name __pycache__ -prune -exec rm -rf {} +
(cd build_tmp && python3 -m zipfile -c ../dist/layout_gif.zip layout_gif)
rm -rf build_tmp
echo "dist/layout_gif.zip"
