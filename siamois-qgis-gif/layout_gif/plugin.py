"""Plugin QGIS : exporte une mise en page contenant un cadre image GIF en GIF animé."""

from __future__ import annotations

from qgis.core import Qgis
from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import QAction, QApplication, QProgressDialog

from .core import log
from .core.layout_render import Cancelled, export_gif
from .core.gif_frames import GifReadError
from .ui.export_dialog import ExportDialog

TITLE = "Mise en page → GIF"


class LayoutGifPlugin:
    def __init__(self, iface):
        self.iface = iface
        self.action = None
        self._designer_actions = []

    def initGui(self):
        self.action = QAction("Exporter une mise en page en GIF animé…", self.iface.mainWindow())
        self.action.triggered.connect(lambda: self.run(None))
        self.iface.addPluginToMenu(TITLE, self.action)
        self.iface.addToolBarIcon(self.action)
        self.iface.layoutDesignerOpened.connect(self._designer_opened)
        log.info("Plugin chargé")

    def unload(self):
        try:
            self.iface.layoutDesignerOpened.disconnect(self._designer_opened)
        except TypeError:
            pass
        if self.action is not None:
            self.iface.removePluginMenu(TITLE, self.action)
            self.iface.removeToolBarIcon(self.action)
        for designer, a in self._designer_actions:
            try:
                designer.layoutMenu().removeAction(a)
            except RuntimeError:  # concepteur déjà fermé
                pass
        self._designer_actions.clear()

    def _designer_opened(self, designer):
        """Ajoute « Exporter en GIF animé » au menu Mise en page du concepteur."""
        action = QAction("Exporter en GIF animé…", designer.window())
        action.triggered.connect(lambda: self.run(designer.layout()))
        try:
            designer.layoutMenu().addAction(action)
            self._designer_actions.append((designer, action))
        except AttributeError:
            log.warning("Menu du concepteur indisponible : utilisez le menu Extensions ▸ Mise en page → GIF")

    def _msg(self, text, level=Qgis.Info, duration=10):
        self.iface.messageBar().pushMessage(TITLE, text, level, duration)

    def run(self, layout):
        dlg = ExportDialog(self.iface.mainWindow(), layout)
        if dlg.exec_() != ExportDialog.Accepted:
            return
        target = dlg.current_layout()
        opts = dlg.options()
        progress = QProgressDialog("Préparation…", "Annuler", 0, 100, self.iface.mainWindow())
        progress.setWindowTitle(TITLE)
        progress.setWindowModality(Qt.WindowModal)
        progress.setMinimumDuration(0)

        def on_progress(step, done, total):
            progress.setLabelText(f"{step} ({done}/{total})" if total else step)
            progress.setValue(int(100 * done / total) if total else 0)
            QApplication.processEvents()
            return not progress.wasCanceled()

        QApplication.setOverrideCursor(Qt.WaitCursor)
        try:
            export_gif(target, opts, on_progress)
        except Cancelled:
            self._msg("Export annulé.", Qgis.Warning)
            return
        except GifReadError as exc:
            log.error("Lecture du GIF impossible", exc)
            self._msg(str(exc), Qgis.Critical, 0)
            return
        except Exception as exc:  # noqa: BLE001 - toute erreur doit être montrée à l'utilisateur
            log.error("Échec de l'export GIF", exc)
            self._msg(f"Échec de l'export : {exc} (détails : Messages du journal ▸ SIAMOIS GIF)", Qgis.Critical, 0)
            return
        finally:
            QApplication.restoreOverrideCursor()
            progress.close()
        s = opts.stats
        self._msg(f"✅ GIF créé : {opts.output} — {s['frames']} images, {s['width']}×{s['height']} px, "
                  f"{s['size_bytes'] / 1e6:.1f} Mo", Qgis.Success, 15)
