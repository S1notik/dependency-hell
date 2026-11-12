import json
import os
import urllib.error
import urllib.request
from urllib.parse import quote


def fetch_visualization(run_id, expect_ascii):
    base = os.environ.get("DEPENDENCY_HELL_API_URL", "http://127.0.0.1:8080").rstrip("/")
    url = base + "/api/runs/" + quote(run_id, safe="") + "/visualization"
    request = urllib.request.Request(url, data=b"", method="POST")
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            result = json.load(response)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(detail)
            if isinstance(parsed, dict): detail = parsed.get("error", detail)
        except json.JSONDecodeError:
            pass
        raise ValueError(f"не удалось сформировать текст графа (HTTP {exc.code}): {detail}") from exc
    except (OSError, json.JSONDecodeError, UnicodeError) as exc:
        raise ValueError(f"не удалось сформировать текст графа: {exc}") from exc
    if not isinstance(result, dict) or not isinstance(result.get("mermaid"), str) or not result["mermaid"]:
        raise ValueError("сервер вернул некорректный Mermaid")
    ascii_tree = result.get("asciiTree")
    if expect_ascii and (not isinstance(ascii_tree, str) or not ascii_tree):
        raise ValueError("сервер не вернул запрошенное ASCII-дерево")
    if not expect_ascii and ascii_tree is not None:
        raise ValueError("сервер вернул ASCII-дерево, хотя оно выключено")
    return result


def print_visualization(result, run_id):
    print("Mermaid:")
    print(result["mermaid"])
    if result.get("asciiTree") is not None:
        print("ASCII-дерево:")
        print(result["asciiTree"])
    print(f"run_id={run_id}")
