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


class StageFiveChecks(unittest.TestCase):
    def run_cli(self, ascii_tree, visual, *args):
        requests = []
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                raw = self.rfile.read(int(self.headers.get("Content-Length", 0)))
                requests.append((self.path, json.loads(raw) if raw else None))
                if self.path == "/api/runs":
                    value = {"id": "sample"}
                elif self.path.endswith("/graph"):
                    value = {"root":"A", "nodes":[{"id":"A"}], "edges":[], "visitOrder":["A"]}
                elif self.path.endswith("/load-order"):
                    value = {"possible":True, "packages":["A"], "cycleEdges":[]}
                else:
                    value = visual
                body = json.dumps(value).encode()
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
            with tempfile.TemporaryDirectory() as directory:
                folder = Path(directory)
                (folder / "graph.json").write_text('{"A":[]}', encoding="utf-8")
                config = folder / "config.csv"
                config.write_text(f"key,value\npackage,A\nrepository,graph.json\ntest_mode,true\nascii_tree,{str(ascii_tree).lower()}\n", encoding="utf-8")
                env = dict(os.environ, DEPENDENCY_HELL_API_URL=f"http://127.0.0.1:{server.server_port}")
                result = subprocess.run([sys.executable, str(ROOT / "python/hell.py"), str(config), *args], capture_output=True, text=True, env=env)
        finally:
            server.shutdown(); server.server_close(); thread.join()
        return result, requests

    def test_prints_mermaid_and_ascii_when_enabled(self):
        result, requests = self.run_cli(True, {"mermaid":"flowchart TD\n    n0[\"A\"]", "asciiTree":"A"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('Mermaid:\nflowchart TD', result.stdout)
        self.assertIn('ASCII-дерево:\nA', result.stdout)
        self.assertIn('run_id=sample', result.stdout)
        self.assertEqual(requests[0][1]["asciiTree"], True)
        self.assertEqual(requests[-1][0], '/api/runs/sample/visualization')

    def test_omits_ascii_when_disabled(self):
        result, requests = self.run_cli(False, {"mermaid":"flowchart TD\n    n0[\"A\"]", "asciiTree":None})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Mermaid:", result.stdout)
        self.assertNotIn("ASCII-дерево", result.stdout)
        self.assertEqual(requests[0][1]["asciiTree"], False)

    def test_rejects_missing_expected_ascii(self):
        result, _ = self.run_cli(True, {"mermaid":"flowchart TD", "asciiTree":None})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ASCII", result.stderr)

    def test_json_keeps_stage_four_comparison_contract(self):
        result, requests = self.run_cli(False, {}, "--json")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["order"]["packages"], ["A"])
        self.assertEqual(requests[-1][0], '/api/runs/sample/load-order')


if __name__ == "__main__": unittest.main()
