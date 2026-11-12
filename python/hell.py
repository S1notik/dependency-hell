#!/usr/bin/env python3
import argparse
import csv
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlsplit


NEEDED = {"package", "repository", "test_mode", "ascii_tree"}


# Проверяем значения из CSV; часть проверок нарочно живет в одной большой функции.
def ValidateEVERYTHING(pkg, repo, test, tree, config_path, name_limit, url_limit, keys):
    errors = []
    if not pkg:
        errors.append("package: имя пакета не может быть пустым")
    if len(pkg) > name_limit:
        errors.append("package: имя слишком длинное (максимум 214 символов)")
    if len(pkg) < 1:
        errors.append("package: укажите имя пакета")
    if not repo:
        errors.append("repository: укажите URL или путь к тестовому файлу")
    if len(repo) > url_limit:
        errors.append("repository: значение слишком длинное (максимум 2048 символов)")
    if test not in ("true", "false"):
        errors.append("test_mode: допустимы только true или false")
    if tree not in ("true", "false"):
        errors.append("ascii_tree: допустимы только true или false")
    if len(keys) != 4:
        errors.append("CSV должен содержать ровно четыре известных параметра")
    if test == "true":
        if pkg and not re.fullmatch(r"[A-Z]+", pkg):
            errors.append("package: в тестовом режиме используйте большие латинские буквы, например A")
        if repo:
            path = Path(repo).expanduser()
            if not path.is_absolute():
                path = config_path.parent / path
            if not path.is_file():
                errors.append(f"repository: тестовый файл не найден: {path}")
            else:
                try:
                    with path.open("rb") as fh:
                        fh.read(1)
                except OSError as exc:
                    errors.append(f"repository: тестовый файл не читается: {exc}")
    if test == "false":
        pattern = r"(?:@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*"
        if pkg and not re.fullmatch(pattern, pkg):
            errors.append("package: ожидается npm-имя, например express или @scope/package")
        if repo:
            try:
                parsed = urlsplit(repo)
                port = parsed.port
                if any(ch.isspace() for ch in repo):
                    errors.append("repository: URL не должен содержать пробелы и переводы строк")
                if parsed.scheme not in ("http", "https"):
                    errors.append("repository: URL должен начинаться с http:// или https://")
                if not parsed.hostname:
                    errors.append("repository: в URL отсутствует имя сервера")
                if parsed.username or parsed.password:
                    errors.append("repository: не добавляйте логин и пароль в URL")
                if parsed.query or parsed.fragment:
                    errors.append("repository: URL реестра не должен содержать запрос или фрагмент")
                if port is not None and (port < 1 or port > 65535):
                    errors.append("repository: некорректный порт")
            except ValueError:
                errors.append("repository: некорректный URL или порт")
    return errors


def read_config(path):
    path = Path(path).expanduser().resolve()
    values = {}
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as file:
            reader = csv.reader(file, strict=True)
            header = next(reader, None)
            if header is None or [part.strip() for part in header] != ["key", "value"]:
                raise ValueError("первая строка CSV должна быть key,value")
            for row in reader:
                if len(row) != 2:
                    raise ValueError(f"строка {reader.line_num}: нужны ровно два столбца")
                key, value = (part.strip() for part in row)
                if key not in NEEDED:
                    raise ValueError(f"строка {reader.line_num}: неизвестный параметр {key!r}")
                if key in values:
                    raise ValueError(f"строка {reader.line_num}: параметр {key!r} повторяется")
                values[key] = value
    except (OSError, UnicodeError, csv.Error) as exc:
        raise ValueError(f"не удалось прочитать CSV {path}: {exc}") from exc
    missing = sorted(NEEDED - values.keys())
    if missing:
        raise ValueError("в CSV отсутствуют параметры: " + ", ".join(missing))
    values["test_mode"] = values["test_mode"].lower()
    values["ascii_tree"] = values["ascii_tree"].lower()
    problems = ValidateEVERYTHING(values["package"], values["repository"], values["test_mode"], values["ascii_tree"], path, 214, 2048, values.keys())
    if problems:
        raise ValueError("\n".join(problems))
    return values


def send_config(values):
    base = os.environ.get("DEPENDENCY_HELL_API_URL", "http://127.0.0.1:8080").rstrip("/")
    body = json.dumps({
        "packageName": values["package"],
        "repository": values["repository"],
        "testMode": values["test_mode"] == "true",
        "asciiTree": values["ascii_tree"] == "true",
    }).encode("utf-8")
    request = urllib.request.Request(base + "/api/runs", data=body, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            payload = json.load(response)
            if not isinstance(payload, dict) or not isinstance(payload.get("id"), str) or not payload["id"]:
                raise ValueError("сервер вернул ответ без идентификатора запуска")
            return payload
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        try:
            detail = json.loads(detail).get("error", detail)
        except (json.JSONDecodeError, AttributeError):
            pass
        raise ValueError(f"сервер отклонил настройки (HTTP {exc.code}): {detail}") from exc
    except (OSError, json.JSONDecodeError, UnicodeError) as exc:
        raise ValueError(f"не удалось связаться с API {base}: {exc}") from exc


def main(argv=None):
    parser = argparse.ArgumentParser(description="Этап 3: граф зависимостей npm из CSV-конфигурации")
    parser.add_argument("config", nargs="?", default="config.csv", help="путь к CSV (по умолчанию config.csv)")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--local", action="store_true", help="проверить CSV без запуска API и базы")
    modes.add_argument("--direct", action="store_true", help="вывести только прямые зависимости как на этапе 2")
    args = parser.parse_args(argv)
    try:
        values = read_config(args.config)
        if args.local:
            result = None
        else:
            if args.direct:
                if values["test_mode"] == "true":
                    raise ValueError("--direct поддерживает только npm-реестр с test_mode=false")
                from stage2 import fetch_direct_dependencies, print_direct_dependencies
                result = send_config(values)
                direct = fetch_direct_dependencies(result["id"])
            else:
                from stage3 import read_test_graph, fetch_graph, print_graph
                test_graph = read_test_graph(args.config, values["repository"], values["package"]) if values["test_mode"] == "true" else None
                result = send_config(values)
                graph = fetch_graph(result["id"], test_graph)
    except ValueError as exc:
        print(f"Ошибка: {exc}", file=sys.stderr)
        return 2
    if args.local:
        for key in ("package", "repository", "test_mode", "ascii_tree"):
            print(f"{key}={values[key]}")
    elif args.direct:
        print_direct_dependencies(direct, result["id"])
    else:
        print_graph(graph, result["id"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
