package ru.dependencyhell.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dependencyhell.model.ApiModels.Visualization;

@Repository
public class VisualizationRepository {
    private final JdbcTemplate jdbc;
    public VisualizationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String runId, Visualization result) {
        jdbc.update("DELETE FROM visualizations WHERE run_id = ?", runId);
        jdbc.update("INSERT INTO visualizations (run_id, mermaid, ascii_tree) VALUES (?, ?, ?)", runId, result.mermaid(), result.asciiTree());
        jdbc.update("UPDATE analysis_runs SET status = ?, stage = ? WHERE id = ?", "COMPLETED", 5, runId);
    }

    public Optional<Visualization> find(String runId) {
        List<Visualization> rows = jdbc.query("SELECT mermaid, ascii_tree FROM visualizations WHERE run_id = ?",
            (rs, row) -> new Visualization(rs.getString("mermaid"), rs.getString("ascii_tree")), runId);
        return rows.stream().findFirst();
    }
}
