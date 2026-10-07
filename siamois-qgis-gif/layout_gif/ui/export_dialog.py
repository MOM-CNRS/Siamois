"""Dialogue d'export : mise en page, cadres GIF détectés, DPI, boucle, vitesse, fichier de sortie."""

from __future__ import annotations

import os
from typing import List, Optional

from qgis.core import QgsLayout, QgsProject
from qgis.PyQt.QtCore import Qt
from qgis.PyQt.QtWidgets import (
    QCheckBox, QComboBox, QDialog, QDialogButtonBox, QFileDialog, QFormLayout, QHBoxLayout, QLabel, QLineEdit,
    QPushButton, QSpinBox, QTableWidget, QTableWidgetItem, QVBoxLayout,
)

from ..core.layout_render import ExportOptions, GifPicture, find_gif_pictures


class ExportDialog(QDialog):
    def __init__(self, parent=None, layout: Optional[QgsLayout] = None):
        super().__init__(parent)
        self.setWindowTitle("Exporter la mise en page en GIF animé")
        self.resize(640, 460)
        self._pictures: List[GifPicture] = []

        self.layouts = QComboBox()
        for l in QgsProject.instance().layoutManager().printLayouts():
            self.layouts.addItem(l.name())
        if layout is not None:
            i = self.layouts.findText(layout.name())
            if i >= 0:
                self.layouts.setCurrentIndex(i)
        self.layouts.currentIndexChanged.connect(self._reload)

        self.table = QTableWidget(0, 3)
        self.table.setHorizontalHeaderLabels(["Animer", "Cadre image", "Images"])
        self.table.horizontalHeader().setStretchLastSection(False)
        self.table.setColumnWidth(0, 60)
        self.table.setColumnWidth(1, 360)
        self.info = QLabel()
        self.info.setWordWrap(True)

        self.dpi = QSpinBox()
        self.dpi.setRange(30, 600)
        self.dpi.setValue(96)
        self.page = QSpinBox()
        self.page.setRange(1, 999)
        self.loop = QSpinBox()
        self.loop.setRange(0, 65535)
        self.loop.setSpecialValueText("infinie")
        self.fixed = QCheckBox("Durée fixe par image")
        self.delay = QSpinBox()
        self.delay.setRange(20, 10000)
        self.delay.setValue(100)
        self.delay.setSuffix(" ms")
        self.delay.setEnabled(False)
        self.fixed.toggled.connect(self.delay.setEnabled)
        self.output = QLineEdit()
        browse = QPushButton("…")
        browse.clicked.connect(self._browse)
        out_row = QHBoxLayout()
        out_row.addWidget(self.output)
        out_row.addWidget(browse)

        form = QFormLayout()
        form.addRow("Mise en page", self.layouts)
        form.addRow("Résolution", self.dpi)
        form.addRow("Page", self.page)
        form.addRow("Boucles", self.loop)
        form.addRow(self.fixed, self.delay)
        form.addRow("Fichier GIF", out_row)

        self.buttons = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        self.buttons.button(QDialogButtonBox.Ok).setText("Exporter")
        self.buttons.accepted.connect(self._validate)
        self.buttons.rejected.connect(self.reject)

        lay = QVBoxLayout(self)
        lay.addLayout(form)
        lay.addWidget(QLabel("Cadres image dont la source est un GIF animé :"))
        lay.addWidget(self.table)
        lay.addWidget(self.info)
        lay.addWidget(self.buttons)
        self._reload()

    # ------------------------------------------------------------------

    def current_layout(self) -> Optional[QgsLayout]:
        # On ne stocke pas l'objet dans le combo : currentData() le renverrait typé QGraphicsScene.
        name = self.layouts.currentText()
        return QgsProject.instance().layoutManager().layoutByName(name) if name else None

    def _reload(self) -> None:
        layout = self.current_layout()
        self.table.setRowCount(0)
        self._pictures = find_gif_pictures(layout) if layout is not None else []
        for p in self._pictures:
            r = self.table.rowCount()
            self.table.insertRow(r)
            box = QTableWidgetItem()
            box.setFlags(Qt.ItemIsUserCheckable | Qt.ItemIsEnabled)
            box.setCheckState(Qt.Checked)
            self.table.setItem(r, 0, box)
            name = QTableWidgetItem(f"{p.label} — {os.path.basename(p.path)}")
            name.setToolTip(p.path)
            name.setFlags(Qt.ItemIsEnabled)
            self.table.setItem(r, 1, name)
            n = QTableWidgetItem(str(p.frames))
            n.setFlags(Qt.ItemIsEnabled)
            self.table.setItem(r, 2, n)
        if layout is None:
            self.info.setText("Aucune mise en page dans le projet.")
        elif not self._pictures:
            self.info.setText("Aucun cadre image avec un GIF animé local dans cette mise en page.")
        else:
            self.info.setText("")
        if layout is not None and not self.output.text():
            self.output.setText(os.path.join(os.path.expanduser("~"), f"{layout.name()}.gif"))

    def _browse(self) -> None:
        path, _ = QFileDialog.getSaveFileName(self, "Enregistrer le GIF", self.output.text(), "GIF (*.gif)")
        if path:
            self.output.setText(path if path.lower().endswith(".gif") else path + ".gif")

    def _validate(self) -> None:
        if not self.selected_pictures():
            self.info.setText("Cochez au moins un cadre GIF à animer.")
            return
        if not self.output.text().strip():
            self.info.setText("Choisissez un fichier de sortie.")
            return
        self.accept()

    def selected_pictures(self) -> List[GifPicture]:
        return [p for r, p in enumerate(self._pictures) if self.table.item(r, 0).checkState() == Qt.Checked]

    def options(self) -> ExportOptions:
        out = self.output.text().strip()
        return ExportOptions(
            output=out if out.lower().endswith(".gif") else out + ".gif", pictures=self.selected_pictures(),
            dpi=self.dpi.value(), page=self.page.value() - 1, loop=self.loop.value(),
            fixed_delay_ms=self.delay.value() if self.fixed.isChecked() else None)
