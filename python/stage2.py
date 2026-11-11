import json
import os
import urllib.error
import urllib.request
from urllib.parse import quote


# Получаем только прямые зависимости; обход графа появится на следующем этапе.
def fetch_direct_dependencies(run_id):
    base = os.environ.get("DEPENDENCY_HELL_API_URL", "http://127.0.0.1:8080").rstrip("/")
    url = base + "/api/runs/" + quote(run_id, safe="") + "/dependencies"
    request = urllib.request.Request(url, data=b"", method="POST")
    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            result = json.load(response)
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(detail)
            if isinstance(parsed, dict):
                detail = parsed.get("error", detail)
        except json.JSONDecodeError:
            pass
        raise ValueError(f"не удалось получить зависимости (HTTP {exc.code}): {detail}") from exc
    except (OSError, json.JSONDecodeError, UnicodeError) as exc:
        raise ValueError(f"не удалось получить зависимости: {exc}") from exc
    if not isinstance(result, dict) or not isinstance(result.get("packageName"), str) or not isinstance(result.get("version"), str) or not isinstance(result.get("dependencies"), list):
        raise ValueError("сервер вернул некорректный список зависимостей")
    for item in result["dependencies"]:
        if not isinstance(item, dict) or not isinstance(item.get("name"), str) or not isinstance(item.get("versionSpec"), str):
            raise ValueError("сервер вернул некорректную зависимость")
    return result


def print_direct_dependencies(result, run_id):
    print(f"package={result['packageName']}")
    print(f"version={result['version']}")
    print("Прямые зависимости:")
    if not result["dependencies"]:
        print("  нет")
    for item in result["dependencies"]:
        print(f"  {item['name']}: {item['versionSpec']}")
    print(f"run_id={run_id}")
