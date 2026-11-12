package ru.dependencyhell.visualization

import org.springframework.stereotype.Component

// Идентификаторы графа заменяем на n0, n1, чтобы имена npm не ломали синтаксис Mermaid.
@Component
class MermaidRenderer : MermaidFormatter {
    override fun render(nodes: List<String>, from: List<String>, to: List<String>, cycles: List<Boolean>): String {
        require(nodes.isNotEmpty() && nodes.size <= 10000 && from.size <= 50000) { "Некорректный размер графа" }
        require(from.size == to.size && from.size == cycles.size) { "Некорректные рёбра графа" }
        val keys = LinkedHashMap<String, String>()
        nodes.forEachIndexed { index, name ->
            require(name.isNotEmpty() && !keys.containsKey(name)) { "Некорректная или повторная вершина" }
            keys[name] = "n$index"
        }
        return buildString {
            append("flowchart TD\n")
            for ((label, key) in keys) {
                append("    ").append(key).append("[\"").append(escape(label)).append("\"]\n")
            }
            for (i in from.indices) {
                val left = requireNotNull(keys[from[i]]) { "Неизвестная вершина ребра" }
                val right = requireNotNull(keys[to[i]]) { "Неизвестная зависимость ребра" }
                append("    ").append(left).append(if (cycles[i]) " -->|цикл| " else " --> ").append(right).append('\n')
            }
        }.trimEnd()
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
