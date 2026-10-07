"""Calque transparent posé sur la carte actuelle : dessine la grille, le serpent et l'amphore."""

from __future__ import annotations

from qgis.PyQt.QtCore import QRectF, Qt
from qgis.PyQt.QtGui import QColor, QFont, QPainter, QPen
from qgis.PyQt.QtWidgets import QWidget

from .game import CLASSIC, SnakeGame, grid_origin

BODY = QColor(46, 160, 67, 215)
BODY_ALT = QColor(34, 139, 52, 215)
HEAD = QColor(20, 90, 35, 240)
FOOD = QColor(214, 140, 20, 235)
HELP = "↑↓←→ / ZQSD : diriger · Espace : pause · Échap : quitter"


class SnakeOverlay(QWidget):
    """Widget enfant de la vue de la carte : transparent aux clics (la carte reste utilisable à la souris)."""

    def __init__(self, parent: QWidget, game: SnakeGame, cell: int, best: int = 0, labels=None):
        super().__init__(parent)
        self.game, self.cell, self.best = game, cell, best
        self.labels = labels or {}  # fid -> libellé (champ choisi)
        self.eraser = None  # PointEraser : efface les points mangés
        self.paused = False
        self.setAttribute(Qt.WA_TransparentForMouseEvents)
        self.setAttribute(Qt.WA_NoSystemBackground)
        self.setGeometry(parent.rect())
        self.show()
        self.raise_()

    # ------------------------------------------------------------------

    def _origin(self):
        return grid_origin(self.width(), self.height(), self.game.cols, self.game.rows, self.cell)

    def _rect(self, x: int, y: int, pad: float = 1.5) -> QRectF:
        ox, oy = self._origin()
        c = self.cell
        return QRectF(ox + x * c + pad, oy + y * c + pad, c - 2 * pad, c - 2 * pad)

    def paintEvent(self, _event) -> None:
        g = self.game
        p = QPainter(self)
        p.setRenderHint(QPainter.Antialiasing)
        ox, oy = self._origin()
        # cadre de jeu
        p.setPen(QPen(QColor(255, 255, 255, 190), 3))
        p.setBrush(QColor(0, 0, 0, 25))
        p.drawRect(ox - 2, oy - 2, g.cols * self.cell + 4, g.rows * self.cell + 4)
        # points « mangés » : recopie du fond sans la couche par-dessus les symboles
        if self.eraser is not None:
            self.eraser.paint(p, self.size())
        # amphore : uniquement au Snake classique (sans couche). Avec une couche, les cibles sont les vrais
        # points de la carte, que QGIS dessine déjà : aucun faux point n'est ajouté.
        if g.mode == CLASSIC and g.food is not None:
            r = self._rect(*g.food, pad=2)
            p.setPen(Qt.NoPen)
            p.setBrush(FOOD)
            p.drawEllipse(r)
            f = QFont(p.font())
            f.setPixelSize(int(self.cell * 0.7))
            p.setFont(f)
            p.setPen(QColor(255, 255, 255))
            p.drawText(r, Qt.AlignCenter, "🏺")
        # serpent
        p.setPen(QPen(QColor(10, 50, 20, 200), 1))
        for i, (x, y) in enumerate(g.body):
            is_head = i == len(g.body) - 1
            p.setBrush(HEAD if is_head else (BODY if i % 2 else BODY_ALT))
            p.drawRoundedRect(self._rect(x, y), self.cell * 0.3, self.cell * 0.3)
        if g.alive:
            self._eyes(p)
        self._hud(p)
        if self.paused and g.alive:
            self._banner(p, "PAUSE", "Espace pour reprendre")
        elif not g.alive:
            if g.won:
                done = "Tous les points mangés" if g.mode != CLASSIC else "Carte complète"
                self._banner(p, f"BRAVO ! {done}", "Espace pour rejouer · Échap pour quitter")
            else:
                score = f"{len(g.eaten)}/{g.total} points" if g.mode != CLASSIC else f"Score {g.score}"
                self._banner(p, "GAME OVER", f"{score} · Espace pour rejouer · Échap pour quitter")
        p.end()

    def _eyes(self, p: QPainter) -> None:
        g = self.game
        hx, hy = g.head
        r = self._rect(hx, hy)
        dx, dy = g.direction
        cx, cy = r.center().x() + dx * r.width() * 0.2, r.center().y() + dy * r.height() * 0.2
        off = r.width() * 0.2
        px, py = (-dy * off, dx * off)  # perpendiculaire
        p.setPen(Qt.NoPen)
        p.setBrush(QColor(255, 255, 255))
        s = r.width() * 0.16
        for sign in (-1, 1):
            p.drawEllipse(QRectF(cx + sign * px - s, cy + sign * py - s, 2 * s, 2 * s))

    def _hud(self, p: QPainter) -> None:
        f = QFont(p.font())
        f.setPixelSize(13)
        f.setBold(True)
        p.setFont(f)
        g = self.game
        if g.mode == CLASSIC:
            head = f"🐍 Score {g.score}   Record {max(self.best, g.score)}"
        else:
            last = next((self.labels.get(k) for k in reversed(g.last_eaten) if self.labels.get(k)), None)
            head = f"🎯 {len(g.eaten)}/{g.total} points" + (f"   dernier : {last}" if last else "")
        text = f"{head}   ·   {HELP}"
        w = p.fontMetrics().horizontalAdvance(text) + 20
        box = QRectF(10, 10, w, 26)
        p.setPen(Qt.NoPen)
        p.setBrush(QColor(0, 0, 0, 150))
        p.drawRoundedRect(box, 8, 8)
        p.setPen(QColor(255, 255, 255))
        p.drawText(box, Qt.AlignCenter, text)

    def _banner(self, p: QPainter, title: str, sub: str) -> None:
        w, h = min(self.width() - 40, 520), 90
        box = QRectF((self.width() - w) / 2, (self.height() - h) / 2, w, h)
        p.setPen(Qt.NoPen)
        p.setBrush(QColor(0, 0, 0, 170))
        p.drawRoundedRect(box, 14, 14)
        f = QFont(p.font())
        f.setBold(True)
        f.setPixelSize(26)
        p.setFont(f)
        p.setPen(QColor(255, 255, 255))
        p.drawText(QRectF(box.x(), box.y() + 8, box.width(), 40), Qt.AlignCenter, title)
        f.setBold(False)
        f.setPixelSize(13)
        p.setFont(f)
        p.drawText(QRectF(box.x(), box.y() + 50, box.width(), 30), Qt.AlignCenter, sub)
