package com.pulse.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class SupplyChainSchemaService {

    private final JdbcTemplate jdbc;

    public SupplyChainSchemaService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Cloud-only lazy schema creation. Local startup never depends on this method.
     */
    public boolean ensureTables() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS stock_transfers (
                    transfer_id BIGSERIAL PRIMARY KEY,
                    request_id BIGINT NOT NULL,
                    source_hospital_id BIGINT NOT NULL,
                    destination_hospital_id BIGINT NOT NULL,
                    medicine_id BIGINT NOT NULL,
                    quantity INT NOT NULL CHECK (quantity > 0),
                    status VARCHAR(30) NOT NULL,
                    created_by_username VARCHAR(100),
                    dispatched_by_username VARCHAR(100),
                    received_by_username VARCHAR(100),
                    note VARCHAR(500),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS stock_movements (
                    movement_id BIGSERIAL PRIMARY KEY,
                    event_id VARCHAR(64) NOT NULL UNIQUE,
                    hospital_id BIGINT NOT NULL,
                    medicine_id BIGINT NOT NULL,
                    delta_quantity INT NOT NULL,
                    movement_type VARCHAR(40) NOT NULL,
                    reference_type VARCHAR(40),
                    reference_id BIGINT,
                    actor_username VARCHAR(100),
                    note VARCHAR(500),
                    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    previous_hash VARCHAR(64),
                    hash VARCHAR(64) NOT NULL
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_stock_transfers_request ON stock_transfers(request_id, created_at)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_stock_transfers_source ON stock_transfers(source_hospital_id, status)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_stock_transfers_destination ON stock_transfers(destination_hospital_id, status)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_stock_movements_hospital ON stock_movements(hospital_id, occurred_at, movement_id)");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
