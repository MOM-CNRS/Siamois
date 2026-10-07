import os
import sys
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from snake_game.game import DOWN, LEFT, RIGHT, UP, SnakeGame  # noqa: E402


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


if __name__ == "__main__":
    unittest.main()
