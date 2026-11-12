#!/usr/bin/env node
"use strict";

const fs = require("node:fs");

// npm ls показывает логический граф, поэтому сравниваем состав и связи, а не время скачивания.
function compare(ours, npm) {
    if (!ours || !ours.graph || !Array.isArray(ours.graph.nodes) || !Array.isArray(ours.graph.edges)) {
        throw new Error("первый файл должен содержать JSON этапа 4 (--json)");
    }
    const graph = ours.graph;
    const root = graph.nodes.find(node => node.id === graph.root);
    if (!root || typeof root.name !== "string" || typeof root.version !== "string") {
        throw new Error("сравнение с npm доступно для графа реального пакета с версией");
    }
    if (!npm || typeof npm !== "object" || Array.isArray(npm)) throw new Error("неверный JSON npm ls");
    const npmRoot = npm.name === root.name && typeof npm.version === "string"
        ? npm : npm.dependencies && npm.dependencies[root.name];
    if (!npmRoot || typeof npmRoot !== "object") throw new Error(`пакет ${root.name} не найден в npm ls`);

    const actualNodes = new Set();
    const actualEdges = new Set();
    const incomplete = new Set();
    const pending = [{ name: root.name, node: npmRoot, parent: null }];
    while (pending.length > 0) {
        const item = pending.pop();
        if (!item.node || typeof item.node !== "object" || typeof item.node.version !== "string") {
            incomplete.add(item.name);
            continue;
        }
        // npm может указать исходное имя при установке зависимости под псевдонимом.
        const name = typeof item.node.name === "string" && item.node.name ? item.node.name : item.name;
        const id = `${name}@${item.node.version}`;
        actualNodes.add(id);
        if (item.parent !== null) actualEdges.add(`${item.parent}\u0000${id}`);
        const children = item.node.dependencies || {};
        if (typeof children !== "object" || Array.isArray(children)) throw new Error(`неверные зависимости npm для ${id}`);
        for (const [childName, child] of Object.entries(children)) pending.push({ name: childName, node: child, parent: id });
    }
    const ourNodes = new Set(graph.nodes.map(node => node.id));
    const ourEdges = new Set(graph.edges.map(edge => `${edge.from}\u0000${edge.to}`));
    const subtract = (left, right) => [...left].filter(item => !right.has(item)).sort();
    if (!ours.order || typeof ours.order.possible !== "boolean" || !Array.isArray(ours.order.packages)) {
        throw new Error("в первом файле нет результата порядка загрузки");
    }
    const positions = new Map(ours.order.packages.map((id, index) => [id, index]));
    const orderViolations = [];
    const unverifiedEdges = [];
    let checkedEdges = 0;
    if (ours.order.possible) {
        for (const edge of actualEdges) {
            const [parent, child] = edge.split("\u0000");
            const label = `${parent} -> ${child}`;
            if (!positions.has(parent) || !positions.has(child)) unverifiedEdges.push(label);
            else {
                checkedEdges++;
                if (positions.get(child) >= positions.get(parent)) orderViolations.push(label);
            }
        }
    }
    return {
        onlyOurs: subtract(ourNodes, actualNodes),
        onlyNpm: subtract(actualNodes, ourNodes),
        edgesOnlyOurs: subtract(ourEdges, actualEdges).map(edge => edge.replace("\u0000", " -> ")),
        edgesOnlyNpm: subtract(actualEdges, ourEdges).map(edge => edge.replace("\u0000", " -> ")),
        incomplete: [...incomplete].sort(),
        oursCount: ourNodes.size,
        npmCount: actualNodes.size,
        orderPossible: ours.order.possible,
        checkedEdges,
        orderViolations: orderViolations.sort(),
        unverifiedEdges: unverifiedEdges.sort(),
    };
}

function main(args) {
    if (args.length !== 2) throw new Error("использование: node javascript/compare-npm.js наш-граф.json npm-ls.json");
    const ours = JSON.parse(fs.readFileSync(args[0], "utf8"));
    const npm = JSON.parse(fs.readFileSync(args[1], "utf8"));
    const result = compare(ours, npm);
    console.log(`Пакетов: наш граф — ${result.oursCount}, npm — ${result.npmCount}`);
    for (const [label, values] of [
        ["Только в нашем графе", result.onlyOurs], ["Только в npm", result.onlyNpm],
        ["Связи только в нашем графе", result.edgesOnlyOurs], ["Связи только в npm", result.edgesOnlyNpm],
        ["Связи npm, нарушающие наш порядок", result.orderViolations], ["Связи npm, отсутствующие в порядке", result.unverifiedEdges],
        ["Неполные данные npm (нет версии)", result.incomplete],
    ]) console.log(`${label}: ${values.length ? values.join(", ") : "нет"}`);
    if (result.orderPossible) console.log(`Проверено связей npm на порядок: ${result.checkedEdges}`);
    else console.log("Строгий порядок не получен из-за циклических зависимостей.");
    console.log("npm ls показывает логическое дерево, а не фактическую хронологию скачивания.");
    console.log("Различия возможны из-за lock-файла, peer/optional-зависимостей, платформы и дедупликации npm.");
}

if (require.main === module) {
    try { main(process.argv.slice(2)); }
    catch (error) { console.error(`Ошибка сравнения: ${error.message}`); process.exitCode = 2; }
}
module.exports = { compare };
