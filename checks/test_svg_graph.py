import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("svg_graph", ROOT / "python/svg_graph.py")
svg_graph = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(svg_graph)
NS = {"svg": "http://www.w3.org/2000/svg"}


def graph(root, names, edges):
    return {"root": root, "nodes": [{"id": name} for name in names],
            "edges": [{"from": source, "to": target, "cycle": cycle} for source, target, cycle in edges]}


class SvgGraphChecks(unittest.TestCase):
    def test_diamond_keeps_every_node_and_edge(self):
        data = graph("A", ["A", "B", "D", "C"],
                     [("A", "B", False), ("B", "D", False), ("A", "C", False), ("C", "D", False)])
        document = ET.fromstring(svg_graph.render_svg(data))
        nodes = document.findall('.//svg:g[@class="node"]', NS)
        edges = document.findall('.//svg:path[@class="edge regular"]', NS)
        arrows = document.findall('.//svg:polygon[@class="arrow regular"]', NS)
        self.assertEqual({node.attrib["data-id"] for node in nodes}, {"A", "B", "C", "D"})
        self.assertEqual({(edge.attrib["data-from"], edge.attrib["data-to"]) for edge in edges},
                         {("A", "B"), ("B", "D"), ("A", "C"), ("C", "D")})
        self.assertEqual(len(arrows), len(edges))

    def test_cycle_and_self_loop_are_rendered_as_cycles(self):
        data = graph("A", ["A", "B"], [("A", "B", False), ("B", "A", True), ("B", "B", True)])
        document = ET.fromstring(svg_graph.render_svg(data))
        cycles = document.findall('.//svg:path[@class="edge cycle"]', NS)
        self.assertEqual({(e.attrib["data-from"], e.attrib["data-to"]) for e in cycles}, {("B", "A"), ("B", "B")})
        self.assertTrue(all(e.attrib.get("stroke-dasharray") for e in cycles))
        self.assertEqual(len(document.findall('.//svg:polygon[@class="arrow cycle"]', NS)), 2)

    def test_special_characters_are_text_not_markup(self):
        name = 'a<&"/script>'
        document = ET.fromstring(svg_graph.render_svg(graph(name, [name], [])))
        self.assertEqual(document.find('.//svg:g[@class="node"]', NS).attrib["data-id"], name)
        self.assertEqual(document.findall('.//svg:script', NS), [])

    def test_rejects_invalid_and_unreachable_nodes(self):
        for data in (graph("A", ["A", "B"], []), graph("A", ["A", "A"], []),
                     graph("A", ["A"], [("A", "B", False)])):
            with self.subTest(data=data), self.assertRaises(ValueError):
                svg_graph.render_svg(data)

    def test_save_does_not_overwrite_existing_file(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "graph.svg"
            data = graph("A", ["A"], [])
            self.assertEqual(svg_graph.save_svg(data, path), path.resolve())
            saved = path.read_text(encoding="utf-8")
            with self.assertRaises(ValueError):
                svg_graph.save_svg(data, path)
            self.assertEqual(path.read_text(encoding="utf-8"), saved)

    def test_open_uses_file_uri_and_reports_failure(self):
        path = Path(tempfile.gettempdir()) / "test graph.svg"
        with patch.object(svg_graph.webbrowser, "open", return_value=True) as open_browser:
            self.assertTrue(svg_graph.open_svg(path))
            open_browser.assert_called_once_with(path.resolve().as_uri())
        with patch.object(svg_graph.webbrowser, "open", return_value=False):
            self.assertFalse(svg_graph.open_svg(path))


if __name__ == "__main__":
    unittest.main()
