import html
import math
import tempfile
import textwrap
import webbrowser
from collections import deque
from pathlib import Path


NODE_WIDTH = 260
NODE_GAP = 45
LAYER_GAP = 100
LINE_HEIGHT = 20
MARGIN = 60
TOP = 120


def graph_layout(graph):
    if not isinstance(graph, dict):
        raise ValueError("некорректный граф для изображения")
    root, nodes, edges = graph.get("root"), graph.get("nodes"), graph.get("edges")
    if not isinstance(root, str) or not isinstance(nodes, list) or not isinstance(edges, list):
        raise ValueError("некорректный граф для изображения")
    if not nodes or len(nodes) > 10000 or len(edges) > 50000:
        raise ValueError("некорректный размер графа для изображения")

    names = []
    children = {}
    for node in nodes:
        name = node.get("id") if isinstance(node, dict) else None
        if (not isinstance(name, str) or not name or len(name) > 512 or
                any(ord(ch) < 32 or ord(ch) == 127 for ch in name) or name in children):
            raise ValueError("некорректная или повторяющаяся вершина графа")
        names.append(name)
        children[name] = []
    if root not in children:
        raise ValueError("корень отсутствует в графе")

    seen_edges = set()
    for edge in edges:
        if not isinstance(edge, dict):
            raise ValueError("некорректное ребро графа")
        source, target = edge.get("from"), edge.get("to")
        if not isinstance(source, str) or not isinstance(target, str):
            raise ValueError("некорректное ребро графа")
        if source not in children or target not in children or not isinstance(edge.get("cycle"), bool):
            raise ValueError("ребро с неизвестной вершиной или неверной отметкой цикла")
        if (source, target) in seen_edges:
            raise ValueError("повторяющееся ребро графа")
        seen_edges.add((source, target))
        children[source].append(target)

    depth = {root: 0}
    pending = deque([root])
    while pending:
        parent = pending.popleft()
        for child in children[parent]:
            if child not in depth:
                depth[child] = depth[parent] + 1
                pending.append(child)
    if len(depth) != len(names):
        raise ValueError("граф содержит недостижимые вершины")

    layers = [[] for _ in range(max(depth.values()) + 1)]
    labels = {}
    heights = {}
    for name in names:
        layers[depth[name]].append(name)
        labels[name] = textwrap.wrap(name, width=30, break_long_words=True,
                                     break_on_hyphens=False, drop_whitespace=False) or [name]
        heights[name] = max(56, 26 + len(labels[name]) * LINE_HEIGHT)

    widest = max(len(layer) for layer in layers)
    content_width = max(500, 2 * MARGIN + widest * NODE_WIDTH + (widest - 1) * NODE_GAP)
    needs_right_margin = any(edge["from"] == edge["to"] or depth[edge["to"]] < depth[edge["from"]] for edge in edges)
    width = content_width + (260 if needs_right_margin else 0)
    positions = {}
    y = TOP
    for layer in layers:
        row_width = len(layer) * NODE_WIDTH + (len(layer) - 1) * NODE_GAP
        x = (content_width - row_width) / 2
        row_height = max(heights[name] for name in layer)
        for name in layer:
            positions[name] = (x, y + (row_height - heights[name]) / 2, heights[name])
            x += NODE_WIDTH + NODE_GAP
        y += row_height + LAYER_GAP
    return names, edges, root, labels, depth, positions, width, y - LAYER_GAP + MARGIN, content_width


def render_svg(graph):
    names, edges, root, labels, depth, positions, width, height, content_width = graph_layout(graph)
    escape = lambda value: html.escape(value, quote=True)
    output = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.0f}" height="{height:.0f}" '
        f'viewBox="0 0 {width:.0f} {height:.0f}" role="img" aria-labelledby="title description">',
        '<title id="title">Граф зависимостей</title>',
        '<desc id="description">Стрелка указывает от пакета к его зависимости. '
        'Красная пунктирная связь замыкает цикл.</desc>',
        f'<rect width="{width:.0f}" height="{height:.0f}" fill="#f8fafc"/>',
        '<text x="32" y="42" font-family="sans-serif" font-size="24" font-weight="700" fill="#0f172a">Граф зависимостей</text>',
        '<text x="32" y="72" font-family="sans-serif" font-size="14" fill="#475569">Стрелка: пакет → зависимость. Красный пунктир: цикл.</text>',
        '<g fill="none" stroke-width="2.2">',
    ]
    backward_index = 0
    same_level_index = 0
    for edge in edges:
        source, target, cycle = edge["from"], edge["to"], edge["cycle"]
        sx, sy, sh = positions[source]
        tx, ty, th = positions[target]
        if source == target:
            start_x, start_y = sx + NODE_WIDTH, sy + sh * 0.30
            end_x, end_y = sx + NODE_WIDTH + 2, sy + sh * 0.70
            rail = sx + NODE_WIDTH + 75
            control_x, control_y = rail, end_y + 25
            path = f'M {start_x:.1f} {start_y:.1f} C {rail:.1f} {start_y - 25:.1f}, {control_x:.1f} {control_y:.1f}, {end_x:.1f} {end_y:.1f}'
        elif depth[target] < depth[source]:
            start_x, start_y = sx + NODE_WIDTH, sy + sh / 2
            end_x, end_y = tx + NODE_WIDTH + 2, ty + th / 2
            rail = content_width + 50 + (backward_index % 8) * 24
            backward_index += 1
            control_x, control_y = rail, end_y
            path = f'M {start_x:.1f} {start_y:.1f} C {rail:.1f} {start_y:.1f}, {control_x:.1f} {control_y:.1f}, {end_x:.1f} {end_y:.1f}'
        elif depth[target] == depth[source]:
            start_x, start_y = sx + NODE_WIDTH / 2, sy + sh
            end_x, end_y = tx + NODE_WIDTH / 2, ty + th + 2
            rail = max(start_y, end_y) + 35 + (same_level_index % 4) * 14
            same_level_index += 1
            control_x, control_y = end_x, rail
            path = f'M {start_x:.1f} {start_y:.1f} C {start_x:.1f} {rail:.1f}, {control_x:.1f} {control_y:.1f}, {end_x:.1f} {end_y:.1f}'
        else:
            start_x, start_y = sx + NODE_WIDTH / 2, sy + sh
            end_x, end_y = tx + NODE_WIDTH / 2, ty - 2
            middle = (start_y + end_y) / 2
            control_x, control_y = end_x, middle
            path = f'M {start_x:.1f} {start_y:.1f} C {start_x:.1f} {middle:.1f}, {control_x:.1f} {control_y:.1f}, {end_x:.1f} {end_y:.1f}'
        style = 'stroke="#dc2626" stroke-dasharray="8 5"' if cycle else 'stroke="#475569"'
        kind = "cycle" if cycle else "regular"
        output.append(f'<path class="edge {kind}" data-from="{escape(source)}" data-to="{escape(target)}" d="{path}" {style}/>')
        dx, dy = end_x - control_x, end_y - control_y
        length = math.hypot(dx, dy)
        ux, uy = dx / length, dy / length
        base_x, base_y = end_x - ux * 13, end_y - uy * 13
        points = f'{end_x:.1f},{end_y:.1f} {base_x - uy * 5:.1f},{base_y + ux * 5:.1f} {base_x + uy * 5:.1f},{base_y - ux * 5:.1f}'
        fill = "#dc2626" if cycle else "#475569"
        output.append(f'<polygon class="arrow {kind}" points="{points}" fill="{fill}" stroke="none"/>')
    output.append('</g>')
    for name in names:
        x, y, box_height = positions[name]
        is_root = name == root
        fill = "#dbeafe" if is_root else "#ffffff"
        stroke = "#2563eb" if is_root else "#94a3b8"
        output.append(f'<g class="node" data-id="{escape(name)}">')
        output.append(f'<title>{escape(name)}</title>')
        output.append(f'<rect x="{x:.1f}" y="{y:.1f}" width="{NODE_WIDTH}" height="{box_height}" rx="10" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
        first_y = y + box_height / 2 - (len(labels[name]) - 1) * LINE_HEIGHT / 2
        for index, line in enumerate(labels[name]):
            output.append(f'<text x="{x + NODE_WIDTH / 2:.1f}" y="{first_y + index * LINE_HEIGHT:.1f}" text-anchor="middle" dominant-baseline="middle" font-family="sans-serif" font-size="14" fill="#0f172a">{escape(line)}</text>')
        output.append('</g>')
    output.append('</svg>')
    return "\n".join(output) + "\n"


def save_svg(graph, destination=None):
    svg = render_svg(graph)
    try:
        if destination is None:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", prefix="dependency-hell-", suffix=".svg", delete=False) as file:
                file.write(svg)
                return Path(file.name).resolve()
        path = Path(destination).expanduser().resolve()
        with path.open("x", encoding="utf-8") as file:
            file.write(svg)
        return path
    except OSError as exc:
        raise ValueError(f"не удалось сохранить изображение SVG: {exc}") from exc


def open_svg(path):
    try:
        return webbrowser.open(path.resolve().as_uri())
    except (OSError, ValueError, webbrowser.Error):
        return False
