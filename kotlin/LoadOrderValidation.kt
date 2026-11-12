package ru.dependencyhell.order

import org.springframework.stereotype.Component

// Проверяем ответ Rust перед сохранением: зависимость должна стоять раньше своего потребителя.
@Component
class LoadOrderValidation : OrderValidator {
    override fun problem(nodes: List<String>, dependencies: Map<String, List<String>>, order: List<String>): String? {
        if (nodes.size != nodes.toSet().size || order.size != nodes.size || order.toSet() != nodes.toSet()) {
            return "порядок должен содержать каждую вершину ровно один раз"
        }
        val position = order.withIndex().associate { it.value to it.index }
        for ((dependent, requiredPackages) in dependencies) {
            val dependentPosition = position[dependent] ?: return "неизвестный пакет: $dependent"
            for (required in requiredPackages) {
                val requiredPosition = position[required] ?: return "неизвестная зависимость: $required"
                if (requiredPosition >= dependentPosition) return "зависимость $required должна идти раньше $dependent"
            }
        }
        return null
    }
}
