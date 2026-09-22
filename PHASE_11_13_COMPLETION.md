# P.U.L.S.E — Phases 11–13

## Phase 11 — District → State escalation

Completed in the existing request workflow. District administrators can escalate eligible medicine requests to the state tier; state administrators receive only requests explicitly escalated to their state and can review, approve, partially fulfill or reject them.

The state request workflow is implemented through `/state-admin/requests` and the request service status transitions `ESCALATED_TO_STATE`, `STATE_APPROVED`, `STATE_PARTIALLY_FULFILLED` and `FULFILLED`.

## Phase 12 — Hierarchical operational analytics

Added scoped analytics pages:
- `/admin/analytics` — hospital scope
- `/district-admin/analytics` — district scope
- `/state-admin/analytics` — state scope

Metrics include stock units, medicine types, low/out-of-stock lines, pending requests, active/completed transfers, active alerts and a hospital risk view.

## Phase 13 — Public read-only API foundation

Added:
- `GET /api/public/stock?query=&district=`
- `GET /api/public/hospitals`
- `GET /api/public/status`

These endpoints expose read-only availability/directory data without exposing administrative mutation operations.


## Online deployment acceptance

The project now includes a Railway deployment target using the root `Dockerfile` and `railway.toml`. Railway runs the Spring Boot service remotely and exposes it through a public HTTPS domain. Supabase remains the central PostgreSQL database; H2 remains local/offline-only.

Phase 11–13 should be marked fully accepted after the Railway deployment is live and the remote acceptance checklist in `RAILWAY_DEPLOYMENT.md` passes from a phone on mobile data with the development PC disconnected or powered off.
