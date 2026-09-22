# P.U.L.S.E — Cloud connection fix

## What was fixed

The custom PostgreSQL `DataSource` was being created manually from `DataSourceProperties`, but the `spring.datasource.hikari.*` settings were not being bound to that custom Hikari datasource. This meant the configured Supavisor isolation/timeout settings were silently ignored. The datasource now binds `spring.datasource.hikari.*` directly to `HikariDataSource`.

The Hibernate dialect warnings were also removed by letting Hibernate detect the database dialect, and Open Session in View is explicitly disabled.

## Local setup

1. Copy `.env.local.example` to `.env.local`.
2. Put the real Supabase database password in `.env.local`.
3. Do not commit `.env.local`.
4. Start with:

```powershell
.\start.ps1
```

The script prints whether the Supabase password variable was loaded without printing the secret itself.

## Important

The uploaded development archive may contain a local `.env.local`; the clean distribution archive intentionally does not. Keep secrets out of GitHub and Railway source files.

## Railway

Railway must deploy the branch containing this fix. If the Railway service is tracking `main` while development is on `h2-phase-integration`, either change Railway's source branch or merge the tested branch into `main`.

Railway Variables must contain the current Supabase Connect values:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Do not copy the password into this repository.
