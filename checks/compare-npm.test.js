const { test } = require("node:test");
const assert = require("node:assert/strict");
const { compare } = require("../javascript/compare-npm.js");

const ours = { graph: {
    root: "@scope/root@1.0.0",
    nodes: [{id:"@scope/root@1.0.0", name:"@scope/root", version:"1.0.0"}, {id:"child@2.0.0", name:"child", version:"2.0.0"}],
    edges: [{from:"@scope/root@1.0.0", to:"child@2.0.0", cycle:false}],
}, order:{possible:true, packages:["child@2.0.0", "@scope/root@1.0.0"], cycleEdges:[]} };

test("compares npm subtree including scoped names", () => {
    const npm = {name:"check-project", dependencies:{"@scope/root":{version:"1.0.0", dependencies:{child:{version:"2.0.0"}}}}};
    const result = compare(ours, npm);
    assert.deepEqual(result.onlyOurs, []);
    assert.deepEqual(result.onlyNpm, []);
    assert.deepEqual(result.edgesOnlyOurs, []);
    assert.deepEqual(result.edgesOnlyNpm, []);
    assert.equal(result.checkedEdges, 1);
    assert.deepEqual(result.orderViolations, []);
});

test("reports version and graph differences", () => {
    const npm = {dependencies:{"@scope/root":{version:"1.0.0", dependencies:{child:{version:"3.0.0"}}}}};
    const result = compare(ours, npm);
    assert.deepEqual(result.onlyOurs, ["child@2.0.0"]);
    assert.deepEqual(result.onlyNpm, ["child@3.0.0"]);
    assert.deepEqual(result.edgesOnlyOurs, ["@scope/root@1.0.0 -> child@2.0.0"]);
    assert.deepEqual(result.unverifiedEdges, ["@scope/root@1.0.0 -> child@3.0.0"]);
});

test("compares the returned order against npm edges", () => {
    const npm = {dependencies:{"@scope/root":{version:"1.0.0", dependencies:{child:{version:"2.0.0"}}}}};
    const wrong = {...ours, order:{possible:true, packages:["@scope/root@1.0.0", "child@2.0.0"], cycleEdges:[]}};
    assert.deepEqual(compare(wrong, npm).orderViolations, ["@scope/root@1.0.0 -> child@2.0.0"]);
});

test("reports packages missing versions and validates the root", () => {
    const npm = {dependencies:{"@scope/root":{version:"1.0.0", dependencies:{child:{missing:true}}}}};
    assert.deepEqual(compare(ours, npm).incomplete, ["child"]);
    assert.throws(() => compare(ours, {dependencies:{}}), /не найден/);
});
