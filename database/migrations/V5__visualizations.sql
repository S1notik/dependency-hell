CREATE TABLE visualizations (
    run_id VARCHAR(36) PRIMARY KEY REFERENCES graphs(run_id) ON DELETE CASCADE,
    mermaid TEXT NOT NULL,
    ascii_tree TEXT
);
