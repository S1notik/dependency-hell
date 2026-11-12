package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.dependencyhell.visualization.MermaidFormatter;

class MermaidRendererTest {
    private final MermaidFormatter renderer = KotlinImplementations.mermaid();
    @Test void emitsNodesEdgesAndCycle() {
        String actual = renderer.render(List.of("A", "B", "C"), List.of("A", "B", "C"), List.of("B", "C", "A"), List.of(false, false, true));
        assertEquals("flowchart TD\n    n0[\"A\"]\n    n1[\"B\"]\n    n2[\"C\"]\n    n0 --> n1\n    n1 --> n2\n    n2 -->|цикл| n0", actual);
    }
    @Test void quotesAndHashAreEscapedInLabels() {
        String actual = renderer.render(List.of("@scope/a@1.0.0", "a\"#&<>"), List.of("@scope/a@1.0.0"), List.of("a\"#&<>"), List.of(false));
        assertTrue(actual.contains("n0[\"@scope/a@1.0.0\"]"));
        assertTrue(actual.contains("n1[\"a#quot;#35;#amp;#lt;#gt;\"]"));
    }
    @Test void rejectsDuplicateOrMissingVertices() {
        assertThrows(IllegalArgumentException.class, () -> renderer.render(List.of("A", "A"), List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(List.of("A"), List.of("A"), List.of("B"), List.of(false)));
    }
}
