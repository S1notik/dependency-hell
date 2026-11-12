"use strict";
const http = require("node:http");

const MAX_INPUT = 64 * 1024 * 1024;
const MAX_OUTPUT = 4 * 1024 * 1024;

function failure(message, status = 400) {
    const error = new Error(message);
    error.status = status;
    return error;
}

// Обход стека без рекурсии позволяет отличать цикл от повторно встреченной вершины.
function buildAsciiTree(graph) {
    if (!graph || typeof graph.root !== "string" || !Array.isArray(graph.nodes) || !Array.isArray(graph.edges) ||
        graph.nodes.length === 0 || graph.nodes.length > 10000 || graph.edges.length > 50000) {
        throw failure("Некорректный размер или формат графа");
    }
    const children = new Map();
    for (const node of graph.nodes) {
        if (!node || typeof node.id !== "string" || node.id.length === 0 || node.id.length > 512 || /[\x00-\x1f\x7f]/.test(node.id) || children.has(node.id)) {
            throw failure("Некорректная или повторяющаяся вершина графа");
        }
        children.set(node.id, []);
    }
    if (!children.has(graph.root)) throw failure("Корень отсутствует в графе");
    const edges = new Set();
    for (const edge of graph.edges) {
        if (!edge || !children.has(edge.from) || !children.has(edge.to)) throw failure("Ребро с неизвестной вершиной");
        const key = `${edge.from}\0${edge.to}`;
        if (edges.has(key)) throw failure("Повторяющееся ребро");
        edges.add(key);
        children.get(edge.from).push(edge.to);
    }
    const lines = [];
    let length = 0;
    function append(line) {
        length += Buffer.byteLength(line, "utf8") + (lines.length ? 1 : 0);
        if (length > MAX_OUTPUT) throw failure("ASCII-дерево превышает 4 МиБ", 413);
        lines.push(line);
    }
    append(graph.root);
    const seen = new Set([graph.root]);
    const active = new Set([graph.root]);
    const stack = [{ id: graph.root, prefix: "", next: 0 }];
    while (stack.length) {
        const frame = stack[stack.length - 1];
        const nextNodes = children.get(frame.id);
        if (frame.next >= nextNodes.length) {
            active.delete(frame.id);
            stack.pop();
            continue;
        }
        const index = frame.next++;
        const child = nextNodes[index];
        const last = index === nextNodes.length - 1;
        const marker = active.has(child) ? " [цикл]" : seen.has(child) ? " [уже показан]" : "";
        append(`${frame.prefix}${last ? "`-- " : "|-- "}${child}${marker}`);
        if (!marker) {
            seen.add(child);
            active.add(child);
            stack.push({ id: child, prefix: frame.prefix + (last ? "    " : "|   "), next: 0 });
        }
    }
    if (seen.size !== children.size) throw failure("Граф содержит недостижимые вершины");
    return lines.join("\n");
}

function createServer() {
    return http.createServer(async (request, response) => {
        const respond = (status, body) => {
            if (!response.writableEnded) response.writeHead(status, { "Content-Type": "application/json; charset=utf-8" }).end(JSON.stringify(body));
        };
        if (request.method === "GET" && request.url === "/health") return respond(200, { status: "UP" });
        if (request.method !== "POST" || request.url !== "/api/ascii-tree") return respond(404, { error: "Маршрут не найден" });
        try {
            const chunks = [];
            let length = 0;
            for await (const chunk of request) {
                length += chunk.length;
                if (length > MAX_INPUT) throw failure("Запрос больше 64 МиБ", 413);
                chunks.push(chunk);
            }
            let graph;
            try { graph = JSON.parse(Buffer.concat(chunks).toString("utf8")); }
            catch (_) { throw failure("Некорректный JSON графа"); }
            respond(200, { asciiTree: buildAsciiTree(graph) });
        } catch (error) {
            respond(error.status || 500, { error: error.message || "Ошибка формирования ASCII-дерева" });
        }
    });
}

if (require.main === module) createServer().listen(8084, "0.0.0.0", () => console.log("ASCII tree service listening on :8084"));
module.exports = { buildAsciiTree, createServer };
