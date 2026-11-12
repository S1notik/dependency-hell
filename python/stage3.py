import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import quote


def read_test_graph(config_path, repository, root):
    path = Path(repository).expanduser()
    if not path.is_absolute():
        path = Path(config_path).expanduser().resolve().parent / path

    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError(f"повторяющийся пакет в тестовом графе: {key}")
            result[key] = value
        return result

    try:
        with path.open("r", encoding="utf-8-sig") as file:
            raw = file.read(8 * 1024 * 1024 + 1)
        if len(raw) > 8 * 1024 * 1024:
            raise ValueError("тестовый граф превышает 8 МиБ")
        graph = json.loads(raw, object_pairs_hook=unique_pairs)
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"не удалось прочитать тестовый граф {path}: {exc}") from exc
    if not isinstance(graph, dict) or len(graph) > 10000:
        raise ValueError("тестовый граф должен быть JSON-объектом не более чем из 10000 пакетов")
    valid_name = lambda value: isinstance(value, str) and len(value) <= 214 and bool(re.fullmatch(r"[A-Z]+", value))
    for name, dependencies in graph.items():
        if not valid_name(name) or not isinstance(dependencies, list) or any(not valid_name(dep) for dep in dependencies):
            raise ValueError("пакеты должны состоять из больших латинских букв, а зависимости — быть списками")
    if root not in graph and not any(root in dependencies for dependencies in graph.values()):
        raise ValueError(f"пакет {root} отсутствует в тестовом графе")
    return graph


def fetch_graph(run_id, test_graph):
    base = os.environ.get("DEPENDENCY_HELL_API_URL", "http://127.0.0.1:8080").rstrip("/")
    url = base + "/api/runs/" + quote(run_id, safe="") + "/graph"
    data = json.dumps({"testGraph": test_graph}).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            graph = json.load(response)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(detail)
            if isinstance(parsed, dict): detail = parsed.get("error", detail)
        except json.JSONDecodeError:
            pass
        raise ValueError(f"не удалось построить граф (HTTP {exc.code}): {detail}") from exc
    except (OSError, json.JSONDecodeError, UnicodeError) as exc:
        raise ValueError(f"не удалось построить граф: {exc}") from exc
    if not isinstance(graph, dict) or not isinstance(graph.get("root"), str) or not isinstance(graph.get("nodes"), list) or not isinstance(graph.get("edges"), list) or not isinstance(graph.get("visitOrder"), list):
        raise ValueError("сервер вернул некорректный граф")
    for edge in graph["edges"]:
        if not isinstance(edge, dict) or not isinstance(edge.get("from"), str) or not isinstance(edge.get("to"), str) or not isinstance(edge.get("cycle"), bool):
            raise ValueError("сервер вернул некорректную связь графа")
    if any(not isinstance(item, str) for item in graph["visitOrder"]):
        raise ValueError("сервер вернул некорректный порядок обхода")
    return graph


def print_graph(graph, run_id):
    print(f"Корень: {graph['root']}")
    print("Порядок DFS: " + " -> ".join(graph["visitOrder"]))
    print(f"Вершин: {len(graph['nodes'])}; связей: {len(graph['edges'])}")
    print("Связи:")
    if not graph["edges"]: print("  нет")
    for edge in graph["edges"]:
        mark = " [цикл]" if edge["cycle"] else ""
        print(f"  {edge['from']} -> {edge['to']}{mark}")
    print(f"run_id={run_id}")
