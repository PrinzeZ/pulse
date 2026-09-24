# P.U.L.S.E Administrative Request Decision Audit

P.U.L.S.E keeps the medicine-request approval trail separate from the stock-movement ledger.

## What is logged

Every request transition is hash-chained and records:

- timestamp
- request ID
- hospital
- medicine
- requested and fulfilled quantity
- actor username and role
- tier (hospital, district, state)
- action (create, review, approve, partial, reject, escalate, fulfill)
- previous and resulting status
- note
- previous hash and event hash

## Retention

- **0–90 days:** decision rows remain in the cloud PostgreSQL ledger and are queryable normally.
- **Monthly archive:** closed months are serialized, GZIP-compressed and AES-256-GCM encrypted.
- **90+ days:** old hot decision rows are purged after the monthly encrypted archive exists. The archive remains the retained source.
- **365 days:** encrypted decision archives are purged by default. Set `PULSE_REQUEST_AUDIT_RETENTION_DAYS` to the organization's actual records-retention requirement before production use.

The scheduled job catches up the previous 12 closed months so a temporary application outage does not permanently skip an archive month.

## Storage

The encrypted archive has two copies when local storage is available:

1. local encrypted `.pulse-log` file under `pulse.audit.archive-root/request-decisions/<hospitalId>/`
2. encrypted payload in Supabase PostgreSQL table `medicine_request_audit_archives`

The browser never receives the encrypted archive itself. An authorized administrator explicitly opens a period; the server verifies SHA-256, decrypts and decompresses it, then renders a read-only table.

## Authorization

Hospital administrators can open only their hospital's archives. District administrators can open archives belonging to hospitals in their district. State administrators can open archives belonging to hospitals in their state. Staff have no decision-archive route.

## Configuration

```text
PULSE_REQUEST_AUDIT_COLD_AFTER_DAYS=90
PULSE_REQUEST_AUDIT_RETENTION_DAYS=365
PULSE_REQUEST_AUDIT_MONTHLY_CRON=0 45 3 1 * *
```

The AES key is the existing `PULSE_AUDIT_ARCHIVE_KEY_BASE64` 32-byte secret used by the audit archive subsystem. It must never be committed to Git.
