"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { once } = require("node:events");
const { buildAsciiTree, createServer } = require("./tree-service.js");

function graph(root, pairs) {
    const ids = new Set([root]);
    const edges = pairs.map(([from, to]) => { ids.add(from); ids.add(to); return { from, to, cycle: false }; });
    return { root, nodes: [...ids].map(id => ({ id })), edges };
}

test("diamond shows the shared dependency without inventing a cycle", () => {
    assert.equal(buildAsciiTree(graph("A", [["A", "B"], ["B", "D"], ["A", "C"], ["C", "D"]])),
        "A\n|-- B\n|   `-- D\n`-- C\n    `-- D [уже показан]");
});

test("marks cycles and self-loops without recursion", () => {
    assert.equal(buildAsciiTree(graph("A", [["A", "B"], ["B", "A"]])), "A\n`-- B\n    `-- A [цикл]");
    assert.equal(buildAsciiTree(graph("A", [["A", "A"]])), "A\n`-- A [цикл]");
    assert.equal(buildAsciiTree(graph("only", [])), "only");
});

test("handles a long chain and rejects invalid input", () => {
    const pairs = Array.from({ length: 1000 }, (_, i) => [`P${i}`, `P${i + 1}`]);
    assert.match(buildAsciiTree(graph("P0", pairs)), /P1000$/);
    assert.throws(() => buildAsciiTree({ root:"A", nodes:[{id:"A"}], edges:[{from:"A",to:"B"}] }), /неизвестной/);
    assert.throws(() => buildAsciiTree({ root:"A", nodes:[{id:"A"},{id:"B"}], edges:[] }), /недостижимые/);
    const tooLong = Array.from({ length: 1500 }, (_, i) => [`P${i}`, `P${i + 1}`]);
    assert.throws(() => buildAsciiTree(graph("P0", tooLong)), /превышает 4 МиБ/);
});

test("HTTP contract exposes JSON and rejects broken requests", async () => {
    const server = createServer().listen(0, "127.0.0.1");
    await once(server, "listening");
    try {
        const address = `http://127.0.0.1:${server.address().port}`;
        let response = await fetch(`${address}/health`);
        assert.equal(response.status, 200);
        response = await fetch(`${address}/api/ascii-tree`, { method:"POST", body:JSON.stringify(graph("A", [["A","B"]])) });
        assert.deepEqual(await response.json(), { asciiTree:"A\n`-- B" });
        response = await fetch(`${address}/api/ascii-tree`, { method:"POST", body:"{" });
        assert.equal(response.status, 400);
    } finally { server.close(); await once(server, "close"); }
});
