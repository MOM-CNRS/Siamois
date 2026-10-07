# Plugin QGIS « Mise en page → GIF animé » (v0.1, expérimental)

QGIS n'anime pas un GIF placé dans un **cadre image** d'une mise en page : l'export ne contient qu'une image.
Ce plugin rend la mise en page **une fois par image du GIF** (le reste — carte, titre, légende, échelle — est identique)
et assemble les rendus en un **GIF animé**.

## Installer
`./build.sh` → `dist/layout_gif.zip` → *Extensions ▸ Installer depuis un ZIP* (QGIS ≥ 3.22).

## Utiliser
1. Dans une mise en page, ajoutez un **cadre image** dont la source est un fichier `.gif` animé (local).
2. *Extensions ▸ Mise en page → GIF ▸ Exporter une mise en page en GIF animé…* (ou menu **Mise en page** du concepteur).
3. Choisissez la mise en page, cochez les cadres GIF, la résolution (96 dpi par défaut), les boucles, la durée
   (celle du GIF source par défaut) et le fichier de sortie.

Plusieurs cadres GIF : le GIF de sortie a autant d'images que le plus long ; les plus courts bouclent.
Les sources d'origine des cadres sont restaurées après l'export (même en cas d'erreur ou d'annulation).

## Fonctionnement
`QMovie` (Qt) extrait les images du GIF → pour chaque image, le cadre pointe dessus et la page est rendue
(`QgsLayoutExporter.renderPageToImage`) → quantification en 256 couleurs (Pillow si présent, sinon Qt) → encodeur GIF89a
en Python pur, écrit en flux (`core/gif_writer.py`, testé et relu avec Pillow).

## Limites v1
Une page à la fois ; sources locales uniquement ; pas d'optimisation par différence d'images (fichiers volumineux : gardez un
dpi modéré) ; 256 couleurs par image. Logs : *Messages du journal ▸ SIAMOIS GIF*.

## Tests (sans QGIS)
`python -m unittest discover -s tests -t .` — l'encodeur est relu avec Pillow (images, délais, boucle, pixels, table LZW saturée).
Le code PyQGIS (`plugin.py`, `ui/`, `core/layout_render.py`, `gif_frames.py`, `quantize.py`) est compilé mais n'a pas été exécuté dans QGIS.
