"""Logique du Snake (pure, sans QGIS ni Qt) : grille, déplacement, nourriture, collisions."""

from __future__ import annotations

import random
from collections import deque
from typing import Deque, List, Optional, Tuple

Cell = Tuple[int, int]  # (colonne, ligne)
UP, DOWN, LEFT, RIGHT = (0, -1), (0, 1), (-1, 0), (1, 0)


class SnakeGame:
    def __init__(self, cols: int, rows: int, *, wrap: bool = False, seed: Optional[int] = None, start_length: int = 3):
        if cols < 4 or rows < 4:
            raise ValueError("grille trop petite (minimum 4×4)")
        self.cols, self.rows, self.wrap = cols, rows, wrap
        self._rnd = random.Random(seed)
        self.start_length = min(start_length, cols - 1)
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

    def _place_food(self) -> None:
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
        eating = new == self.food
        # la queue libère sa case ce tour-ci, sauf si on grandit
        body_to_check = list(self.body) if eating else list(self.body)[1:]
        if new in body_to_check:
            self.alive = False
            return False
        self.body.append(new)
        if eating:
            self.score += 1
            self._place_food()
        else:
            self.body.popleft()
        return self.alive

    def interval_ms(self, base: int = 160, minimum: int = 60) -> int:
        """Vitesse : plus rapide tous les 3 points."""
        return max(minimum, base - 10 * (self.score // 3))
