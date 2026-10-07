"""Extraction des images d'un GIF animé via QMovie (Qt) — aucune dépendance externe."""

from __future__ import annotations

import os
from typing import List, Tuple

from qgis.PyQt.QtGui import QMovie

MAX_FRAMES = 2000  # garde-fou contre les GIF démesurés


class GifReadError(Exception):
    pass


def _open(path: str) -> QMovie:
    if not os.path.isfile(path):
        raise GifReadError(f"Fichier introuvable : {path}")
    mv = QMovie(path)
    if not mv.isValid():
        raise GifReadError(f"GIF illisible : {path}")
    return mv


def frame_count(path: str) -> int:
    """Nombre d'images (1 = GIF non animé)."""
    mv = _open(path)
    n = mv.frameCount()
    if n > 0:
        return n
    mv.jumpToFrame(0)
    n = 1
    while mv.jumpToNextFrame() and mv.currentFrameNumber() != 0 and n < MAX_FRAMES:
        n += 1
    return n


def extract_frames(path: str, out_dir: str) -> Tuple[List[str], List[int]]:
    """Écrit chaque image (composée, avec transparence) en PNG ; retourne (chemins, délais en ms)."""
    mv = _open(path)
    n = mv.frameCount()
    n = n if n > 0 else frame_count(path)
    if n > MAX_FRAMES:
        raise GifReadError(f"GIF trop long ({n} images, maximum {MAX_FRAMES}) : {path}")
    base = os.path.splitext(os.path.basename(path))[0]
    paths: List[str] = []
    delays: List[int] = []
    for i in range(n):
        if not mv.jumpToFrame(i):
            break
        img = mv.currentImage()
        p = os.path.join(out_dir, f"{base}_{i:04d}.png")
        if not img.save(p, "PNG"):
            raise GifReadError(f"Écriture impossible : {p}")
        paths.append(p)
        delays.append(mv.nextFrameDelay())
    if not paths:
        raise GifReadError(f"Aucune image lue : {path}")
    return paths, delays
