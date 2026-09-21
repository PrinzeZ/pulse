CREATE TABLE IF NOT EXISTS hospitals (
    hospital_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    district VARCHAR(50) NOT NULL
);

CREATE TABLE IF NOT EXISTS medicines (
    medicine_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    threshold INT NOT NULL DEFAULT 10
);

CREATE TABLE IF NOT EXISTS users (
    user_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    hospital_id BIGINT,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id)
);

CREATE TABLE IF NOT EXISTS stock_entries (
    entry_id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    quantity INT NOT NULL CHECK (quantity >= 0),
    last_updated DATE NOT NULL,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id),
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);

CREATE TABLE IF NOT EXISTS alerts (
    alert_id BIGSERIAL PRIMARY KEY,
    hospital_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    message VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id),
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);

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
