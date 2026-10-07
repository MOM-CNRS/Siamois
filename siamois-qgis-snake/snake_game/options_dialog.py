"""Dialogue de lancement : couche de points, libellé, variante, croissance."""

from __future__ import annotations

from typing import Optional

from qgis.core import QgsProject
from qgis.PyQt.QtWidgets import (
    QCheckBox, QComboBox, QDialog, QDialogButtonBox, QFormLayout, QLabel, QVBoxLayout,
)

from .targets import SnakeOptions, is_point_layer

NO_LAYER = "Aucune (amphores aléatoires)"


class OptionsDialog(QDialog):
    def __init__(self, parent=None, active_layer=None):
        super().__init__(parent)
        self.setWindowTitle("Snake sur la carte")
        self.layer_box = QComboBox()
        self.layer_box.addItem(NO_LAYER, None)
        for layer in QgsProject.instance().mapLayers().values():
            if is_point_layer(layer):
                self.layer_box.addItem(layer.name(), layer.id())
        if active_layer is not None and is_point_layer(active_layer):
            i = self.layer_box.findData(active_layer.id())
            if i >= 0:
                self.layer_box.setCurrentIndex(i)
        self.field_box = QComboBox()
        self.mode_box = QComboBox()
        self.mode_box.addItem("Tout manger : tous les points sont des cibles", "all")
        self.mode_box.addItem("Un point à la fois : une cible allumée au hasard", "single")
        self.grow_box = QComboBox()
        self.grow_box.addItem("Grandit à chaque point mangé", 1)
        self.grow_box.addItem("Grandit tous les 3 points", 3)
        self.grow_box.addItem("Ne grandit jamais (mode zen)", 0)
        self.select_box = QCheckBox("Sélectionner les points mangés à la fin")
        self.select_box.setChecked(True)
        self.hint = QLabel()
        self.hint.setWordWrap(True)

        form = QFormLayout()
        form.addRow("Couche de points", self.layer_box)
        form.addRow("Afficher le champ", self.field_box)
        form.addRow("Règle", self.mode_box)
        form.addRow("Croissance", self.grow_box)
        form.addRow(self.select_box)
        buttons = QDialogButtonBox(QDialogButtonBox.Ok | QDialogButtonBox.Cancel)
        buttons.button(QDialogButtonBox.Ok).setText("Jouer 🐍")
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        lay = QVBoxLayout(self)
        lay.addWidget(QLabel("Le terrain est la vue actuelle de la carte (zoom et déplacement verrouillés pendant la partie)."))
        lay.addLayout(form)
        lay.addWidget(self.hint)
        lay.addWidget(buttons)
        self.layer_box.currentIndexChanged.connect(self._layer_changed)
        self._layer_changed()

    def _layer(self):
        lid = self.layer_box.currentData()
        return QgsProject.instance().mapLayer(lid) if lid else None

    def _layer_changed(self) -> None:
        layer = self._layer()
        self.field_box.clear()
        on = layer is not None
        for w in (self.field_box, self.mode_box, self.grow_box, self.select_box):
            w.setEnabled(on)
        self.hint.setText("" if on else "Sans couche : une amphore apparaît au hasard, comme au Snake classique.")
        if on:
            self.field_box.addItem("(aucun)", None)
            for f in layer.fields():
                self.field_box.addItem(f.name(), f.name())
            for guess in ("fullIdentifier", "Identifiant complet", "name", "nom", "label", "id"):
                i = self.field_box.findText(guess)
                if i >= 0:
                    self.field_box.setCurrentIndex(i)
                    break

    def options(self) -> SnakeOptions:
        return SnakeOptions(
            layer=self._layer(), label_field=self.field_box.currentData(), mode=self.mode_box.currentData(),
            grow_every=self.grow_box.currentData(), select_at_end=self.select_box.isChecked())
