import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from kitlib import S_, bbox, parse, render_piece
from matplotlib.path import Path as MplPath
from PIL import Image


class SvgPathParserTest(unittest.TestCase):
    def test_lines_and_repeated_coordinate_pairs(self):
        vertices, codes = parse("M 0 0 1 1 L 2 2")
        self.assertEqual(vertices, [(0.0, 0.0), (1.0, 1.0), (2.0, 2.0)])
        self.assertEqual(codes, [MplPath.MOVETO, MplPath.LINETO, MplPath.LINETO])

    def test_cubic_bezier_consumes_three_coordinate_pairs(self):
        vertices, codes = parse("M0,0 C 1 2 3 4 5 6")
        self.assertEqual(vertices, [(0.0, 0.0), (1.0, 2.0), (3.0, 4.0), (5.0, 6.0)])
        self.assertEqual(codes, [MplPath.MOVETO, MplPath.CURVE4, MplPath.CURVE4, MplPath.CURVE4])

    def test_quadratic_bezier_is_preserved(self):
        vertices, codes = parse("M .5 -.25 Q 1.25 2.5 3 4")
        self.assertEqual(vertices, [(0.5, -0.25), (1.25, 2.5), (3.0, 4.0)])
        self.assertEqual(codes, [MplPath.MOVETO, MplPath.CURVE3, MplPath.CURVE3])

    def test_close_path_returns_to_subpath_start(self):
        vertices, codes = parse("M 1 2 L 3 4 Z")
        self.assertEqual(vertices[-1], (1.0, 2.0))
        self.assertEqual(codes[-1], MplPath.CLOSEPOLY)

    def test_signed_and_exponent_coordinates(self):
        vertices, _ = parse("M -1.5e-2 +2E+1 L .5 -.75")
        self.assertEqual(vertices, [(-0.015, 20.0), (0.5, -0.75)])

    def test_invalid_tokens_and_incomplete_curves_fail_clearly(self):
        with self.assertRaisesRegex(ValueError, "Invalid SVG path token"):
            parse("M 0 0 X 1 1")
        with self.assertRaisesRegex(ValueError, "Not enough coordinates"):
            parse("M 0 0 C 1 2 3 4")

    def test_bbox_contains_all_curve_control_points_and_margin(self):
        shapes = [S_("M 0 0 C 1 2 3 4 5 6", ink=False)]
        x0, y0, x1, y1 = bbox(shapes, margin=0.1)
        self.assertAlmostEqual(x0, -0.1)
        self.assertAlmostEqual(y0, -0.1)
        self.assertAlmostEqual(x1, 5.1)
        self.assertAlmostEqual(y1, 6.1)

    def test_render_translates_geometry_and_keeps_joint_inside_bitmap(self):
        shapes = [S_("M 0 0 L 1 0 L 1 1 Z", ink=False)]
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "piece.png"
            metrics = render_piece(shapes, (0.5, 0.5), target)
            self.assertTrue(target.is_file())
            self.assertGreater(target.stat().st_size, 0)
            with Image.open(target) as image:
                self.assertEqual(image.size, (metrics["w"], metrics["h"]))
            self.assertGreaterEqual(metrics["pivot"][0], 0)
            self.assertLessEqual(metrics["pivot"][0], metrics["w"])
            self.assertGreaterEqual(metrics["pivot"][1], 0)
            self.assertLessEqual(metrics["pivot"][1], metrics["h"])


if __name__ == "__main__":
    unittest.main()
