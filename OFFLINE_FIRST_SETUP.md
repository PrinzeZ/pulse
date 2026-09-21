# P.U.L.S.E — Offline-First Local Node

## What this build changes

P.U.L.S.E no longer treats H2 as only a stock cache. When the `local` profile is active, the hospital machine is a local P.U.L.S.E node:

- Spring Boot serves the application on the hospital LAN.
- H2 stores the local hospital snapshot.
- Login can authenticate from the local mirror without Internet.
- Hospital stock reads and writes use the local mirror when cloud access is unavailable.
- Medicine search falls back to H2 when Supabase is unavailable.
- Hospital dashboards and hospital alerts can fall back to local data.
- Staff accounts created while offline are queued locally and pushed to Supabase when connectivity returns.
- Stock changes remain pending until the sync engine successfully pushes them.
- Reference data and cloud users are refreshed periodically when Supabase is reachable.

## Start

```powershell
.\start.ps1
```

This starts:

```text
profiles = dev,local
server = 0.0.0.0:8080
cloud = Supabase when reachable
local = H2 always
```

Other devices on the same LAN use the IP printed by `start.ps1`, for example:

```text
http://192.168.x.x:8080
```

## Offline test

1. Start P.U.L.S.E while Internet is available.
2. Let the first sync complete.
3. Stop the application.
4. Disconnect Internet/Wi-Fi from the machine's Internet path while keeping the local LAN available.
5. Start the local node:

```powershell
.\start.ps1
```

6. Test hospital staff login, dashboard, inventory, local stock, medicine search, alerts and stock updates.
7. Change stock while offline. It should remain locally stored/pending.
8. Restore Internet.
9. Wait for the scheduler or use **Local Sync → Sync Now**.
10. Confirm the local change reaches Supabase and becomes synced.

## Important architecture rule

`localhost` is the local server itself. Offline mode is meaningful because the P.U.L.S.E server can continue serving hospital devices over the LAN even when the Internet/Supabase connection is down.

The intended production topology is:

```text
                    Supabase
                       ▲
                       │ Internet (when available)
                       ▼
              Hospital P.U.L.S.E Node
              Spring Boot + H2
                       │
                     LAN
              ┌────────┼────────┐
              ▼        ▼        ▼
            Staff    Staff    Admin
```

The local node must have completed at least one successful cloud/reference-data synchronization before it can provide a full real hospital snapshot after a first-time Internet outage. Development mode also contains a local demo bootstrap so a clean demo node can be started without the cloud database.


## Fresh-node offline bootstrap (Phase 7)

A fresh clone no longer depends on a previously populated `data/local-hospital-cache.mv.db`.

On startup with `dev,local`:
1. P.U.L.S.E attempts to mirror hospitals, medicines, and users from Supabase into H2.
2. If Supabase is unreachable and H2 is empty, deterministic demo hospitals, medicines, stock, and development administrator accounts are created locally.
3. Local stock synchronization is attempted immediately instead of waiting for the first scheduled sync.
4. Phase 7 medicine requests are stored in H2 first. Hospital, district, and state request actions can operate against the local request mirror while offline.
5. When connectivity returns, pending local request changes are pushed to Supabase by the existing scheduler.

The runtime H2 database is intentionally local machine state. It should not be committed to Git or shared between developers.
