# P.U.L.S.E Phase 5 + Phase 6 — Cloud + Bidirectional Local Sync

## Phase 5 — Cloud backend

P.U.L.S.E continues to use PostgreSQL/Supabase as the shared source of truth for the normal application data. The current Spring Boot server connects to Supabase through the existing PostgreSQL datasource.

Railway deployment is intentionally not required for this local integration test. Railway can host the Spring Boot server later without changing the sync model.

## Phase 6 — Bidirectional stock synchronization

When the `local` profile is active, P.U.L.S.E has two persistence units:

- `postgres`: `com.pulse.repository` and `com.pulse.model`
- `local`: `com.pulse.local.repository` and `com.pulse.local.model`

The local H2 database stores hospital stock in `local_stock_entries`.

### Online → Local

`StockSyncService.pullCloudStock(hospitalId)` downloads PostgreSQL stock into H2. It does not overwrite an H2 record whose `synced` flag is false.

### Local → Online

When staff changes stock while the `local` profile is active, the change is written to H2 and marked `synced=false`. `StockSyncService.pushLocalChanges(hospitalId)` sends those changes to PostgreSQL, updates the cloud entry, stores the cloud entry id locally, and marks the local record as synced.

### Full sync

`syncHospital(hospitalId)` performs:

1. Push unsynced local changes to PostgreSQL.
2. Pull the latest PostgreSQL stock into H2.
3. Report pushed/pulled/pending counts.

The service catches connection failures and reports an `OFFLINE: ...` status instead of destroying the local stock data.

### Automatic retry

With the `local` profile active, `LocalSyncScheduler` retries known hospital caches every 60 seconds. It also performs a first retry after 15 seconds. This is stock synchronization only; it is not a full database clone.

### Login sync

Staff login triggers a hospital stock sync when the `local` profile is active. Normal PostgreSQL-only mode is unchanged.

### Manual sync

A logged-in staff user can POST to `/staff/sync` through a UI action when such a button is exposed. The endpoint reports pushed, pulled, and pending records.

## Current scope / limitations

- This phase synchronizes **hospital stock**, not every PostgreSQL table.
- H2 is not a replacement for Supabase.
- No conflict-resolution policy beyond preserving unsynced local changes is implemented.
- Cloud deployment to Railway is a later deployment step.
- Full offline authentication and offline replication of users/medicines/hospitals are not implemented here.
