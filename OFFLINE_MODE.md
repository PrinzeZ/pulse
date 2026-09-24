# P.U.L.S.E local/offline mode

P.U.L.S.E is designed so a hospital node can keep core hospital operations running when the Internet or Supabase is unavailable.

## What remains available offline

- Local login for accounts already cached in the hospital H2 mirror.
- Local hospital/medicine reference data.
- Local stock reads and stock adjustments.
- Local stock movement audit ledger.
- Local medicine-request/decision audit entries.
- Local audit archive files.
- Local map data and a non-tile hospital list fallback.

## What requires connectivity

- Supabase/Railway synchronization.
- Cross-hospital/district/state cloud workflows.
- Cloud archive synchronization.
- Internet map tiles and external routing.
- External medicine package images.
- Email delivery.

The UI does not depend on the Internet for its own CSS/JavaScript. The map page normally uses Leaflet from its external distribution and remote map tiles; if Leaflet cannot be loaded, P.U.L.S.E now falls back to a local hospital-list view instead of leaving the page blank.

The local sync worker first performs a short cloud health check. When the cloud is unreachable, it skips synchronization rather than repeatedly consuming database connections. When connectivity returns, synchronization resumes automatically.

Cloud Hikari connections use bounded timeouts, a shorter max lifetime and keepalive settings so an expired Supabase pooler connection does not stall the local node for a long period.

## Important

A fresh hospital node still needs its initial local account/reference-data seed. In the development profile, P.U.L.S.E creates the demo administrator accounts and local demo reference data when the cloud database is unavailable. Production deployments should provision the local mirror from an approved hospital onboarding process rather than relying on development seed credentials.
