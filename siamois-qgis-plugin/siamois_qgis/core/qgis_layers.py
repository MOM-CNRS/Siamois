"""Couches QGIS : écriture du GeoPackage, widgets natifs (listes déroulantes, contraintes), lecture des modifs.

Dépend de PyQGIS (QGIS >= 3.22). La logique métier reste dans loader/syncplan (testables sans QGIS).
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, List, Optional, Tuple

from qgis.core import (
    NULL, QgsAttributeEditorContainer, QgsAttributeEditorField, QgsCoordinateReferenceSystem,
    QgsCoordinateTransformContext, QgsEditFormConfig, QgsEditorWidgetSetup, QgsFeature, QgsField,
    QgsFieldConstraints, QgsGeometry, QgsLayerTreeGroup, QgsProject, QgsVectorFileWriter, QgsVectorLayer, QgsWkbTypes,
)
from qgis.PyQt.QtCore import QVariant

from . import _sdk_import  # noqa: F401
from siamois_sdk.flatten import (
    CHOICE, DATETIME, DECIMAL, INTEGER, MEASUREMENT, ColumnSpec, Vocabularies,
)
from . import log
from .geo import geojson_to_wkt, with_srid
from .loader import LayerData, ProjectBundle, VOCAB_STATUS, status_vocabulary

ID_FIELD = "siamois_id"
REV_FIELD = "siamois_revision"
PROP_DB = "siamois/db"
PROP_KIND = "siamois/kind"
PROP_NAME = "siamois/layer"
EMPTY_LABEL = "(vide)"
WKT_PRECISION = 6


def _qvariant(spec: ColumnSpec):
    if spec.kind == INTEGER:
        return QVariant.Int
    if spec.kind in (DECIMAL, MEASUREMENT):
        return QVariant.Double
    return QVariant.String


def norm_wkt(wkt: Optional[str]) -> Optional[str]:
    """WKT normalisé (même précision des deux côtés de la comparaison)."""
    if not wkt:
        return None
    g = QgsGeometry.fromWkt(wkt)
    return None if g is None or g.isNull() else g.asWkt(WKT_PRECISION)


def _clean(v: Any) -> Any:
    if v is None or v == NULL:
        return None
    return v


# ------------------------------------------------------------------ écriture


def _layer_geometry_type(geoms: List[QgsGeometry]) -> str:
    """Type de géométrie de la couche : « None » (sans géométrie), un type unique, sa version Multi, ou « Geometry »."""
    kinds = set()
    for g in geoms:
        t = g.wkbType()
        flat = QgsWkbTypes.flatType(t)
        kinds.add((QgsWkbTypes.singleType(flat), QgsWkbTypes.hasZ(t)))
    if not kinds:
        return "None"
    if len(kinds) == 1:
        (single, z) = next(iter(kinds))
        multi = any(QgsWkbTypes.isMultiType(g.wkbType()) for g in geoms)
        t = QgsWkbTypes.multiType(single) if multi else single
        if z:
            t = QgsWkbTypes.addZ(t)
        return QgsWkbTypes.displayString(t)
    return "Geometry"


def _memory_layer(ld: LayerData) -> QgsVectorLayer:
    geoms = []
    for r in ld.rows:
        wkt = geojson_to_wkt(r.geom)
        g = QgsGeometry.fromWkt(wkt) if wkt else None
        geoms.append(g if g is not None and not g.isNull() else None)
    gtype = _layer_geometry_type([g for g in geoms if g is not None])
    uri = "None" if gtype == "None" else f"{gtype}?crs=epsg:{ld.srid}"
    mem = QgsVectorLayer(uri, ld.name, "memory")
    pr = mem.dataProvider()
    fields = [QgsField(ID_FIELD, QVariant.String), QgsField(REV_FIELD, QVariant.Int)]
    fields += [QgsField(s.name, _qvariant(s)) for s in ld.specs]
    pr.addAttributes(fields)
    mem.updateFields()
    feats = []
    for r, g in zip(ld.rows, geoms):
        f = QgsFeature(mem.fields())
        f.setAttributes([r.id, r.revision] + [_clean(r.cells.get(s.name)) for s in ld.specs])
        if g is not None and gtype != "None":
            if QgsWkbTypes.isMultiType(mem.wkbType()) and not g.isMultipart():
                g.convertToMultiType()
            f.setGeometry(g)
        feats.append(f)
    pr.addFeatures(feats)
    return mem


def write_geopackage(bundle: ProjectBundle, gpkg_path: str) -> Dict[str, Dict[str, Optional[str]]]:
    """Écrit les 3 couches dans le GeoPackage (recréé). Retourne {layer: {id: wkt normalisé}}."""
    if os.path.exists(gpkg_path):
        os.remove(gpkg_path)
    wkts: Dict[str, Dict[str, Optional[str]]] = {}
    for i, ld in enumerate(bundle.layers):
        mem = _memory_layer(ld)
        opts = QgsVectorFileWriter.SaveVectorOptions()
        opts.driverName = "GPKG"
        opts.layerName = ld.name
        opts.fileEncoding = "UTF-8"
        opts.actionOnExistingFile = (QgsVectorFileWriter.CreateOrOverwriteFile if i == 0
                                     else QgsVectorFileWriter.CreateOrOverwriteLayer)
        res = QgsVectorFileWriter.writeAsVectorFormatV3(mem, gpkg_path, QgsCoordinateTransformContext(), opts)
        log.info(f"GPKG {ld.name}: {len(ld.rows)} ligne(s), type {QgsWkbTypes.displayString(mem.wkbType())}, "
                 f"srid {ld.srid}, résultat {res}")
        if res[0] != QgsVectorFileWriter.NoError:
            raise RuntimeError(f"Écriture GeoPackage impossible ({ld.name}, code {res[0]}, "
                               f"type {QgsWkbTypes.displayString(mem.wkbType())}) : {res[1] or 'aucun détail'}")
        wkts[ld.name] = {f[ID_FIELD]: norm_wkt(f.geometry().asWkt() if f.hasGeometry() else None)
                         for f in mem.getFeatures()}
    return wkts


# ------------------------------------------------------------------ widgets natifs


def _setup(layer: QgsVectorLayer, idx: int, wtype: str, config: Dict[str, Any]) -> None:
    layer.setEditorWidgetSetup(idx, QgsEditorWidgetSetup(wtype, config))


def _value_map(labels: List[str]) -> Dict[str, Any]:
    entries = [{EMPTY_LABEL: ""}] + [{l: l} for l in labels]
    return {"map": entries}


def configure_layer(layer: QgsVectorLayer, specs: List[ColumnSpec], vocabs: Vocabularies, geom_editable: bool) -> None:
    """Listes déroulantes, bornes, dates, champs obligatoires, lecture seule, onglets par panneau."""
    form = layer.editFormConfig()
    fields = layer.fields()
    for hidden in (ID_FIELD, REV_FIELD):
        idx = fields.indexOf(hidden)
        if idx >= 0:
            _setup(layer, idx, "Hidden", {})
            form.setReadOnly(idx, True)
    for s in specs:
        idx = fields.indexOf(s.name)
        if idx < 0:
            continue
        vocab = vocabs.get(s.vocab_code or "")
        if s.kind == CHOICE and vocab is not None and s.editable:
            _setup(layer, idx, "ValueMap", _value_map(vocab.sorted_labels))
        elif s.kind in (INTEGER, DECIMAL, MEASUREMENT) and s.editable:
            big = 1e12
            _setup(layer, idx, "Range", {
                "Min": s.min if s.min is not None else -big, "Max": s.max if s.max is not None else big,
                "Step": 1 if s.kind == INTEGER else 0.01, "Style": "SpinBox", "AllowNull": True,
                "Precision": 0 if s.kind == INTEGER else 3,
                "Suffix": f" {s.unit}" if s.unit else ""})
        elif s.kind == DATETIME and s.editable:
            _setup(layer, idx, "DateTime", {
                "field_iso_format": True, "display_format": "yyyy-MM-dd", "calendar_popup": True,
                "allow_null": True})
        form.setReadOnly(idx, not s.editable)
        if s.required:
            layer.setFieldConstraint(idx, QgsFieldConstraints.ConstraintNotNull, QgsFieldConstraints.ConstraintStrengthSoft)
    _build_tabs(layer, form, specs)
    layer.setEditFormConfig(form)


def _build_tabs(layer: QgsVectorLayer, form: QgsEditFormConfig, specs: List[ColumnSpec]) -> None:
    """Fiche = formulaire QGIS à onglets, un onglet par panneau du formulaire serveur."""
    try:
        layout = QgsEditFormConfig.TabLayout
    except AttributeError:  # QGIS >= 3.30 (enum déplacé)
        from qgis.core import Qgis
        layout = Qgis.AttributeFormLayout.DragAndDrop
    form.setLayout(layout)
    root = form.invisibleRootContainer()
    root.clear()
    panels: Dict[str, List[ColumnSpec]] = {}
    for s in specs:
        panels.setdefault(s.panel or "Général", []).append(s)
    for name, items in panels.items():
        tab = QgsAttributeEditorContainer(name, root)
        for s in items:
            idx = layer.fields().indexOf(s.name)
            if idx >= 0:
                tab.addChildElement(QgsAttributeEditorField(s.name, idx, tab))
        root.addChildElement(tab)


def remove_project_layers(project_name: str, project: Optional[QgsProject] = None) -> None:
    """Retire le groupe d'un projet SIAMOIS (à faire avant de réécrire son GeoPackage : fichier verrouillé sinon)."""
    project = project or QgsProject.instance()
    root = project.layerTreeRoot()
    old = root.findGroup(f"SIAMOIS – {project_name}")
    if old is not None:
        for node in old.findLayers():
            project.removeMapLayer(node.layerId())
        root.removeChildNode(old)


def add_to_project(bundle: ProjectBundle, gpkg_path: str, db_path: str, vocabs: Vocabularies,
                   project: Optional[QgsProject] = None) -> List[QgsVectorLayer]:
    project = project or QgsProject.instance()
    group_name = f"SIAMOIS – {bundle.project_name}"
    remove_project_layers(bundle.project_name, project)
    group = project.layerTreeRoot().insertGroup(0, group_name)
    layers = []
    for ld in bundle.layers:
        layer = QgsVectorLayer(f"{gpkg_path}|layername={ld.name}", ld.title, "ogr")
        if not layer.isValid():
            raise RuntimeError(f"Couche invalide : {ld.name}")
        layer.setCustomProperty(PROP_DB, db_path)
        layer.setCustomProperty(PROP_KIND, ld.kind)
        layer.setCustomProperty(PROP_NAME, ld.name)
        configure_layer(layer, ld.specs, vocabs, ld.geom_editable)
        project.addMapLayer(layer, False)
        group.addLayer(layer)
        layers.append(layer)
    return layers


def siamois_layers(project: Optional[QgsProject] = None) -> List[QgsVectorLayer]:
    project = project or QgsProject.instance()
    return [l for l in project.mapLayers().values()
            if isinstance(l, QgsVectorLayer) and l.customProperty(PROP_DB)]


# ------------------------------------------------------------------ lecture des modifications


def read_current(layer: QgsVectorLayer, specs: List[ColumnSpec]) -> Tuple[Dict[str, Dict[str, Any]], List[Dict[str, Any]]]:
    """(id -> {cells, wkt, geojson, fid}, lignes neuves [{cells, wkt, geojson, fid}] sans identifiant SIAMOIS)."""
    srid = layer.crs().postgisSrid() or 4326
    out: Dict[str, Dict[str, Any]] = {}
    new_rows: List[Dict[str, Any]] = []
    for f in layer.getFeatures():
        sid = _clean(f[ID_FIELD])
        g = f.geometry() if f.hasGeometry() else None
        has_geom = g is not None and not g.isNull()
        row = {
            "cells": {s.name: _clean(f[s.name]) for s in specs},
            "wkt": norm_wkt(g.asWkt()) if has_geom else None,
            "geojson": with_srid(json.loads(g.asJson(8)), srid) if has_geom else None,
            "fid": f.id(),
        }
        if sid is None:
            new_rows.append(row)
        else:
            out[str(sid)] = row
    return out, new_rows


def update_after_sync(layer: QgsVectorLayer, fid: int, revision: Optional[int]) -> None:
    """Met à jour la révision d'une ligne directement dans le fournisseur (sans passer par le tampon d'édition)."""
    if revision is None:
        return
    idx = layer.fields().indexOf(REV_FIELD)
    layer.dataProvider().changeAttributeValues({fid: {idx: revision}})


def apply_server_row(layer: QgsVectorLayer, fid: int, specs: List[ColumnSpec], cells: Dict[str, Any],
                     geom_wkt: Optional[str], revision: Optional[int]) -> None:
    """Résolution « garder le serveur » : réécrit la ligne locale avec la version serveur."""
    attrs = {layer.fields().indexOf(s.name): _clean(cells.get(s.name)) for s in specs}
    if revision is not None:
        attrs[layer.fields().indexOf(REV_FIELD)] = revision
    layer.dataProvider().changeAttributeValues({fid: attrs})
    if geom_wkt is not None:
        g = QgsGeometry.fromWkt(geom_wkt)
        if g is not None and not g.isNull():
            layer.dataProvider().changeGeometryValues({fid: g})
    layer.reload()


def apply_created_row(layer: QgsVectorLayer, fid: int, specs: List[ColumnSpec], siamois_id: str,
                      cells: Dict[str, Any], revision: Optional[int]) -> None:
    """Après création serveur : la ligne locale reçoit son identifiant, sa révision et les valeurs du serveur."""
    attrs = {layer.fields().indexOf(ID_FIELD): siamois_id}
    if revision is not None:
        attrs[layer.fields().indexOf(REV_FIELD)] = revision
    for s in specs:
        idx = layer.fields().indexOf(s.name)
        if idx >= 0:
            attrs[idx] = _clean(cells.get(s.name))
    layer.dataProvider().changeAttributeValues({fid: attrs})
    layer.reload()


def refresh_choice_widget(layer: QgsVectorLayer, spec: ColumnSpec, labels: List[str]) -> None:
    """Met à jour la liste déroulante d'une colonne (ex. nouvelles UE proposées pour le mobilier)."""
    idx = layer.fields().indexOf(spec.name)
    if idx >= 0:
        _setup(layer, idx, "ValueMap", _value_map(labels))
