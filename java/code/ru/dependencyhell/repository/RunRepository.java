package ru.dependencyhell.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.dependencyhell.model.ApiModels.Dependency;
import ru.dependencyhell.model.ApiModels.DirectDependencies;
import ru.dependencyhell.model.ApiModels.Run;

@Repository
public class RunRepository {
    private final JdbcTemplate jdbc;

    public RunRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Run run) {
        jdbc.update("INSERT INTO analysis_runs (id, package_name, repository, test_mode, ascii_tree, status, stage) VALUES (?, ?, ?, ?, ?, ?, ?)", run.id(), run.packageName(), run.repository(), run.testMode(), run.asciiTree(), run.status(), run.stage());
    }

    public Optional<Run> find(String id) {
        List<Run> rows = jdbc.query("SELECT id, package_name, repository, test_mode, ascii_tree, status, stage, package_version FROM analysis_runs WHERE id = ?", (rs, row) -> new Run(rs.getString("id"), rs.getString("package_name"), rs.getString("repository"), rs.getBoolean("test_mode"), rs.getBoolean("ascii_tree"), rs.getString("status"), rs.getInt("stage"), rs.getString("package_version")), id);
        return rows.stream().findFirst();
    }

    public void saveDependencies(String id, DirectDependencies result) {
        jdbc.update("DELETE FROM direct_dependencies WHERE run_id = ?", id);
        for (Dependency dependency : result.dependencies()) {
            jdbc.update("INSERT INTO direct_dependencies (run_id, name, version_spec) VALUES (?, ?, ?)", id, dependency.name(), dependency.versionSpec());
        }
        jdbc.update("UPDATE analysis_runs SET package_version = ?, status = ?, stage = ? WHERE id = ?", result.version(), "COMPLETED", 2, id);
    }

    public List<Dependency> findDependencies(String id) {
        return jdbc.query("SELECT name, version_spec FROM direct_dependencies WHERE run_id = ? ORDER BY name", (rs, row) -> new Dependency(rs.getString("name"), rs.getString("version_spec")), id);
    }
}
