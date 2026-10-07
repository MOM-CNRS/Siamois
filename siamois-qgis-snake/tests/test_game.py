import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from snake_game.game import (  # noqa: E402
    ALL, DOWN, LEFT, RIGHT, SINGLE, UP, SnakeGame, build_targets, grid_origin, point_to_cell,
)


class SnakeTests(unittest.TestCase):
    def game(self, **kw):
        g = SnakeGame(10, 8, seed=1, **kw)
        g.food = (0, 0)
        return g

    def test_initial_state(self):
        g = SnakeGame(10, 8, seed=1)
        self.assertEqual(g.length, 3)
        self.assertEqual(g.direction, RIGHT)
        self.assertTrue(g.alive)
        self.assertNotIn(g.food, g.body)
        ys = {y for _, y in g.body}
        self.assertEqual(len(ys), 1)

    def test_moves_forward(self):
        g = self.game()
        head = g.head
        self.assertTrue(g.step())
        self.assertEqual(g.head, (head[0] + 1, head[1]))
        self.assertEqual(g.length, 3)

    def test_no_reverse_and_turn_queue(self):
        g = self.game()
        g.turn(LEFT)  # demi-tour : ignoré
        g.step()
        self.assertEqual(g.direction, RIGHT)
        g.turn(UP)
        g.turn(LEFT)  # deux virages dans le même tour : pris en 2 pas, sans demi-tour
        g.step()
        self.assertEqual(g.direction, UP)
        g.step()
        self.assertEqual(g.direction, LEFT)

    def test_eat_grows_and_scores(self):
        g = self.game()
        g.food = (g.head[0] + 1, g.head[1])
        g.step()
        self.assertEqual((g.length, g.score), (4, 1))
        self.assertNotIn(g.food, g.body)

    def test_wall_collision(self):
        g = self.game()
        for _ in range(20):
            if not g.step():
                break
        self.assertFalse(g.alive)
        self.assertFalse(g.step())

    def test_wrap(self):
        g = self.game(wrap=True)
        for _ in range(15):
            g.step()
        self.assertTrue(g.alive)
        self.assertTrue(0 <= g.head[0] < 10)

    def test_self_collision(self):
        g = SnakeGame(10, 8, seed=2, start_length=3)
        g.food = (0, 0)
        # on grossit à 6 en plaçant la nourriture devant, puis on se mord
        for _ in range(3):
            g.food = (g.head[0] + 1, g.head[1])
            g.step()
        self.assertEqual(g.length, 6)
        g.food = (0, 0)
        g.turn(DOWN)
        g.step()
        g.turn(LEFT)
        g.step()
        g.turn(UP)
        g.step()
        self.assertFalse(g.alive)

    def test_tail_cell_is_free_when_not_eating(self):
        g = SnakeGame(4, 4, seed=3, start_length=3)
        g.food = (0, 0)
        # serpent en carré 2x2 : la tête peut entrer dans la case que la queue quitte
        g.body.clear()
        g.body.extend([(1, 1), (1, 2), (2, 2), (2, 1)])  # tête en (2,1), queue en (1,1)
        g.direction = UP
        g._queue = [LEFT]
        self.assertTrue(g.step())  # (1,1) était la queue : libre ce tour-ci

    def test_win_when_board_full(self):
        g = SnakeGame(4, 4, seed=4, start_length=3)
        g.body.clear()
        cells = [(x, y) for y in range(4) for x in (range(4) if y % 2 == 0 else reversed(range(4)))]
        g.body.extend(cells[:-1])
        g.direction = RIGHT if 3 % 2 == 0 else LEFT
        g.food = cells[-1]
        g.direction = (cells[-1][0] - cells[-2][0], cells[-1][1] - cells[-2][1])
        g.step()
        self.assertTrue(g.won)
        self.assertFalse(g.alive)

    def test_speed_increases(self):
        g = self.game()
        g.score = 0
        slow = g.interval_ms()
        g.score = 30
        self.assertLess(g.interval_ms(), slow)
        g.score = 1000
        self.assertEqual(g.interval_ms(), 60)

    def test_small_grid_rejected(self):
        with self.assertRaises(ValueError):
            SnakeGame(3, 10)


class TargetsTests(unittest.TestCase):
    def game(self, targets, **kw):
        return SnakeGame(12, 8, seed=1, targets=targets, **kw)

    def test_all_mode_eats_cells_and_wins(self):
        g = SnakeGame(12, 8, seed=1)
        y = g.head[1]
        t = {(g.head[0] + 2, y): [10, 11], (g.head[0] + 4, y): [12]}
        g = self.game(t, mode=ALL)
        self.assertEqual((g.total, g.mode, g.food), (3, ALL, None))
        g.step()
        g.step()
        self.assertEqual((g.eaten, g.last_eaten, g.score), ([10, 11], [10, 11], 2))  # 2 entités dans la même case
        self.assertTrue(g.alive)
        g.step()
        g.step()
        self.assertEqual(g.eaten, [10, 11, 12])
        self.assertTrue(g.won)
        self.assertFalse(g.alive)

    def test_single_mode_lights_one_target(self):
        base = SnakeGame(12, 8, seed=1)
        y = base.head[1]
        t = {(base.head[0] + 1, y): ["a"], (0, 0): ["b"], (11, 7): ["c"]}
        g = self.game(t, mode=SINGLE)
        self.assertIn(g.food, t)
        g.food = (g.head[0] + 1, y)
        g.step()
        self.assertEqual(g.eaten, ["a"])
        self.assertIn(g.food, [(0, 0), (11, 7)])  # une nouvelle cible
        self.assertEqual(g.total, 3)

    def test_non_target_cell_in_single_mode_is_ignored(self):
        base = SnakeGame(12, 8, seed=1)
        y = base.head[1]
        g = self.game({(0, 0): ["b"], (11, 7): ["c"]}, mode=SINGLE)
        g.food = (0, 0)
        g.step()
        self.assertEqual((g.eaten, g.length), ([], 3))

    def test_growth_modes(self):
        base = SnakeGame(14, 8, seed=1)
        y, x = base.head[1], base.head[0]
        t = {(x + i, y): [i] for i in range(1, 7)}
        never = SnakeGame(14, 8, seed=1, targets=t, mode=ALL, grow_every=0)
        for _ in range(6):
            never.step()
        self.assertEqual((len(never.eaten), never.length), (6, 3))
        third = SnakeGame(14, 8, seed=1, targets=t, mode=ALL, grow_every=3)
        for _ in range(6):
            third.step()
        self.assertEqual((len(third.eaten), third.length), (6, 5))
        each = SnakeGame(14, 8, seed=1, targets=t, mode=ALL, grow_every=1)
        for _ in range(6):
            each.step()
        self.assertEqual(each.length, 9)

    def test_target_under_initial_snake_is_already_eaten(self):
        base = SnakeGame(12, 8, seed=1)
        g = self.game({base.head: ["x"], (0, 0): ["y"]}, mode=ALL)
        self.assertEqual(g.eaten, ["x"])
        self.assertEqual(len(g.remaining), 1)

    def test_out_of_grid_targets_ignored_and_classic_fallback(self):
        g = self.game({(99, 99): ["z"]}, mode=ALL)
        self.assertEqual(g.mode, "classic")
        self.assertIsNotNone(g.food)

    def test_pixel_mapping(self):
        origin = grid_origin(500, 300, 10, 6, 40)
        self.assertEqual(origin, (50, 30))
        self.assertEqual(point_to_cell(50, 30, origin, 40, 10, 6), (0, 0))
        self.assertEqual(point_to_cell(449, 269, origin, 40, 10, 6), (9, 5))
        self.assertIsNone(point_to_cell(49, 100, origin, 40, 10, 6))
        self.assertIsNone(point_to_cell(450, 100, origin, 40, 10, 6))
        t = build_targets([("a", 60, 40), ("b", 70, 50), ("c", 0, 0)], origin, 40, 10, 6)
        self.assertEqual(t, {(0, 0): ["a", "b"]})


if __name__ == "__main__":
    unittest.main()
