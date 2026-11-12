package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.GraphClient;
import ru.dependencyhell.model.ApiModels.*;
import ru.dependencyhell.repository.GraphRepository;
import ru.dependencyhell.repository.RunRepository;
import ru.dependencyhell.service.ConfigurationService;
import ru.dependencyhell.service.GraphService;
import ru.dependencyhell.service.GraphStorageService;

class GraphServiceTest {
    static class FakeConfiguration extends ConfigurationService {
        FakeConfiguration() { super(new RunRepository(new JdbcTemplate())); }
        @Override public Run find(String id) { return new Run(id, "A", "graph.json", true, true, "CONFIGURED", 1, null); }
    }
    static class FakeGraphClient extends GraphClient {
        GraphResult result;
        GraphRequest request;
        FakeGraphClient(GraphResult result) { super("http://127.0.0.1:8082"); this.result = result; }
        @Override public GraphResult build(GraphRequest input) { request = input; return result; }
    }
    static class FakeRepo extends GraphRepository {
        GraphResult result;
        FakeRepo() { super(new JdbcTemplate()); }
        @Override public void save(String id, GraphResult graph) { result = graph; }
        @Override public Optional<GraphResult> find(String id) { return Optional.ofNullable(result); }
    }
    static GraphResult graph() {
        return new GraphResult("A", List.of(new GraphNode("A","A",null), new GraphNode("B","B",null)),
                List.of(new GraphEdge("A","B",false), new GraphEdge("B","A",true)), List.of("A","B"));
    }
    @Test void sendsTestGraphAndStoresCycle() {
        FakeRepo repo = new FakeRepo();
        FakeGraphClient client = new FakeGraphClient(graph());
        GraphService service = new GraphService(new FakeConfiguration(), client, repo, new GraphStorageService(repo));
        GraphResult result = service.build("id", new GraphInput(Map.of("A", List.of("B"), "B", List.of("A"))));
        assertTrue(result.edges().get(1).cycle());
        assertEquals(List.of("B"), client.request.testGraph().get("A"));
        assertEquals(result, service.getSaved("id"));
    }
    @Test void rejectsInvalidGraphBeforeSaving() {
        FakeRepo repo = new FakeRepo();
        GraphResult bad = new GraphResult("A", List.of(new GraphNode("A","A",null)), List.of(new GraphEdge("A","B",false)), List.of("A"));
        GraphService service = new GraphService(new FakeConfiguration(), new FakeGraphClient(bad), repo, new GraphStorageService(repo));
        assertThrows(ResponseStatusException.class, () -> service.build("id", new GraphInput(Map.of("A", List.of("B")))));
        assertNull(repo.result);
    }
    @Test void requiresTestFileContents() {
        FakeRepo repo = new FakeRepo();
        GraphService service = new GraphService(new FakeConfiguration(), new FakeGraphClient(graph()), repo, new GraphStorageService(repo));
        assertThrows(ResponseStatusException.class, () -> service.build("id", null));
    }
}
