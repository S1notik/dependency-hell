package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.TreeClient;
import ru.dependencyhell.model.ApiModels.*;
import ru.dependencyhell.repository.RunRepository;
import ru.dependencyhell.repository.VisualizationRepository;
import ru.dependencyhell.service.ConfigurationService;
import ru.dependencyhell.service.GraphService;
import ru.dependencyhell.service.VisualizationService;
import ru.dependencyhell.service.VisualizationStorageService;

class VisualizationServiceTest {
    static class FakeConfig extends ConfigurationService {
        final boolean ascii;
        FakeConfig(boolean ascii) { super(new RunRepository(new JdbcTemplate())); this.ascii = ascii; }
        @Override public Run find(String id) { return new Run(id, "A", "graph.json", true, ascii, "COMPLETED", 3, null); }
    }
    static class FakeGraph extends GraphService {
        final GraphResult value;
        FakeGraph(GraphResult value) { super(null, null, null, null); this.value = value; }
        @Override public GraphResult getSaved(String id) { return value; }
    }
    static class FakeTree extends TreeClient {
        int calls;
        FakeTree() { super("http://127.0.0.1:8084"); }
        @Override public String render(GraphResult graph) { calls++; return "A\n`-- B"; }
    }
    static class FakeRepo extends VisualizationRepository {
        Visualization value;
        FakeRepo() { super(new JdbcTemplate()); }
        @Override public void save(String id, Visualization result) { value = result; }
        @Override public Optional<Visualization> find(String id) { return Optional.ofNullable(value); }
    }
    static GraphResult graph(boolean cycle) {
        var edges = cycle ? List.of(new GraphEdge("A", "B", false), new GraphEdge("B", "A", true)) : List.of(new GraphEdge("A", "B", false));
        return new GraphResult("A", List.of(new GraphNode("A", "A", null), new GraphNode("B", "B", null)), edges, List.of("A", "B"));
    }
    static VisualizationService service(boolean ascii, GraphResult graph, FakeTree tree, FakeRepo repo) {
        return new VisualizationService(new FakeConfig(ascii), new FakeGraph(graph), tree, repo, new VisualizationStorageService(repo), KotlinImplementations.mermaid());
    }
    @Test void savesBothFormatsWhenEnabled() {
        FakeTree tree = new FakeTree(); FakeRepo repo = new FakeRepo();
        VisualizationService service = service(true, graph(false), tree, repo);
        Visualization actual = service.build("run");
        assertEquals("A\n`-- B", actual.asciiTree());
        assertTrue(actual.mermaid().contains("n0 --> n1"));
        assertEquals(1, tree.calls);
        assertEquals(actual, service.getSaved("run"));
    }
    @Test void skipsAsciiServiceWhenDisabledAndKeepsCycle() {
        FakeTree tree = new FakeTree(); FakeRepo repo = new FakeRepo();
        Visualization result = service(false, graph(true), tree, repo).build("run");
        assertNull(result.asciiTree());
        assertEquals(0, tree.calls);
        assertTrue(result.mermaid().contains("n1 -->|цикл| n0"));
    }
    @Test void unsavedVisualizationReturnsConflict() {
        assertThrows(ResponseStatusException.class, () -> service(false, graph(false), new FakeTree(), new FakeRepo()).getSaved("run"));
    }
}
