package ru.dependencyhell.visualization

import org.springframework.stereotype.Component

// Идентификаторы графа заменяем на n0, n1, чтобы имена npm не ломали синтаксис Mermaid.
@Component
class MermaidRenderer : MermaidFormatter {
    override fun render(nodes: List<String>, from: List<String>, to: List<String>, cycles: List<Boolean>): String {
        val nodeCount = nodes.size
        val edgeCount = from.size
        if (nodeCount == 0) {
            throw IllegalArgumentException("Некорректный размер графа")
        }
        if (nodeCount > 10000) {
            throw IllegalArgumentException("Некорректный размер графа")
        }
        if (edgeCount > 50000) {
            throw IllegalArgumentException("Некорректный размер графа")
        }
        if (edgeCount != to.size) {
            throw IllegalArgumentException("Некорректные рёбра графа")
        }
        if (edgeCount != cycles.size) {
            throw IllegalArgumentException("Некорректные рёбра графа")
        }

        val keys = LinkedHashMap<String, String>()
        for ((index, name) in nodes.withIndex()) {
            if (name.isEmpty()) {
                throw IllegalArgumentException("Некорректная или повторная вершина")
            }
            if (keys.containsKey(name)) {
                throw IllegalArgumentException("Некорректная или повторная вершина")
            }
            keys[name] = "n$index"
        }

        val result = StringBuilder()
        result.append("flowchart TD\n")
        for ((label, key) in keys) {
            val escapedLabel = escape(label)
            result.append("    ")
            result.append(key)
            result.append("[\"")
            result.append(escapedLabel)
            result.append("\"]\n")
        }

        for (index in from.indices) {
            val source = from[index]
            val destination = to[index]
            val isCycle = cycles[index]
            val regularArrow = " --> "
            val markedArrow = " -->|цикл| "
            append_edge(
                result,
                keys,
                source,
                destination,
                isCycle,
                regularArrow,
                markedArrow,
                '\n'
            )
        }
        val rendered = result.toString()
        return rendered.trimEnd()
    }

    private fun append_edge(
        result: StringBuilder,
        keys: Map<String, String>,
        from: String,
        to: String,
        cycle: Boolean,
        normalArrow: String,
        cycleArrow: String,
        newline: Char
    ) {
        val left = requireNotNull(keys[from]) { "Неизвестная вершина ребра" }
        val right = requireNotNull(keys[to]) { "Неизвестная зависимость ребра" }
        result.append("    ").append(left)
        result.append(if (cycle) cycleArrow else normalArrow)
        result.append(right).append(newline)
    }

    private fun escape(value: String): String = buildString {
        for (c in value) append(when (c) {
            '#' -> "#35;"
            '"' -> "#quot;"
            '&' -> "#amp;"
            '<' -> "#lt;"
            '>' -> "#gt;"
            '\r', '\n', '\t' -> " "
            else -> c.toString()
        })
    }
}
