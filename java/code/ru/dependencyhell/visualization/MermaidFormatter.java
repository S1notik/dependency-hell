package ru.dependencyhell.visualization;

import java.util.List;

public interface MermaidFormatter {
    String render(List<String> nodes, List<String> from, List<String> to, List<Boolean> cycles);
}
