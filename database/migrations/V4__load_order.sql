CREATE TABLE load_orders (
    run_id VARCHAR(36) PRIMARY KEY REFERENCES graphs(run_id) ON DELETE CASCADE,
    possible BOOLEAN NOT NULL
);

CREATE TABLE load_order_entries (
    run_id VARCHAR(36) NOT NULL REFERENCES load_orders(run_id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    node_id VARCHAR(512) NOT NULL,
    PRIMARY KEY (run_id, position),
    UNIQUE (run_id, node_id),
    FOREIGN KEY (run_id, node_id) REFERENCES graph_nodes(run_id, node_id) ON DELETE CASCADE
);
