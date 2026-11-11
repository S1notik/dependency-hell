import importlib.util
import io
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch
from urllib.error import HTTPError


ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("stage2", ROOT / "python/stage2.py")
stage2 = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(stage2)


class StageTwoChecks(unittest.TestCase):
    def test_empty_dependencies_are_printed(self):
        output = io.StringIO()
        with redirect_stdout(output):
            stage2.print_direct_dependencies({"packageName": "empty", "version": "1.0.0", "dependencies": []}, "run-1")
        self.assertIn("Прямые зависимости:\n  нет", output.getvalue())
        self.assertIn("run_id=run-1", output.getvalue())

    def test_api_error_is_reported(self):
        error = HTTPError("http://example", 404, "Not Found", {}, io.BytesIO('{"error":"Пакет не найден"}'.encode()))
        with patch("urllib.request.urlopen", side_effect=error):
            with self.assertRaisesRegex(ValueError, "Пакет не найден"):
                stage2.fetch_direct_dependencies("run-1")

    def test_malformed_api_response_is_rejected(self):
        class Response:
            def __enter__(self): return io.BytesIO(b'{"packageName":"x","dependencies":[]}')
            def __exit__(self, *args): return False
        with patch("urllib.request.urlopen", return_value=Response()):
            with self.assertRaisesRegex(ValueError, "некорректный"):
                stage2.fetch_direct_dependencies("run-1")


if __name__ == "__main__":
    unittest.main()
