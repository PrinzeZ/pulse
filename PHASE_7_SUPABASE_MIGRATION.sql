-- P.U.L.S.E Phase 7 — Medicine Request Workflow
-- Safe to run once against the Supabase PostgreSQL database.

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
);

CREATE INDEX IF NOT EXISTS idx_medicine_requests_district
    ON medicine_requests(district_id, status);

CREATE INDEX IF NOT EXISTS idx_medicine_requests_state
    ON medicine_requests(state_id, status);

CREATE INDEX IF NOT EXISTS idx_medicine_requests_hospital
    ON medicine_requests(hospital_id, created_at);
