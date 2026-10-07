import io
import os
import random
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from layout_gif.core.gif_writer import GifWriter, lzw_encode  # noqa: E402
from layout_gif.core.timing import output_delays, output_frame_count, source_frame_index  # noqa: E402

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    Image = None


def pixels(im):
    px = im.convert("RGB").load()
    return [px[x, y] for y in range(im.size[1]) for x in range(im.size[0])]


def make_gif(frames, w, h, delays, loop=0):
    buf = io.BytesIO()
    with GifWriter(buf, w, h, loop) as g:
        for (idx, pal), d in zip(frames, delays):
            g.add_frame(idx, pal, d)
    return buf.getvalue()


@unittest.skipIf(Image is None, "Pillow absent : relecture impossible")
class WriterTests(unittest.TestCase):
    def test_animation_roundtrip(self):
        w, h = 7, 5
        red, green, blue = (255, 0, 0), (0, 255, 0), (0, 0, 255)
        frames = [(bytes([0] * (w * h)), bytes(c)) for c in (red + (0, 0, 0), green + (0, 0, 0), blue + (0, 0, 0))]
        data = make_gif(frames, w, h, [100, 200, 300], loop=3)
        im = Image.open(io.BytesIO(data))
        self.assertEqual((im.size, im.n_frames), ((w, h), 3))
        self.assertEqual(im.info["loop"], 3)
        seen = []
        for i in range(3):
            im.seek(i)
            seen.append((im.info["duration"], im.convert("RGB").getpixel((0, 0))))
        self.assertEqual(seen, [(100, red), (200, green), (300, blue)])

    def test_infinite_loop_and_min_delay(self):
        data = make_gif([(b"\x00" * 4, b"\x00\x00\x00\xff\xff\xff")] * 2, 2, 2, [1, 1000])
        im = Image.open(io.BytesIO(data))
        self.assertEqual(im.info["loop"], 0)
        self.assertEqual(im.info["duration"], 20)  # délai minimal

    def test_pixel_exactness_various_palettes(self):
        rnd = random.Random(1)
        for n_colors in (2, 3, 4, 5, 16, 17, 100, 256):
            w, h = 31, 23
            palette = bytes(rnd.randrange(256) for _ in range(n_colors * 3))
            idx = bytes(rnd.randrange(n_colors) for _ in range(w * h))
            data = make_gif([(idx, palette)], w, h, [100])
            im = Image.open(io.BytesIO(data)).convert("RGB")
            expected = [tuple(palette[i * 3:i * 3 + 3]) for i in idx]
            self.assertEqual(pixels(im), expected, f"{n_colors} couleurs")

    def test_large_image_resets_lzw_table(self):
        # 300x300 de bruit : sature la table LZW (> 4096 codes) plusieurs fois
        rnd = random.Random(7)
        w = h = 300
        palette = bytes(rnd.randrange(256) for _ in range(256 * 3))
        idx = bytes(rnd.randrange(256) for _ in range(w * h))
        data = make_gif([(idx, palette)], w, h, [100])
        im = Image.open(io.BytesIO(data)).convert("RGB")
        self.assertEqual(pixels(im), [tuple(palette[i * 3:i * 3 + 3]) for i in idx])

    def test_uniform_and_tiny(self):
        for size in ((1, 1), (3, 1), (1, 9), (64, 64)):
            w, h = size
            data = make_gif([(bytes(w * h), b"\x10\x20\x30\x40\x50\x60")], w, h, [50])
            im = Image.open(io.BytesIO(data)).convert("RGB")
            self.assertEqual(set(pixels(im)), {(0x10, 0x20, 0x30)})

    def test_file_path_target(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "o.gif")
            with GifWriter(path, 2, 2) as g:
                g.add_frame(b"\x00\x01\x01\x00", b"\x00\x00\x00\xff\xff\xff", 100)
            with Image.open(path) as im:
                self.assertEqual(im.size, (2, 2))


class LzwTests(unittest.TestCase):
    def test_header_and_end(self):
        out = lzw_encode(b"\x00\x01", 2)
        self.assertTrue(len(out) >= 1)

    def test_invalid_frame_size(self):
        buf = io.BytesIO()
        g = GifWriter(buf, 2, 2)
        with self.assertRaises(ValueError):
            g.add_frame(b"\x00", b"\x00\x00\x00\xff\xff\xff")
        with self.assertRaises(ValueError):
            g.add_frame(b"\x00" * 4, b"")


class TimingTests(unittest.TestCase):
    def test_counts_and_index(self):
        self.assertEqual(output_frame_count([3, 8, 5]), 8)
        self.assertEqual([source_frame_index(i, 3) for i in range(7)], [0, 1, 2, 0, 1, 2, 0])
        with self.assertRaises(ValueError):
            output_frame_count([0, 0])

    def test_delays(self):
        self.assertEqual(output_delays([[100, 200], [50, 50, 50]], 5), [50, 50, 50, 50, 50])
        self.assertEqual(output_delays([[100, 200]], 3), [100, 200, 100])
        self.assertEqual(output_delays([[0]], 2), [100, 100])
        self.assertEqual(output_delays([[100]], 2, fixed_ms=10), [20, 20])  # min 20 ms
        self.assertEqual(output_delays([], 2), [100, 100])


if __name__ == "__main__":
    unittest.main()
