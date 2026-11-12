package ru.dependencyhell;

import ru.dependencyhell.order.OrderValidator;
import ru.dependencyhell.visualization.MermaidFormatter;

final class KotlinImplementations {
    private KotlinImplementations() { }

    static OrderValidator orderValidator() { return create("ru.dependencyhell.order.LoadOrderValidation", OrderValidator.class); }
    static MermaidFormatter mermaid() { return create("ru.dependencyhell.visualization.MermaidRenderer", MermaidFormatter.class); }

    private static <T> T create(String name, Class<T> type) {
        try { return type.cast(Class.forName(name).getDeclaredConstructor().newInstance()); }
        catch (ReflectiveOperationException error) { throw new AssertionError("Kotlin-компонент не собран: " + name, error); }
    }
}
