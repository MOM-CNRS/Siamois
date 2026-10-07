"""Snake sur la carte actuelle : un calque transparent sur le canevas, piloté au clavier."""

from __future__ import annotations

from qgis.core import Qgis, QgsSettings
from qgis.PyQt.QtCore import QEvent, QObject, Qt, QTimer
from qgis.PyQt.QtWidgets import QAction, QApplication

from .game import CLASSIC, DOWN, LEFT, RIGHT, UP, SnakeGame, build_targets, grid_origin
from .eraser import PointEraser
from .options_dialog import OptionsDialog
from .overlay import SnakeOverlay
from .targets import SnakeOptions, collect_points

CELL = 26
SETTING_BEST = "snake_game/best"
KEYS = {
    Qt.Key_Up: UP, Qt.Key_Down: DOWN, Qt.Key_Left: LEFT, Qt.Key_Right: RIGHT,
    Qt.Key_Z: UP, Qt.Key_S: DOWN, Qt.Key_Q: LEFT, Qt.Key_D: RIGHT,  # AZERTY
    Qt.Key_W: UP, Qt.Key_A: LEFT,  # QWERTY (S et D identiques)
}


class SnakeController(QObject):
    """Une partie sur un canevas : grille calée sur la taille de la vue, clavier capté, minuterie."""

    def __init__(self, iface, on_stop, options=None):
        super().__init__(iface.mainWindow())
        self.iface, self.canvas, self.on_stop = iface, iface.mapCanvas(), on_stop
        self.options = options or SnakeOptions()
        self._finished = False
        self.viewport = self.canvas.viewport()
        self.timer = QTimer(self)
        self.timer.timeout.connect(self._tick)
        self.best = int(QgsSettings().value(SETTING_BEST, 0) or 0)
        self.overlay = None
        self.game = None
        self.eraser = None
        self._seen = 0  # nombre d'entités mangées déjà effacées de l'écran

    def start(self) -> None:
        opt = self.options
        if opt.layer is not None and opt.hide_eaten:
            self.eraser = PointEraser(self.canvas, opt.layer)
        self._new_game()
        self.canvas.installEventFilter(self)
        self.viewport.installEventFilter(self)
        self.canvas.setFocus()

    def stop(self) -> None:
        self.timer.stop()
        self._save_best()
        self._finish()
        self.eraser = None  # rien à restaurer : la couche n'a jamais été modifiée
        for w in (self.canvas, self.viewport):
            w.removeEventFilter(self)
        if self.overlay is not None:
            self.overlay.hide()
            self.overlay.deleteLater()
            self.overlay = None
        self.on_stop()

    # ------------------------------------------------------------------

    def _grid(self):
        return max(8, self.viewport.width() // CELL - 2), max(6, self.viewport.height() // CELL - 3)

    def _new_game(self) -> None:
        cols, rows = self._grid()
        self._finished = False
        targets, labels = {}, {}
        opt = self.options
        if opt.layer is not None:
            points, labels, truncated = collect_points(self.canvas, opt)
            origin = grid_origin(self.viewport.width(), self.viewport.height(), cols, rows, CELL)
            targets = build_targets(points, origin, CELL, cols, rows)
            if truncated:
                self._msg(f"Plus de {len(points)} points : seuls les premiers sont jouables. Zoomez pour en avoir moins.",
                          Qgis.Warning)
            if not targets:
                self._msg("Aucun point de la couche dans la vue actuelle : partie classique (amphores).", Qgis.Warning)
        self.game = SnakeGame(cols, rows, targets=targets, mode=opt.mode, growth_cap=opt.growth_cap)
        self._seen = 0
        if self.eraser is not None:
            # fond = carte rendue une fois sans la couche ; nouvelle partie : tous les points réapparaissent
            QApplication.setOverrideCursor(Qt.WaitCursor)
            try:
                ok = self.eraser.prepare(points)
            finally:
                QApplication.restoreOverrideCursor()
            if not ok:
                self.eraser = None
                self._msg("Impossible de préparer l'effacement des points : ils resteront visibles.", Qgis.Warning)
        if self.overlay is None:
            self.overlay = SnakeOverlay(self.viewport, self.game, CELL, self.best, labels)
        else:
            self.overlay.game, self.overlay.paused, self.overlay.labels = self.game, False, labels
        self.overlay.eraser = self.eraser
        self.overlay.setGeometry(self.viewport.rect())
        self.timer.start(self.game.interval_ms())
        self.overlay.update()

    def _msg(self, text, level=Qgis.Info, duration: int = 8) -> None:
        self.iface.messageBar().pushMessage("Snake", text, level, duration)

    def _finish(self) -> None:
        """Fin de partie : sélectionne dans QGIS les entités mangées (si demandé)."""
        g, opt = self.game, self.options
        if self._finished or g is None or opt.layer is None or g.mode == CLASSIC or not g.eaten:
            return
        self._finished = True
        ids = sorted(set(g.eaten))
        if opt.select_at_end:
            opt.layer.selectByIds(ids)
        self._msg(f"{len(g.eaten)} point(s) mangé(s) sur {g.total}"
                  + (" — sélectionnés dans la couche." if opt.select_at_end else "."), Qgis.Success, 10)

    def _save_best(self) -> None:
        if self.game is not None and self.game.mode == CLASSIC and self.game.score > self.best:
            self.best = self.game.score
            QgsSettings().setValue(SETTING_BEST, self.best)
            if self.overlay is not None:
                self.overlay.best = self.best

    def _tick(self) -> None:
        if self.overlay is None or self.overlay.paused:
            return
        alive = self.game.step()
        if self.eraser is not None and len(self.game.eaten) > self._seen:
            self.eraser.erase(self.game.eaten[self._seen:])  # « mangés » : disparaissent aussitôt de l'écran
            self._seen = len(self.game.eaten)
        if not alive:
            self.timer.stop()
            self._save_best()
            self._finish()
        else:
            self.timer.setInterval(self.game.interval_ms())
        self.overlay.update()

    def _toggle_pause(self) -> None:
        if not self.game.alive:  # rejouer
            self._new_game()
            return
        self.overlay.paused = not self.overlay.paused
        self.overlay.update()

    # ------------------------------------------------------------------

    def eventFilter(self, obj, event):  # noqa: N802 - API Qt
        t = event.type()
        if (self.options.layer is not None and obj is self.viewport and self.overlay is not None
                and t in (QEvent.Wheel, QEvent.MouseButtonPress, QEvent.MouseButtonDblClick)):
            return True  # carte verrouillée : la grille de jeu est calée sur l'emprise de départ
        if t == QEvent.KeyPress and not event.isAutoRepeat():
            key = event.key()
            if key == Qt.Key_Escape:
                self.stop()
                return True
            if key == Qt.Key_Space or key == Qt.Key_P:
                self._toggle_pause()
                return True
            if key in KEYS:
                if self.overlay is not None and self.overlay.paused and self.game.alive:
                    self.overlay.paused = False  # une flèche reprend la partie
                self.game.turn(KEYS[key])
                return True
        elif t == QEvent.KeyPress and event.key() in KEYS:
            return True  # répétition automatique : la carte ne doit pas défiler
        elif t == QEvent.Resize and obj is self.viewport and self.overlay is not None:
            cols, rows = self._grid()
            self.overlay.setGeometry(self.viewport.rect())
            if (cols, rows) != (self.game.cols, self.game.rows):
                self._new_game()  # la grille suit la taille de la carte
        elif t == QEvent.FocusOut and obj is self.canvas and self.overlay is not None and self.game.alive:
            self.overlay.paused = True  # pause si on clique ailleurs
            self.overlay.update()
        return False


class SnakePlugin:
    def __init__(self, iface):
        self.iface = iface
        self.action = None
        self.controller = None

    def initGui(self):
        self.action = QAction("🐍 Snake sur la carte", self.iface.mainWindow())
        self.action.setCheckable(True)
        self.action.toggled.connect(self._toggle)
        self.iface.addPluginToMenu("Snake", self.action)
        self.iface.addToolBarIcon(self.action)

    def unload(self):
        if self.controller is not None:
            self.controller.stop()
        self.iface.removePluginMenu("Snake", self.action)
        self.iface.removeToolBarIcon(self.action)

    def _toggle(self, on: bool) -> None:
        if on and self.controller is None:
            dlg = OptionsDialog(self.iface.mainWindow(), self.iface.activeLayer())
            if dlg.exec_() != OptionsDialog.Accepted:
                self._stopped()
                return
            self.controller = SnakeController(self.iface, self._stopped, dlg.options())
            self.controller.start()
        elif not on and self.controller is not None:
            self.controller.stop()

    def _stopped(self) -> None:
        self.controller = None
        self.action.blockSignals(True)  # évite de relancer _toggle
        self.action.setChecked(False)
        self.action.blockSignals(False)
