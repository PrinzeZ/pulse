# P.U.L.S.E — H2 Local Database Integration

This integration combines the current Phase 3/4 P.U.L.S.E project with the H2/local-stock work from `feature/h2-local-db`.

## What was integrated

- H2 runtime dependency in `pom.xml`
- `application-local.properties`
- `LocalDataSourceConfig`
- `LocalJpaConfig`
- `LocalStockEntry`
- `LocalStockEntryRepository`
- `StockSyncService`
- H2 change log

## Safety choices

- The current Phase 3/4 `LoginController` was preserved. It was extended only so that, when the `local` Spring profile is active, a staff login can refresh that hospital's H2 stock cache.
- The H2 configuration and sync service are restricted to the `local` profile. Normal PostgreSQL/Supabase operation therefore remains the default.
- The friend's `PostgresJpaConfig` was intentionally NOT copied because the current project already has a working PostgreSQL JPA setup and replacing it would risk changing Phase 3/4 behavior.
- H2 uses its own Hibernate dialect and schema update setting so the global PostgreSQL dialect is not applied to the local H2 EntityManagerFactory.
- Full bidirectional sync is NOT implemented here. This is the local cache/download foundation only.

## Run modes

Normal development:
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

H2-enabled development:
```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,local"
```

The local H2 database is stored under:
`./data/local-hospital-cache`

## Important

Do not commit database passwords, SMTP app passwords, or other secrets. Keep credentials in environment variables.
