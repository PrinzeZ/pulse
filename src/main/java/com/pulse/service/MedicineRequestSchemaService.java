package com.pulse.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class MedicineRequestSchemaService {

    private final JdbcTemplate jdbc;

    public MedicineRequestSchemaService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * Creates the Phase 7 cloud table without making application startup depend
     * on PostgreSQL. Called only when a cloud operation is actually attempted.
     */
    public boolean ensureTable() {
        try {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS medicine_requests (
                    request_id BIGSERIAL PRIMARY KEY,
                    hospital_id BIGINT NOT NULL,
                    district_id BIGINT NOT NULL,
                    state_id BIGINT,
                    medicine_id BIGINT NOT NULL,
                    requested_quantity INT NOT NULL CHECK (requested_quantity > 0),
                    fulfilled_quantity INT NOT NULL DEFAULT 0 CHECK (fulfilled_quantity >= 0),
                    status VARCHAR(40) NOT NULL,
                    requested_by_username VARCHAR(100),
                    last_updated_by_username VARCHAR(100),
                    district_note VARCHAR(500),
                    state_note VARCHAR(500),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_medicine_requests_district ON medicine_requests(district_id, status)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_medicine_requests_state ON medicine_requests(state_id, status)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_medicine_requests_hospital ON medicine_requests(hospital_id, created_at)");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
