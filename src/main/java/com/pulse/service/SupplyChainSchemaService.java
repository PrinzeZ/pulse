package com.pulse.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SupplyChainSchemaService implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final GovernmentHospitalCatalogService hospitalCatalog;

    public SupplyChainSchemaService(DataSource dataSource, GovernmentHospitalCatalogService hospitalCatalog) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.hospitalCatalog = hospitalCatalog;
    }

    /**
     * Run the small identity migration before the dev seeders and scheduled
     * sync jobs can query Hospital entities. Hibernate is deliberately not
     * relied upon for this migration because the application uses a Supabase
     * pooler and two separate persistence units.
     */
    @Override
    public void run(ApplicationArguments args) {
        ensureHospitalIdentitySchema();
    }

    private void ensureHospitalIdentitySchema() {
        try {
            jdbc.execute("""
                ALTER TABLE hospitals
                ADD COLUMN IF NOT EXISTS government_hospital_key VARCHAR(64)
                """);

            jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_hospitals_government_hospital_key
                ON hospitals(government_hospital_key)
                WHERE government_hospital_key IS NOT NULL
                """);

            jdbc.execute("""
                ALTER TABLE hospital_registrations
                ADD COLUMN IF NOT EXISTS government_hospital_key VARCHAR(64)
                """);

            int backfilled = 0;
            for (GovernmentHospitalCatalogService.GovernmentHospital g : hospitalCatalog.all()) {
                backfilled += jdbc.update("""
                    UPDATE hospitals
                    SET government_hospital_key=?
                    WHERE government_hospital_key IS NULL
                      AND LOWER(name)=LOWER(?)
                      AND LOWER(district)=LOWER(?)
                    """, g.key(), g.name(), g.district());
            }

            System.out.println("P.U.L.S.E hospital identity schema ready"
                    + " (government_hospital_key present; backfilled=" + backfilled + ")");
        } catch (RuntimeException ex) {
            System.err.println("P.U.L.S.E hospital identity schema migration failed: "
                    + ex.getMessage());
            // Keep local/offline startup alive, but do not hide the reason.
        }
    }

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
