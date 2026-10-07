"""« Manger » les vrais points sans jamais redessiner la carte.

Au lancement, la carte est rendue **une seule fois sans la couche de points** (image de fond). Quand un point est
mangé, le calque de jeu recopie par-dessus un petit carré de ce fond : le point disparaît instantanément, sans que
QGIS ait à redessiner la couche (donc plus de clignotement). La couche et son style ne sont jamais modifiés.
"""

from __future__ import annotations

from typing import Dict, Iterable, List, Optional, Set, Tuple

from qgis.core import QgsMapRendererParallelJob, QgsMapSettings, QgsVectorLayer
from qgis.PyQt.QtCore import QRectF, QSize
from qgis.PyQt.QtGui import QImage, QPainter

HALF = 14  # demi-côté (pixels) du carré effacé autour d'un point : couvre un symbole d'environ 5 mm


class PointEraser:
    def __init__(self, canvas, layer: QgsVectorLayer):
        self.canvas, self.layer = canvas, layer
        self.background: Optional[QImage] = None
        self._key = None
        self._points: Dict[int, List[Tuple[float, float]]] = {}
        self._erased: Set[int] = set()

    def prepare(self, points: Iterable[Tuple[int, float, float]]) -> bool:
        """Rend la carte sans la couche (une fois par taille/emprise de vue). Retourne False si le rendu a échoué."""
        self._points = {}
        for fid, x, y in points:
            self._points.setdefault(fid, []).append((x, y))
        self._erased.clear()
        settings = QgsMapSettings(self.canvas.mapSettings())
        key = (settings.outputSize().width(), settings.outputSize().height(), settings.extent().toString(),
               tuple(l.id() for l in settings.layers()))
        if self.background is not None and key == self._key:
            return True  # même vue : le fond est déjà bon (rejouer ne coûte rien)
        settings.setLayers([l for l in settings.layers() if l.id() != self.layer.id()])
        job = QgsMapRendererParallelJob(settings)
        job.start()
        job.waitForFinished()
        image = job.renderedImage()
        if image is None or image.isNull():
            self.background = None
            return False
        self.background, self._key = QImage(image), key
        return True

    def erase(self, fids: Iterable[int]) -> None:
        self._erased.update(fids)

    def clear(self) -> None:
        """Nouvelle partie : tous les points réapparaissent."""
        self._erased.clear()

    def paint(self, painter: QPainter, view_size: QSize) -> None:
        """À appeler dans le paintEvent du calque, avant le serpent."""
        if self.background is None or not self._erased:
            return
        dpr = self.background.width() / max(1, view_size.width())
        h = HALF
        for fid in self._erased:
            for x, y in self._points.get(fid, ()):
                painter.drawImage(QRectF(x - h, y - h, 2 * h, 2 * h), self.background,
                                  QRectF((x - h) * dpr, (y - h) * dpr, 2 * h * dpr, 2 * h * dpr))

    @property
    def active(self) -> bool:
        return self.background is not None
