# P.U.L.S.E Phase 17 — Complete Fix

## Included
- Explicit registration of `HierarchyDashboardService` to prevent the startup bean-resolution failure.
- Deterministic PostgreSQL hospital identity migration:
  - adds `government_hospital_key` to `hospitals`
  - adds it to `hospital_registrations`
  - creates the unique partial index
  - backfills exact official hospital-name/district matches
  - runs before the development seeders
  - reports the real database migration error instead of silently swallowing it
- CARTO basemap key support through `PULSE_CARTO_MAP_KEY`.
- OpenStreetMap fallback when the CARTO key is absent.
- Existing connected Supabase hospitals are represented on the government-hospital map using exact/legacy matching.
- Full government hospital map, medicine search, nearest-stock routing, and Google Maps outbound navigation remain enabled.
- Medicine directory "View all medicines" overlay is edge-to-edge, translucent, and changes opacity on hover.
- Full vertical medicine directory remains available at `/search?view=all`.
- Added `SUPABASE_HOSPITAL_IDENTITY_FIX.sql` as a manual fallback if the Supabase database user does not have permission to alter the schema.

## CARTO key
Put the key in the local `.env.local` file:

`PULSE_CARTO_MAP_KEY=YOUR_CARTO_KEY`

Do not commit `.env.local`.

## Fresh build
This package intentionally excludes:
- `.git`
- `target`
- `.env.local`
- the local H2 database

That forces Maven to compile the current source instead of reusing stale compiled classes.
