# 🐍 Snake sur la carte (plugin QGIS, v0.2)

Un Snake jouable **par-dessus la carte actuelle** : un calque transparent sur le canevas, rien n'est modifié dans le projet.

## Deux façons de jouer (dialogue au lancement)
- **Sur une couche de points** : le terrain est la **vue actuelle** (zoom et déplacement verrouillés). **Aucun faux point** :
  le serpent se promène librement et « mange » les **vrais points de la couche** qu'il croise.
  - Les points mangés **disparaissent de l'écran instantanément, sans clignotement** : au lancement la carte est rendue une fois
    *sans* la couche, puis le calque de jeu recopie un petit carré de ce fond sur chaque point mangé. QGIS ne redessine rien et la
    couche (données, style) n'est **jamais modifiée** : en quittant, la carte est exactement comme avant. Désactivable.
  - Plusieurs points dans la même case comptent tous ; le serpent **grandit du nombre de points mangés, au plus 10 par case**
    (ou jamais, en mode zen).
  - Un champ au choix (ex. identifiant) s'affiche pour le dernier point mangé ; à la fin les points mangés peuvent être
    **sélectionnés** dans la couche.
  - Victoire quand tous les points de la vue sont mangés ; au-delà de 3000 points, seuls les premiers sont jouables (zoomez).
  - Le carré effacé fait ~28 px : un symbole plus grand garderait un liseré visible ; les étiquettes de la couche restent affichées.
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
