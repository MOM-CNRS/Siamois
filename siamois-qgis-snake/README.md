# 🐍 Snake sur la carte (plugin QGIS, v0.1)

Un Snake jouable **par-dessus la carte actuelle** : un calque transparent sur le canevas, rien n'est modifié dans le projet.

## Deux façons de jouer (dialogue au lancement)
- **Sur une couche de points** : le terrain est la **vue actuelle** (zoom et déplacement verrouillés), les cibles sont les entités
  de la couche visibles dans l'emprise (cercles orange).
  - *Tout manger* : toutes les cibles sont allumées ; victoire quand elles sont toutes mangées.
  - *Un point à la fois* : une cible rouge au hasard.
  - *Croissance* : à chaque point / tous les 3 / jamais (mode zen).
  - Un champ au choix (ex. identifiant) s'affiche pour le dernier point mangé ; à la fin les entités mangées peuvent être **sélectionnées**
    dans la couche (aucune donnée n'est modifiée).
  - Plusieurs points dans la même case sont mangés d'un coup ; au-delà de 3000 points, seuls les premiers sont jouables (zoomez).
- **Sans couche** : une amphore 🏺 apparaît au hasard (Snake classique ; record mémorisé).

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
