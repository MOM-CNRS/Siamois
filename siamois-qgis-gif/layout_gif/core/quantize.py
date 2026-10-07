"""QImage -> (indices, palette RVB) pour l'encodeur GIF. Pillow si disponible (meilleure qualité), sinon Qt."""

from __future__ import annotations

from typing import Tuple

from qgis.PyQt.QtGui import QImage

try:
    from PIL import Image as PilImage
except ImportError:  # Pillow n'est pas garanti dans QGIS
    PilImage = None


def _strip_padding(raw: bytes, width: int, height: int, bytes_per_line: int, bytes_per_pixel: int) -> bytes:
    row = width * bytes_per_pixel
    if bytes_per_line == row:
        return raw[:row * height]
    return b"".join(raw[y * bytes_per_line:y * bytes_per_line + row] for y in range(height))


def flatten_on_white(image: QImage) -> QImage:
    """Aplatit la transparence sur fond blanc (le GIF de sortie n'a pas de couche alpha)."""
    from qgis.PyQt.QtGui import QColor, QPainter
    out = QImage(image.size(), QImage.Format_RGB32)
    out.fill(QColor(255, 255, 255))
    p = QPainter(out)
    p.drawImage(0, 0, image)
    p.end()
    return out


def quantize(image: QImage) -> Tuple[bytes, bytes, str]:
    """(indices, palette RVB, méthode utilisée). `image` : QImage opaque ou non (aplatie sur blanc)."""
    img = flatten_on_white(image).convertToFormat(QImage.Format_RGB888)
    w, h = img.width(), img.height()
    if PilImage is not None:
        raw = _strip_padding(bytes(img.constBits().asstring(img.sizeInBytes())), w, h, img.bytesPerLine(), 3)
        pil = PilImage.frombytes("RGB", (w, h), raw).quantize(colors=256)
        palette = bytes(pil.getpalette()[:768])
        return pil.tobytes(), palette, "Pillow"
    indexed = img.convertToFormat(QImage.Format_Indexed8)
    table = indexed.colorTable()[:256] or [0xFF000000, 0xFFFFFFFF]
    palette = b"".join(bytes(((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF)) for c in table)
    raw = _strip_padding(bytes(indexed.constBits().asstring(indexed.sizeInBytes())), w, h, indexed.bytesPerLine(), 1)
    return raw, palette, "Qt"
