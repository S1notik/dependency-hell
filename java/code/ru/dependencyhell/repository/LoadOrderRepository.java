package ru.dependencyhell.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dependencyhell.model.ApiModels.GraphEdge;
import ru.dependencyhell.model.ApiModels.LoadOrder;

@Repository
public class LoadOrderRepository {
    private final JdbcTemplate jdbc;
    public LoadOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String runId, LoadOrder order) {
        jdbc.update("DELETE FROM load_orders WHERE run_id = ?", runId);
        jdbc.update("INSERT INTO load_orders (run_id, possible) VALUES (?, ?)", runId, order.possible());
        List<Object[]> entries = new ArrayList<>();
        for (int i = 0; i < order.packages().size(); i++) entries.add(new Object[]{runId, i, order.packages().get(i)});
        if (!entries.isEmpty()) jdbc.batchUpdate("INSERT INTO load_order_entries (run_id, position, node_id) VALUES (?, ?, ?)", entries);
        jdbc.update("UPDATE analysis_runs SET status = ?, stage = ? WHERE id = ?", "COMPLETED", 4, runId);
    }

    public Optional<LoadOrder> find(String runId) {
        List<Boolean> possible = jdbc.query("SELECT possible FROM load_orders WHERE run_id = ?", (rs, row) -> rs.getBoolean("possible"), runId);
        if (possible.isEmpty()) return Optional.empty();
        List<String> packages = jdbc.query("SELECT node_id FROM load_order_entries WHERE run_id = ? ORDER BY position", (rs, row) -> rs.getString("node_id"), runId);
        List<GraphEdge> cycles = jdbc.query("SELECT from_id, to_id, is_cycle FROM graph_edges WHERE run_id = ? AND is_cycle = true ORDER BY edge_index", (rs, row) -> new GraphEdge(rs.getString("from_id"), rs.getString("to_id"), true), runId);
        return Optional.of(new LoadOrder(possible.get(0), packages, cycles));
    }
}
