package ru.dependencyhell.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.TreeClient;
import ru.dependencyhell.model.ApiModels.GraphEdge;
import ru.dependencyhell.model.ApiModels.GraphNode;
import ru.dependencyhell.model.ApiModels.GraphResult;
import ru.dependencyhell.model.ApiModels.Run;
import ru.dependencyhell.model.ApiModels.Visualization;
import ru.dependencyhell.repository.VisualizationRepository;
import ru.dependencyhell.visualization.MermaidFormatter;

@Service
public class VisualizationService {
    private final ConfigurationService configurations;
    private final GraphService graphs;
    private final TreeClient trees;
    private final VisualizationRepository repository;
    private final VisualizationStorageService storage;
    private final MermaidFormatter formatter;

    public VisualizationService(ConfigurationService configurations, GraphService graphs, TreeClient trees,
                                VisualizationRepository repository, VisualizationStorageService storage, MermaidFormatter formatter) {
        this.configurations = configurations;
        this.graphs = graphs;
        this.trees = trees;
        this.repository = repository;
        this.storage = storage;
        this.formatter = formatter;
    }

    public Visualization build(String id) {
        Run run = configurations.find(id);
        GraphResult graph = graphs.getSaved(id);
        String mermaid = formatter.render(graph.nodes().stream().map(GraphNode::id).toList(),
            graph.edges().stream().map(GraphEdge::from).toList(), graph.edges().stream().map(GraphEdge::to).toList(),
            graph.edges().stream().map(GraphEdge::cycle).toList());
        String ascii = Boolean.TRUE.equals(run.asciiTree()) ? trees.render(graph) : null;
        Visualization result = new Visualization(mermaid, ascii);
        storage.save(id, result);
        return result;
    }

    public Visualization getSaved(String id) {
        graphs.getSaved(id);
        return repository.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Текст графа ещё не сформирован"));
    }
}
