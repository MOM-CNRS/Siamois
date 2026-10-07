# 🐍 Snake sur la carte (plugin QGIS, v0.1)

Un Snake jouable **par-dessus la carte actuelle** : un calque transparent sur le canevas, rien n'est modifié dans le projet.
Le serpent ramasse des 🏺 (amphores) ; il accélère tous les 3 points ; la grille s'adapte à la taille de la vue.

## Installer
`./build.sh` → `dist/snake_game.zip` → *Extensions ▸ Installer depuis un ZIP* (QGIS ≥ 3.22). Bouton **🐍** dans la barre d'outils
ou menu *Extensions ▸ Snake*.

## Jouer
Flèches ou **ZQSD** (AZERTY) / **WASD** : diriger · **Espace** (ou P) : pause / rejouer · **Échap** : quitter.
Perdre le focus de la carte met le jeu en pause. Les murs sont mortels ; remplir toute la grille gagne la partie. Le record est
mémorisé dans les réglages QGIS.

## Tests (sans QGIS)
`python -m unittest discover -s tests -t .` — logique du jeu (déplacement, virages sans demi-tour, croissance, murs, morsure,
case libérée par la queue, victoire, vitesse). Le calque et le clavier (PyQGIS) sont compilés mais n'ont pas été exécutés dans QGIS.
