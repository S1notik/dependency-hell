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
SPEC = importlib.util.spec_from_file_location("hell", ROOT / "python" / "hell.py")
hell = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(hell)


class StageOneChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.dir = Path(self.temp.name)
        self.config = self.dir / "config.csv"

    def tearDown(self):
        self.temp.cleanup()

    def write_config(self, package="express", repository="https://registry.npmjs.org", test_mode="false", ascii_tree="true"):
        self.config.write_text(f"key,value\npackage,{package}\nrepository,{repository}\ntest_mode,{test_mode}\nascii_tree,{ascii_tree}\n", encoding="utf-8")

    def test_local_cli_prints_all_four_settings(self):
        self.write_config()
        result = subprocess.run([sys.executable, str(ROOT / "python/hell.py"), str(self.config), "--local"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.splitlines(), ["package=express", "repository=https://registry.npmjs.org", "test_mode=false", "ascii_tree=true"])

    def test_test_file_is_relative_to_csv(self):
        (self.dir / "graph.json").write_text('{"A": []}', encoding="utf-8")
        self.write_config(package="A", repository="graph.json", test_mode="true")
        self.assertEqual(hell.read_config(self.config)["repository"], "graph.json")

    def test_test_mode_rejects_missing_file_and_bad_package(self):
        self.write_config(package="bad name", repository="missing.json", test_mode="true")
        with self.assertRaises(ValueError) as error:
            hell.read_config(self.config)
        self.assertIn("package", str(error.exception))
        self.assertIn("repository", str(error.exception))

    def test_rejects_invalid_url_and_package(self):
        self.write_config(package="Not A Package", repository="not-an-url")
        with self.assertRaises(ValueError) as error:
            hell.read_config(self.config)
        self.assertIn("package", str(error.exception))
        self.assertIn("repository", str(error.exception))

    def test_rejects_url_with_spaces_or_invalid_port(self):
        for repository in ("https://bad host", "https://example.org:99999"):
            with self.subTest(repository=repository):
                self.write_config(repository=repository)
                with self.assertRaisesRegex(ValueError, "repository"):
                    hell.read_config(self.config)

    def test_rejects_each_invalid_boolean(self):
        self.write_config(test_mode="perhaps", ascii_tree="1")
        with self.assertRaises(ValueError) as error:
            hell.read_config(self.config)
        self.assertIn("test_mode", str(error.exception))
        self.assertIn("ascii_tree", str(error.exception))

    def test_rejects_missing_duplicate_and_unknown_fields(self):
        for data, expected in [
            ("key,value\npackage,express\n", "отсутствуют"),
            ("key,value\npackage,one\npackage,two\n", "повторяется"),
            ("key,value\nextra,x\n", "неизвестный"),
        ]:
            with self.subTest(expected=expected):
                self.config.write_text(data, encoding="utf-8")
                with self.assertRaisesRegex(ValueError, expected):
                    hell.read_config(self.config)

    def test_scoped_package_and_bom(self):
        self.config.write_text("\ufeffkey,value\npackage,@scope/name\nrepository,https://registry.npmjs.org\ntest_mode,FALSE\nascii_tree,TRUE\n", encoding="utf-8")
        values = hell.read_config(self.config)
        self.assertEqual(values["package"], "@scope/name")
        self.assertEqual(values["ascii_tree"], "true")

    def test_cli_posts_json_to_api_and_prints_id(self):
        self.write_config()
        received = []

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                raw = self.rfile.read(int(self.headers["Content-Length"]))
                received.append((self.path, json.loads(raw) if raw else None))
                if self.path == "/api/runs":
                    body = json.dumps({"id": "example-id"}).encode()
                    self.send_response(201)
                else:
                    body = json.dumps({"packageName": "express", "version": "5.0.0", "dependencies": [{"name": "accepts", "versionSpec": "^2.0.0"}]}).encode()
                    self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, format, *args):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            env = dict(os.environ, DEPENDENCY_HELL_API_URL=f"http://127.0.0.1:{server.server_port}")
            result = subprocess.run([sys.executable, str(ROOT / "python/hell.py"), str(self.config)], capture_output=True, text=True, env=env)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("run_id=example-id", result.stdout)
        self.assertIn("accepts: ^2.0.0", result.stdout)
        self.assertIn("version=5.0.0", result.stdout)
        self.assertEqual(received, [("/api/runs", {"packageName": "express", "repository": "https://registry.npmjs.org", "testMode": False, "asciiTree": True}), ("/api/runs/example-id/dependencies", None)])

    def test_test_mode_remains_available_for_local_validation(self):
        (self.dir / "graph.json").write_text('{"A": []}', encoding="utf-8")
        self.write_config(package="A", repository="graph.json", test_mode="true")
        result = subprocess.run([sys.executable, str(ROOT / "python/hell.py"), str(self.config), "--local"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("test_mode=true", result.stdout)

    def test_test_mode_reports_not_implemented_for_analysis(self):
        (self.dir / "graph.json").write_text('{"A": []}', encoding="utf-8")
        self.write_config(package="A", repository="graph.json", test_mode="true")
        result = subprocess.run([sys.executable, str(ROOT / "python/hell.py"), str(self.config)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn("этапе 3", result.stderr)


if __name__ == "__main__":
    unittest.main()
