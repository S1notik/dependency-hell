package ru.dependencyhell.order;

import java.util.List;
import java.util.Map;

public interface OrderValidator {
    String problem(List<String> nodes, Map<String, List<String>> dependencies, List<String> order);
}
