package ru.dependencyhell.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.GraphClient;
import ru.dependencyhell.model.ApiModels.GraphEdge;
import ru.dependencyhell.model.ApiModels.GraphNode;
import ru.dependencyhell.model.ApiModels.GraphResult;
import ru.dependencyhell.model.ApiModels.LoadOrder;
import ru.dependencyhell.order.OrderValidator;
import ru.dependencyhell.repository.LoadOrderRepository;

@Service
public class LoadOrderService {
    private final GraphService graphs;
    private final GraphClient graphClient;
    private final LoadOrderRepository repository;
    private final LoadOrderStorageService storage;
    private final OrderValidator validator;

    public LoadOrderService(GraphService graphs, GraphClient graphClient, LoadOrderRepository repository, LoadOrderStorageService storage, OrderValidator validator) {
        this.graphs = graphs;
        this.graphClient = graphClient;
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
    }

    public LoadOrder build(String id) {
        GraphResult graph = graphs.getSaved(id);
        LoadOrder result = graphClient.order(graph);
        validate(graph, result);
        storage.save(id, result);
        return result;
    }

    public LoadOrder getSaved(String id) {
        graphs.getSaved(id);
        return repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Порядок ещё не рассчитан"));
    }

    private void validate(GraphResult graph, LoadOrder order) {
        if (order == null || order.possible() == null || order.packages() == null || order.cycleEdges() == null)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул неполный порядок загрузки");
        List<GraphEdge> cycles = graph.edges().stream().filter(GraphEdge::cycle).toList();
        if (!order.cycleEdges().equals(cycles))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул неверные циклы");
        if (!order.possible()) {
            if (cycles.isEmpty() || !order.packages().isEmpty())
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust неверно обработал цикл");
            return;
        }
        if (!cycles.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "При цикле строгий порядок невозможен");
        Map<String, List<String>> dependencies = new HashMap<>();
        for (GraphEdge edge : graph.edges()) dependencies.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge.to());
        String problem = validator.problem(graph.nodes().stream().map(GraphNode::id).toList(), dependencies, order.packages());
        if (problem != null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Некорректный порядок: " + problem);
    }
}
