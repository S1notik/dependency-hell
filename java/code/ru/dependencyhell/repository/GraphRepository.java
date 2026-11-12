package ru.dependencyhell.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dependencyhell.model.ApiModels.GraphEdge;
import ru.dependencyhell.model.ApiModels.GraphNode;
import ru.dependencyhell.model.ApiModels.GraphResult;

@Repository
public class GraphRepository {
    private final JdbcTemplate jdbc;
    public GraphRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String runId, GraphResult graph) {
        jdbc.update("DELETE FROM graphs WHERE run_id = ?", runId);
        jdbc.update("INSERT INTO graphs (run_id, root_id) VALUES (?, ?)", runId, graph.root());
        List<Object[]> nodes = new ArrayList<>();
        for (int i = 0; i < graph.nodes().size(); i++) {
            GraphNode node = graph.nodes().get(i);
            nodes.add(new Object[]{runId, node.id(), node.name(), node.version(), i});
        }
        jdbc.batchUpdate("INSERT INTO graph_nodes (run_id, node_id, package_name, package_version, visit_index) VALUES (?, ?, ?, ?, ?)", nodes);
        List<Object[]> edges = new ArrayList<>();
        for (int i = 0; i < graph.edges().size(); i++) {
            GraphEdge edge = graph.edges().get(i);
            edges.add(new Object[]{runId, edge.from(), edge.to(), edge.cycle(), i});
        }
        if (!edges.isEmpty()) jdbc.batchUpdate("INSERT INTO graph_edges (run_id, from_id, to_id, is_cycle, edge_index) VALUES (?, ?, ?, ?, ?)", edges);
        String version = graph.nodes().stream().filter(node -> node.id().equals(graph.root())).findFirst().orElseThrow().version();
        jdbc.update("UPDATE analysis_runs SET package_version = ?, status = ?, stage = ? WHERE id = ?", version, "COMPLETED", 3, runId);
    }

    public Optional<GraphResult> find(String runId) {
        List<String> roots = jdbc.query("SELECT root_id FROM graphs WHERE run_id = ?", (rs, row) -> rs.getString("root_id"), runId);
        if (roots.isEmpty()) return Optional.empty();
        List<GraphNode> nodes = jdbc.query("SELECT node_id, package_name, package_version FROM graph_nodes WHERE run_id = ? ORDER BY visit_index", (rs, row) -> new GraphNode(rs.getString("node_id"), rs.getString("package_name"), rs.getString("package_version")), runId);
        List<GraphEdge> edges = jdbc.query("SELECT from_id, to_id, is_cycle FROM graph_edges WHERE run_id = ? ORDER BY edge_index", (rs, row) -> new GraphEdge(rs.getString("from_id"), rs.getString("to_id"), rs.getBoolean("is_cycle")), runId);
        return Optional.of(new GraphResult(roots.get(0), nodes, edges, nodes.stream().map(GraphNode::id).toList()));
    }
}
