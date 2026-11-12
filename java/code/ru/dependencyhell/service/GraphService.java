package ru.dependencyhell.service;

import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.GraphClient;
import ru.dependencyhell.model.ApiModels.GraphEdge;
import ru.dependencyhell.model.ApiModels.GraphInput;
import ru.dependencyhell.model.ApiModels.GraphNode;
import ru.dependencyhell.model.ApiModels.GraphRequest;
import ru.dependencyhell.model.ApiModels.GraphResult;
import ru.dependencyhell.model.ApiModels.Run;
import ru.dependencyhell.repository.GraphRepository;

@Service
public class GraphService {
    private final ConfigurationService configurations;
    private final GraphClient graphClient;
    private final GraphRepository repository;
    private final GraphStorageService storage;

    public GraphService(ConfigurationService configurations, GraphClient graphClient, GraphRepository repository, GraphStorageService storage) {
        this.configurations = configurations;
        this.graphClient = graphClient;
        this.repository = repository;
        this.storage = storage;
    }

    public GraphResult build(String id, GraphInput input) {
        Run run = configurations.find(id);
        if (Boolean.TRUE.equals(run.testMode()) && (input == null || input.testGraph() == null))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не передан тестовый граф");
        GraphRequest request = new GraphRequest(run.packageName(), Boolean.TRUE.equals(run.testMode()), input == null ? null : input.testGraph(), run.repository());
        GraphResult graph = graphClient.build(request);
        validate(graph, run);
        storage.save(id, graph);
        return graph;
    }

    public GraphResult getSaved(String id) {
        configurations.find(id);
        return repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Граф ещё не построен"));
    }

    private void validate(GraphResult graph, Run run) {
        if (graph.nodes().isEmpty() || graph.nodes().size() > 10000 || graph.edges().size() > 50000 || graph.visitOrder().size() != graph.nodes().size())
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул некорректный размер графа");
        Set<String> ids = new HashSet<>();
        for (GraphNode node : graph.nodes()) {
            if (node == null || node.id() == null || node.id().length() > 512 || node.name() == null || node.name().length() > 214 || (node.version() != null && node.version().length() > 255) || !ids.add(node.id()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул некорректные вершины");
        }
        if (!ids.contains(graph.root()) || !graph.nodes().get(0).id().equals(graph.root()) || !graph.nodes().get(0).name().equals(run.packageName()))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул неверный корень графа");
        Set<String> order = new HashSet<>(graph.visitOrder());
        if (order.size() != graph.visitOrder().size() || !order.equals(ids) || !graph.visitOrder().get(0).equals(graph.root()))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул некорректный порядок обхода");
        for (int i = 0; i < graph.nodes().size(); i++) {
            if (!graph.nodes().get(i).id().equals(graph.visitOrder().get(i)))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Порядок вершин не совпадает с DFS");
        }
        Set<String> edges = new HashSet<>();
        for (GraphEdge edge : graph.edges()) {
            if (edge == null || !ids.contains(edge.from()) || !ids.contains(edge.to()) || !edges.add(edge.from() + "\u0000" + edge.to()))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Rust вернул некорректные связи");
        }
    }
}
