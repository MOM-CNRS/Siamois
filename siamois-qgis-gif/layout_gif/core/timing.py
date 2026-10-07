"""Calendrier des images de sortie (pur, sans QGIS)."""

from __future__ import annotations

from typing import List, Optional, Sequence

DEFAULT_DELAY_MS = 100
MIN_DELAY_MS = 20


def output_frame_count(source_counts: Sequence[int]) -> int:
    """Nombre d'images du GIF de sortie = le GIF source le plus long."""
    counts = [c for c in source_counts if c and c > 0]
    if not counts:
        raise ValueError("aucune image à animer")
    return max(counts)


def source_frame_index(output_index: int, source_count: int) -> int:
    """Un GIF plus court boucle : image `i % n`."""
    return output_index % source_count


def output_delays(source_delays: Sequence[Sequence[int]], n_out: int, fixed_ms: Optional[int] = None) -> List[int]:
    """Délai (ms) de chaque image de sortie.

    `fixed_ms` : durée imposée ; sinon délais du GIF source le plus long (puis bouclés), 100 ms si absents/nuls.
    """
    if fixed_ms:
        return [max(MIN_DELAY_MS, int(fixed_ms))] * n_out
    longest = max(source_delays, key=len, default=[])
    if not longest:
        return [DEFAULT_DELAY_MS] * n_out
    return [max(MIN_DELAY_MS, int(longest[i % len(longest)]) or DEFAULT_DELAY_MS) for i in range(n_out)]
