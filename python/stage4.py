import json
import os
import urllib.error
import urllib.request
from urllib.parse import quote


def fetch_load_order(run_id):
    base = os.environ.get("DEPENDENCY_HELL_API_URL", "http://127.0.0.1:8080").rstrip("/")
    url = base + "/api/runs/" + quote(run_id, safe="") + "/load-order"
    request = urllib.request.Request(url, data=b"", method="POST")
    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            result = json.load(response)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(detail)
            if isinstance(parsed, dict): detail = parsed.get("error", detail)
        except json.JSONDecodeError:
            pass
        raise ValueError(f"не удалось определить порядок загрузки (HTTP {exc.code}): {detail}") from exc
    except (OSError, json.JSONDecodeError, UnicodeError) as exc:
        raise ValueError(f"не удалось определить порядок загрузки: {exc}") from exc
    if not isinstance(result, dict) or not isinstance(result.get("possible"), bool) or not isinstance(result.get("packages"), list) or not isinstance(result.get("cycleEdges"), list):
        raise ValueError("сервер вернул некорректный порядок загрузки")
    if any(not isinstance(package, str) for package in result["packages"]):
        raise ValueError("сервер вернул некорректное имя пакета")
    for edge in result["cycleEdges"]:
        if not isinstance(edge, dict) or not isinstance(edge.get("from"), str) or not isinstance(edge.get("to"), str) or edge.get("cycle") is not True:
            raise ValueError("сервер вернул некорректное ребро цикла")
    if result["possible"] and result["cycleEdges"] or not result["possible"] and (result["packages"] or not result["cycleEdges"]):
        raise ValueError("сервер вернул противоречивый результат")
    return result


def print_load_order(root, result, run_id):
    print(f"Корень: {root}")
    if result["possible"]:
        print("Порядок загрузки:")
        for index, package in enumerate(result["packages"], 1):
            print(f"  {index}. {package}")
    else:
        print("Строгий порядок загрузки невозможен: в графе есть циклы.")
        print("Обратные связи DFS, замыкающие циклы:")
        for edge in result["cycleEdges"]:
            print(f"  {edge['from']} -> {edge['to']}")
    print(f"run_id={run_id}")
