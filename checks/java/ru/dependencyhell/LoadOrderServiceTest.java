package ru.dependencyhell;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ru.dependencyhell.client.GraphClient;
import ru.dependencyhell.model.ApiModels.*;
import ru.dependencyhell.repository.LoadOrderRepository;
import ru.dependencyhell.service.GraphService;
import ru.dependencyhell.service.LoadOrderService;
import ru.dependencyhell.service.LoadOrderStorageService;

class LoadOrderServiceTest {
    static class FakeGraph extends GraphService {
        GraphResult value;
        FakeGraph(GraphResult value) { super(null, null, null, null); this.value = value; }
        @Override public GraphResult getSaved(String id) { return value; }
    }
    static class FakeClient extends GraphClient {
        LoadOrder value;
        FakeClient(LoadOrder value) { super("http://127.0.0.1:8082"); this.value = value; }
        @Override public LoadOrder order(GraphResult graph) { return value; }
    }
    static class FakeRepo extends LoadOrderRepository {
        LoadOrder value;
        FakeRepo() { super(new JdbcTemplate()); }
        @Override public void save(String id, LoadOrder order) { value = order; }
        @Override public Optional<LoadOrder> find(String id) { return Optional.ofNullable(value); }
    }
    private static GraphResult graph() {
        return new GraphResult("A", List.of(new GraphNode("A", "A", null), new GraphNode("B", "B", null), new GraphNode("C", "C", null)),
            List.of(new GraphEdge("A", "B", false), new GraphEdge("B", "C", false), new GraphEdge("A", "C", false)), List.of("A", "B", "C"));
    }
    private static LoadOrderService service(GraphResult graph, LoadOrder order, FakeRepo repo) {
        return new LoadOrderService(new FakeGraph(graph), new FakeClient(order), repo, new LoadOrderStorageService(repo));
    }
    @Test void savesAndRetrievesDependenciesFirst() {
        FakeRepo repo = new FakeRepo();
        LoadOrder expected = new LoadOrder(true, List.of("C", "B", "A"), List.of());
        LoadOrderService service = service(graph(), expected, repo);
        assertEquals(expected, service.build("run"));
        assertEquals(expected, service.getSaved("run"));
    }
    @Test void kotlinValidationRejectsMissingAndWrongOrder() {
        for (List<String> packages : List.of(List.of("A", "B", "C"), List.of("B", "A"), List.of("C", "B", "B"))) {
            FakeRepo repo = new FakeRepo();
            assertThrows(ResponseStatusException.class, () -> service(graph(), new LoadOrder(true, packages, List.of()), repo).build("run"));
            assertNull(repo.value);
        }
    }
    @Test void savesCycleWithoutPretendingThereIsAnOrder() {
        GraphEdge edge = new GraphEdge("A", "A", true);
        GraphResult cyclic = new GraphResult("A", List.of(new GraphNode("A", "A", null)), List.of(edge), List.of("A"));
        FakeRepo repo = new FakeRepo();
        LoadOrder expected = new LoadOrder(false, List.of(), List.of(edge));
        assertEquals(expected, service(cyclic, expected, repo).build("run"));
        assertThrows(ResponseStatusException.class, () -> service(cyclic, new LoadOrder(true, List.of("A"), List.of()), new FakeRepo()).build("run"));
    }
    @Test void unsavedOrderIsReported() {
        assertThrows(ResponseStatusException.class, () -> service(graph(), null, new FakeRepo()).getSaved("run"));
    }
}
