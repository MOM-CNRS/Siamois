"""Logique du Snake (pure, sans QGIS ni Qt) : grille, déplacement, nourriture, collisions."""

from __future__ import annotations

import random
from collections import deque
from typing import Any, Deque, Dict, Iterable, List, Optional, Tuple

Cell = Tuple[int, int]  # (colonne, ligne)
UP, DOWN, LEFT, RIGHT = (0, -1), (0, 1), (-1, 0), (1, 0)


CLASSIC, ALL, SINGLE = "classic", "all", "single"


class SnakeGame:
    """Modes : *classic* (nourriture aléatoire) ; avec `targets` (cellule -> clés d'entités) :
    *all* (tout manger) ou *single* (une cible allumée à la fois). `grow_every` : 1 = grandit à chaque
    cellule mangée, n = tous les n, 0 = jamais."""

    def __init__(self, cols: int, rows: int, *, wrap: bool = False, seed: Optional[int] = None, start_length: int = 3,
                 targets: Optional[Dict[Cell, List[Any]]] = None, mode: str = ALL, grow_every: int = 1):
        if cols < 4 or rows < 4:
            raise ValueError("grille trop petite (minimum 4×4)")
        self.cols, self.rows, self.wrap = cols, rows, wrap
        self._rnd = random.Random(seed)
        self.start_length = min(start_length, cols - 1)
        self.grow_every = grow_every
        self._targets_init = {c: list(k) for c, k in (targets or {}).items()
                              if 0 <= c[0] < cols and 0 <= c[1] < rows and k}
        self.mode = mode if self._targets_init else CLASSIC
        self.reset()

    def reset(self) -> None:
        y = self.rows // 2
        x0 = max(self.start_length - 1, self.cols // 2 - self.start_length // 2)
        # la tête est en dernier ; le serpent part vers la droite
        self.body: Deque[Cell] = deque((x0 - (self.start_length - 1) + i, y) for i in range(self.start_length))
        self.direction: Cell = RIGHT
        self._queue: List[Cell] = []
        self.score = 0
        self.alive = True
        self.won = False
        self.food: Optional[Cell] = None
        self.remaining: Dict[Cell, List[Any]] = {c: list(k) for c, k in self._targets_init.items()}
        self.total = sum(len(k) for k in self.remaining.values())
        self.eaten: List[Any] = []  # clés des entités mangées, dans l'ordre
        self.last_eaten: List[Any] = []
        self._foods = 0
        for cell in list(self.body):  # cibles sous le serpent au départ : déjà mangées
            if self.mode != CLASSIC and cell in self.remaining:
                self._consume(cell)
        self._place_food()

    # ------------------------------------------------------------------ état

    @property
    def head(self) -> Cell:
        return self.body[-1]

    @property
    def length(self) -> int:
        return len(self.body)

    def turn(self, direction: Cell) -> None:
        """Mémorise un virage (file de 2 max) ; demi-tour et répétition ignorés."""
        last = self._queue[-1] if self._queue else self.direction
        if direction == last or (direction[0] + last[0] == 0 and direction[1] + last[1] == 0):
            return
        if len(self._queue) < 2:
            self._queue.append(direction)

    def _consume(self, cell: Cell) -> None:
        keys = self.remaining.pop(cell)
        self.eaten.extend(keys)
        self.last_eaten = keys
        self.score += len(keys)

    def _place_food(self) -> None:
        if self.mode == ALL:
            self.food = None
            if not self.remaining:
                self.won, self.alive = True, False
            return
        if self.mode == SINGLE:
            if not self.remaining:
                self.food, self.won, self.alive = None, True, False
                return
            body = set(self.body)
            choices = [c for c in self.remaining if c not in body] or list(self.remaining)
            self.food = self._rnd.choice(sorted(choices))
            return
        free = [(x, y) for x in range(self.cols) for y in range(self.rows) if (x, y) not in set(self.body)]
        if not free:
            self.food, self.won, self.alive = None, True, False
            return
        self.food = self._rnd.choice(free)

    def step(self) -> bool:
        """Avance d'une case. Retourne True si la partie continue."""
        if not self.alive:
            return False
        if self._queue:
            self.direction = self._queue.pop(0)
        x, y = self.head
        nx, ny = x + self.direction[0], y + self.direction[1]
        if self.wrap:
            nx, ny = nx % self.cols, ny % self.rows
        elif not (0 <= nx < self.cols and 0 <= ny < self.rows):
            self.alive = False
            return False
        new = (nx, ny)
        eating = new in self.remaining if self.mode == ALL else new == self.food
        grow = False
        if eating:
            self._foods += 1
            grow = self.grow_every == 1 or (self.grow_every > 1 and self._foods % self.grow_every == 0)
        # la queue libère sa case ce tour-ci, sauf si on grandit
        body_to_check = list(self.body) if grow else list(self.body)[1:]
        if new in body_to_check:
            self.alive = False
            return False
        self.body.append(new)
        if not grow:
            self.body.popleft()
        if eating:
            if self.mode == CLASSIC:
                self.score += 1
            else:
                self._consume(new)
            self._place_food()
        return self.alive

    def interval_ms(self, base: int = 160, minimum: int = 60) -> int:
        """Vitesse : plus rapide tous les 3 points."""
        return max(minimum, base - 10 * (self.score // 3))


# ------------------------------------------------------------------ passage pixels -> cases


def grid_origin(width: int, height: int, cols: int, rows: int, cell: int) -> Tuple[int, int]:
    """Coin haut-gauche de la grille centrée dans une vue de width×height pixels."""
    return (width - cols * cell) // 2, (height - rows * cell) // 2


def point_to_cell(x: float, y: float, origin: Tuple[int, int], cell: int, cols: int, rows: int) -> Optional[Cell]:
    cx, cy = int((x - origin[0]) // cell), int((y - origin[1]) // cell)
    return (cx, cy) if 0 <= cx < cols and 0 <= cy < rows else None


def build_targets(points: Iterable[Tuple[Any, float, float]], origin: Tuple[int, int], cell: int, cols: int,
                  rows: int) -> Dict[Cell, List[Any]]:
    """(clé, x_pixel, y_pixel) -> {cellule: [clés]} ; les points hors grille sont ignorés."""
    out: Dict[Cell, List[Any]] = {}
    for key, x, y in points:
        c = point_to_cell(x, y, origin, cell, cols, rows)
        if c is not None:
            out.setdefault(c, []).append(key)
    return out
