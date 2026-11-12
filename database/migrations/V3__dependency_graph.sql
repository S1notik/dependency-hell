CREATE TABLE graphs (
    run_id VARCHAR(36) PRIMARY KEY REFERENCES analysis_runs(id) ON DELETE CASCADE,
    root_id VARCHAR(512) NOT NULL
);

CREATE TABLE graph_nodes (
    run_id VARCHAR(36) NOT NULL REFERENCES graphs(run_id) ON DELETE CASCADE,
    node_id VARCHAR(512) NOT NULL,
    package_name VARCHAR(214) NOT NULL,
    package_version VARCHAR(255),
    visit_index INTEGER NOT NULL,
    PRIMARY KEY (run_id, node_id),
    UNIQUE (run_id, visit_index)
);

CREATE TABLE graph_edges (
    run_id VARCHAR(36) NOT NULL REFERENCES graphs(run_id) ON DELETE CASCADE,
    from_id VARCHAR(512) NOT NULL,
    to_id VARCHAR(512) NOT NULL,
    is_cycle BOOLEAN NOT NULL,
    edge_index INTEGER NOT NULL,
    PRIMARY KEY (run_id, from_id, to_id),
    FOREIGN KEY (run_id, from_id) REFERENCES graph_nodes(run_id, node_id),
    FOREIGN KEY (run_id, to_id) REFERENCES graph_nodes(run_id, node_id)
);
