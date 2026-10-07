"""Rendu d'une mise en page image par image : le cadre « image » prend successivement chaque image du GIF."""

from __future__ import annotations

import os
import tempfile
from dataclasses import dataclass, field
from typing import Callable, List, Optional

from qgis.core import QgsLayout, QgsLayoutExporter, QgsLayoutItemPicture
from qgis.PyQt.QtCore import QSize

from . import log
from .gif_frames import GifReadError, extract_frames, frame_count
from .gif_writer import GifWriter
from .quantize import quantize
from .timing import output_delays, output_frame_count, source_frame_index

Progress = Callable[[str, int, int], bool]  # (étape, fait, total) -> False pour annuler


class Cancelled(Exception):
    pass


@dataclass
class GifPicture:
    item: QgsLayoutItemPicture
    path: str
    frames: int
    label: str


def find_gif_pictures(layout: QgsLayout) -> List[GifPicture]:
    """Cadres image dont la source est un fichier GIF local de plus d'une image."""
    found: List[GifPicture] = []
    for item in layout.items():
        if not isinstance(item, QgsLayoutItemPicture):
            continue
        path = item.picturePath() or ""
        if not path.lower().endswith(".gif") or not os.path.isfile(path):
            continue
        try:
            n = frame_count(path)
        except GifReadError:
            continue
        if n > 1:
            found.append(GifPicture(item, path, n, item.displayName() or item.id() or os.path.basename(path)))
    return found


@dataclass
class ExportOptions:
    output: str
    pictures: List[GifPicture]
    dpi: int = 96
    page: int = 0
    loop: int = 0  # 0 = infinie
    fixed_delay_ms: Optional[int] = None  # None : délais du GIF source
    stats: dict = field(default_factory=dict)


def export_gif(layout: QgsLayout, opts: ExportOptions, progress: Optional[Progress] = None) -> str:
    """Produit le GIF animé. Les sources d'origine des cadres sont toujours restaurées."""
    if not opts.pictures:
        raise ValueError("Aucun cadre GIF sélectionné.")
    originals = [(p.item, p.item.picturePath()) for p in opts.pictures]
    writer: Optional[GifWriter] = None
    try:
        with tempfile.TemporaryDirectory(prefix="layout_gif_") as tmp:
            sources = []
            for n, p in enumerate(opts.pictures):
                if progress and progress(f"Lecture de {p.label}", n, len(opts.pictures)) is False:
                    raise Cancelled()
                sub = os.path.join(tmp, str(n))
                os.makedirs(sub)
                frames, delays = extract_frames(p.path, sub)
                log.info(f"{p.label}: {len(frames)} image(s), délais {delays[:5]}…")
                sources.append((p, frames, delays))

            n_out = output_frame_count([len(f) for _, f, _ in sources])
            delays_out = output_delays([d for _, _, d in sources], n_out, opts.fixed_delay_ms)
            exporter = QgsLayoutExporter(layout)
            method = ""
            for i in range(n_out):
                if progress and progress("Rendu de la mise en page", i, n_out) is False:
                    raise Cancelled()
                for p, frames, _ in sources:
                    p.item.setPicturePath(frames[source_frame_index(i, len(frames))])
                    p.item.refresh()
                img = exporter.renderPageToImage(opts.page, QSize(), opts.dpi)
                if img.isNull():
                    raise RuntimeError(f"Rendu impossible de la page {opts.page + 1} (image {i + 1}/{n_out}).")
                indices, palette, method = quantize(img)
                if writer is None:
                    writer = GifWriter(opts.output, img.width(), img.height(), opts.loop)
                    log.info(f"GIF {img.width()}×{img.height()} px, {n_out} image(s), quantification : {method}")
                writer.add_frame(indices, palette, delays_out[i])
            writer.close()
            opts.stats = {"frames": n_out, "width": writer.width, "height": writer.height, "method": method,
                          "size_bytes": os.path.getsize(opts.output)}
            log.info(f"GIF écrit : {opts.output} ({opts.stats})")
            writer = None
            return opts.output
    except BaseException:
        if writer is not None:
            writer.close()
            try:
                os.remove(opts.output)  # pas de fichier tronqué
            except OSError:
                pass
        raise
    finally:
        for item, path in originals:  # restaure la mise en page, quoi qu'il arrive
            item.setPicturePath(path)
            item.refresh()
