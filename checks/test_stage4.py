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


class StageFourChecks(unittest.TestCase):
    def run_cli(self, order, *options):
        requests = []
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                data = self.rfile.read(int(self.headers.get("Content-Length", 0)))
                requests.append((self.path, data))
                if self.path == "/api/runs":
                    response = {"id": "sample"}
                elif self.path.endswith("/graph"):
                    response = {"root":"A", "nodes":[{"id":"A"},{"id":"B"}], "edges":[{"from":"A","to":"B","cycle":False}], "visitOrder":["A","B"]}
                else:
                    response = order
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
            result = subprocess.run([sys.executable, str(ROOT / "python/hell.py"), str(ROOT / "examples/test/config.csv"), *options], capture_output=True, text=True, env=env)
        finally:
            server.shutdown(); server.server_close(); thread.join()
        return result, requests

    def test_prints_dependencies_first(self):
        result, requests = self.run_cli({"possible":True, "packages":["B", "A"], "cycleEdges":[]})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("1. B\n  2. A", result.stdout)
        self.assertNotIn("Строится граф", result.stdout)
        self.assertEqual(requests[-1][0], "/api/runs/sample/load-order")

    def test_reports_cycle_without_order(self):
        result, _ = self.run_cli({"possible":False, "packages":[], "cycleEdges":[{"from":"B", "to":"A", "cycle":True}]})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("невозможен", result.stdout)
        self.assertIn("B -> A", result.stdout)
        self.assertNotIn("1.", result.stdout)

    def test_json_includes_graph_order_and_id(self):
        result, _ = self.run_cli({"possible":True, "packages":["B", "A"], "cycleEdges":[]}, "--json")
        self.assertEqual(result.returncode, 0, result.stderr)
        body = json.loads(result.stdout)
        self.assertEqual(body["runId"], "sample")
        self.assertEqual(body["order"]["packages"], ["B", "A"])
        self.assertEqual(body["graph"]["root"], "A")

    def test_rejects_contradictory_order_response(self):
        result, _ = self.run_cli({"possible":False, "packages":["B", "A"], "cycleEdges":[]})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("противоречивый", result.stderr)


if __name__ == "__main__": unittest.main()
