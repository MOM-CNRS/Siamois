"""Encodeur GIF89a animé en Python pur (sans QGIS, sans Pillow).

Écriture *en flux* : les images sont écrites au fur et à mesure (pas besoin de toutes les garder en mémoire).
Chaque image a sa propre palette locale (jusqu'à 256 couleurs) ; boucle via l'extension NETSCAPE2.0.
"""

from __future__ import annotations

import struct
from typing import BinaryIO, Optional, Union

MIN_DELAY_MS = 20  # en dessous, la plupart des lecteurs forcent 100 ms


def lzw_encode(data: bytes, min_code_size: int) -> bytes:
    """Compression LZW du GIF (codes variables 3..12 bits, code Clear en cas de table pleine)."""
    clear = 1 << min_code_size
    eoi = clear + 1
    first_free = eoi + 1
    out = bytearray()
    bitbuf = 0
    nbits = 0
    code_size = min_code_size + 1
    next_code = first_free
    table: dict = {}

    def emit(code: int) -> None:
        nonlocal bitbuf, nbits
        bitbuf |= code << nbits
        nbits += code_size
        while nbits >= 8:
            out.append(bitbuf & 0xFF)
            bitbuf >>= 8
            nbits -= 8

    emit(clear)
    if data:
        prefix = data[0]
        for b in data[1:]:
            key = (prefix << 8) | b
            code = table.get(key)
            if code is not None:
                prefix = code
                continue
            emit(prefix)
            if next_code < 4096:
                table[key] = next_code
                if next_code == (1 << code_size) and code_size < 12:
                    code_size += 1
                next_code += 1
            else:  # table pleine : Clear, on repart de zéro
                emit(clear)
                table.clear()
                next_code = first_free
                code_size = min_code_size + 1
            prefix = b
        emit(prefix)
    emit(eoi)
    if nbits:
        out.append(bitbuf & 0xFF)
    return bytes(out)


def _sub_blocks(data: bytes) -> bytes:
    out = bytearray()
    for i in range(0, len(data), 255):
        chunk = data[i:i + 255]
        out.append(len(chunk))
        out += chunk
    out.append(0)
    return bytes(out)


def _palette_bits(n_colors: int) -> int:
    """Nombre de bits pour indexer `n_colors` (1..8) : la table doit avoir 2**bits entrées."""
    bits = 1
    while (1 << bits) < n_colors:
        bits += 1
    return bits


class GifWriter:
    """>>> with GifWriter("out.gif", 100, 80, loop=0) as g:
    ...     g.add_frame(indices, rgb_palette, delay_ms=100)
    """

    def __init__(self, target: Union[str, BinaryIO], width: int, height: int, loop: int = 0):
        if not (0 < width < 65536 and 0 < height < 65536):
            raise ValueError("dimensions GIF invalides")
        self.width, self.height, self.loop = width, height, loop
        self._own = isinstance(target, str)
        self._f: BinaryIO = open(target, "wb") if self._own else target
        self.frames = 0
        self._closed = False
        self._f.write(b"GIF89a" + struct.pack("<HHBBB", width, height, 0x00, 0, 0))
        # boucle : 0 = infinie
        self._f.write(b"\x21\xFF\x0BNETSCAPE2.0\x03\x01" + struct.pack("<H", loop) + b"\x00")

    def add_frame(self, indices: bytes, palette: bytes, delay_ms: int = 100, transparent: Optional[int] = None) -> None:
        """`indices` : width*height octets (index de palette, ligne par ligne) ; `palette` : RVB répétés (≤ 256 couleurs)."""
        if len(indices) != self.width * self.height:
            raise ValueError(f"image de {len(indices)} pixels, attendu {self.width * self.height}")
        n_colors = len(palette) // 3
        if not 1 <= n_colors <= 256:
            raise ValueError("palette de 1 à 256 couleurs attendue")
        bits = _palette_bits(max(n_colors, 2))
        table = palette[:n_colors * 3] + b"\x00" * ((1 << bits) * 3 - n_colors * 3)
        delay_cs = max(MIN_DELAY_MS, int(delay_ms)) // 10
        packed = (1 << 2) | (1 if transparent is not None else 0)  # disposition « ne pas disposer » + transparence
        self._f.write(b"\x21\xF9\x04" + struct.pack("<BHB", packed, delay_cs, transparent or 0) + b"\x00")
        self._f.write(b"\x2C" + struct.pack("<HHHHB", 0, 0, self.width, self.height, 0x80 | (bits - 1)))
        self._f.write(table)
        min_code = max(2, bits)
        self._f.write(bytes([min_code]) + _sub_blocks(lzw_encode(indices, min_code)))
        self.frames += 1

    def close(self) -> None:
        if not self._closed:
            self._f.write(b"\x3B")
            if self._own:
                self._f.close()
            self._closed = True

    def __enter__(self) -> "GifWriter":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()
