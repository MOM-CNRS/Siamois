"""Cibles = entités d'une couche de points visibles dans l'emprise de la carte (convertis en pixels)."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from qgis.core import (
    Qgis, QgsCoordinateTransform, QgsFeatureRequest, QgsGeometry, QgsPointXY, QgsProject, QgsVectorLayer, QgsWkbTypes,
)

MAX_TARGETS = 3000  # au-delà, la grille ne serait plus jouable


@dataclass
class SnakeOptions:
    layer: Optional[QgsVectorLayer] = None  # None : amphores aléatoires
    label_field: Optional[str] = None
    mode: str = "all"  # "all" (tout manger) | "single" (une cible à la fois)
    grow_every: int = 1  # 1 : chaque case mangée ; 3 : tous les 3 ; 0 : jamais
    select_at_end: bool = True


def is_point_layer(layer) -> bool:
    if not isinstance(layer, QgsVectorLayer):
        return False
    try:
        return layer.geometryType() == Qgis.GeometryType.Point
    except AttributeError:  # QGIS < 3.30
        return layer.geometryType() == QgsWkbTypes.PointGeometry


def collect_points(canvas, options: SnakeOptions) -> Tuple[List[Tuple[Any, float, float]], Dict[Any, str], bool]:
    """([(fid, x_px, y_px)], {fid: libellé}, tronqué) pour les entités de la couche dans l'emprise visible."""
    layer = options.layer
    ctx = QgsProject.instance().transformContext()
    canvas_crs = canvas.mapSettings().destinationCrs()
    to_layer = QgsCoordinateTransform(canvas_crs, layer.crs(), ctx)
    to_canvas = QgsCoordinateTransform(layer.crs(), canvas_crs, ctx)
    m2p = canvas.getCoordinateTransform()
    request = QgsFeatureRequest().setFilterRect(to_layer.transformBoundingBox(canvas.extent()))
    points: List[Tuple[Any, float, float]] = []
    labels: Dict[Any, str] = {}
    truncated = False
    for feat in layer.getFeatures(request):
        geom: QgsGeometry = feat.geometry()
        if geom is None or geom.isNull():
            continue
        if QgsWkbTypes.geometryType(geom.wkbType()) == QgsWkbTypes.PointGeometry:
            pts = geom.asMultiPoint() if geom.isMultipart() else [geom.asPoint()]
        else:
            pts = [geom.centroid().asPoint()]
        if options.label_field:
            v = feat[options.label_field]
            labels[feat.id()] = "" if v is None or str(v) == "NULL" else str(v)
        for p in pts:
            px = m2p.transform(to_canvas.transform(QgsPointXY(p)))
            points.append((feat.id(), px.x(), px.y()))
        if len(points) >= MAX_TARGETS:
            truncated = True
            break
    return points, labels, truncated
