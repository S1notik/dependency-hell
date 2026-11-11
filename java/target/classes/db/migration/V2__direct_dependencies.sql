ALTER TABLE analysis_runs ADD COLUMN package_version VARCHAR(255);

CREATE TABLE direct_dependencies (
    run_id VARCHAR(36) NOT NULL REFERENCES analysis_runs(id) ON DELETE CASCADE,
    name VARCHAR(214) NOT NULL,
    version_spec TEXT NOT NULL,
    PRIMARY KEY (run_id, name)
);
