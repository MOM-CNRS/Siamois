#!/usr/bin/env sh
# Produit dist/snake_game.zip (Extensions ▸ Installer depuis un ZIP).
set -e
cd "$(dirname "$0")"
rm -rf dist build_tmp && mkdir -p dist build_tmp
cp -r snake_game build_tmp/snake_game
find build_tmp -name __pycache__ -prune -exec rm -rf {} +
(cd build_tmp && python3 -m zipfile -c ../dist/snake_game.zip snake_game)
rm -rf build_tmp
echo "dist/snake_game.zip"
