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
            jdbc.execute("ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS audit_attachment_file_name VARCHAR(255)");
            jdbc.execute("ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS audit_attachment_sha256 VARCHAR(64)");
            jdbc.execute("ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS audit_attachment_period_start DATE");
            jdbc.execute("ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS audit_attachment_period_end DATE");
            jdbc.execute("ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS audit_attachment_created_at TIMESTAMP");
            jdbc.execute("ALTER TABLE medicine_requests ADD COLUMN IF NOT EXISTS audit_attachment_encrypted BYTEA");
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS medicine_request_action_logs (
                    action_log_id BIGSERIAL PRIMARY KEY,
                    event_id VARCHAR(36) NOT NULL UNIQUE,
                    request_id BIGINT,
                    hospital_id BIGINT NOT NULL,
                    district_id BIGINT NOT NULL,
                    state_id BIGINT,
                    medicine_id BIGINT NOT NULL,
                    actor_role VARCHAR(40) NOT NULL,
                    actor_username VARCHAR(100) NOT NULL,
                    tier VARCHAR(32) NOT NULL,
                    action VARCHAR(40) NOT NULL,
                    from_status VARCHAR(40),
                    to_status VARCHAR(40),
                    requested_quantity INT NOT NULL,
                    fulfilled_quantity INT NOT NULL DEFAULT 0,
                    note VARCHAR(500),
                    occurred_at TIMESTAMP NOT NULL,
                    previous_hash VARCHAR(64),
                    hash VARCHAR(64) NOT NULL
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_request_action_logs_district ON medicine_request_action_logs(district_id, occurred_at)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_request_action_logs_state ON medicine_request_action_logs(state_id, occurred_at)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_request_action_logs_hospital ON medicine_request_action_logs(hospital_id, occurred_at)");
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS medicine_request_audit_archives (
                    archive_id BIGSERIAL PRIMARY KEY,
                    hospital_id BIGINT NOT NULL,
                    granularity VARCHAR(16) NOT NULL,
                    period_start DATE NOT NULL,
                    period_end DATE NOT NULL,
                    storage_name VARCHAR(255) NOT NULL,
                    sha256 VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    encrypted_payload BYTEA NOT NULL,
                    CONSTRAINT uk_request_audit_archive_hospital_period UNIQUE(hospital_id, granularity, period_start)
                )
                """);
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_request_audit_archives_hospital ON medicine_request_audit_archives(hospital_id, period_start)");
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public void ensureTableOrThrow() {
        if (!ensureTable()) throw new IllegalStateException("Cloud request storage is unavailable.");
    }
}
