import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("stage3", ROOT / "python/stage3.py")
stage3 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(stage3)


class StageThreeChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.folder = Path(self.temp.name)
        self.config = self.folder / "config.csv"
        self.graph = self.folder / "graph.json"
        self.config.write_text("key,value\npackage,A\nrepository,graph.json\ntest_mode,true\nascii_tree,true\n", encoding="utf-8")

    def tearDown(self): self.temp.cleanup()

    def test_reads_relative_file_and_allows_implicit_leaf(self):
        self.graph.write_text('{"A": ["B"]}', encoding="utf-8")
        self.assertEqual(stage3.read_test_graph(self.config, "graph.json", "A"), {"A": ["B"]})

    def test_rejects_invalid_graphs(self):
        for data in ('{"A": ["b"]}', '{"A": "B"}', '{"B": []}', '{"A": [], "A": []}', '{not json}'):
            with self.subTest(data=data):
                self.graph.write_text(data, encoding="utf-8")
                with self.assertRaises(ValueError): stage3.read_test_graph(self.config, "graph.json", "A")

    def test_cli_sends_test_graph_and_prints_cycle(self):
        self.graph.write_text('{"A":["B"],"B":["A"]}', encoding="utf-8")
        requests = []
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                requests.append((self.path, data))
                if self.path == "/api/runs":
                    response = {"id":"sample"}
                else:
                    response = {"root":"A", "nodes":[{"id":"A"},{"id":"B"}], "edges":[{"from":"A","to":"B","cycle":False},{"from":"B","to":"A","cycle":True}], "visitOrder":["A","B"]}
                body = json.dumps(response).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
            def log_message(self, format, *args): pass
        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            env = dict(os.environ, DEPENDENCY_HELL_API_URL=f"http://127.0.0.1:{server.server_port}")
            result = subprocess.run([sys.executable, str(ROOT / "python/hell.py"), str(self.config)], capture_output=True, text=True, env=env)
        finally:
            server.shutdown(); server.server_close(); thread.join()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Порядок DFS: A -> B", result.stdout)
        self.assertIn("B -> A [цикл]", result.stdout)
        self.assertEqual(requests[1], ("/api/runs/sample/graph", {"testGraph":{"A":["B"],"B":["A"]}}))


if __name__ == "__main__": unittest.main()
