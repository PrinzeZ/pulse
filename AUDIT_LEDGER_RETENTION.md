# P.U.L.S.E Audit Ledger, Archive and Retention

## Policy implemented

- Every non-zero stock quantity change records timestamp, actor, medicine, before/after quantity, delta, reference and a SHA-256 hash-chain link.
- The same ledger is maintained on the hospital-local node and synchronized to the cloud with the original event ID, so retries are idempotent.
- Staff can directly view the latest 30 days only. Staff have no audit download endpoint.
- Staff can request an older period. A hospital ADMIN must explicitly approve the request. Approved history is rendered in the web application only.
- A browser cannot be made technically incapable of screenshots; the staff page therefore uses view-only controls, print blocking, text-selection restrictions and a visible watermark.
- Daily audit records are sealed into encrypted `.pulse-audit` containers.
- Monthly archives combine all medicines for a hospital into one hospital-month archive. This is intentionally not one file per medicine: one hospital-month container is smaller, easier to verify, and produces one ordered Excel workbook with medicine columns/filtering.
- After 90 days, hot ledger rows are removed and the monthly encrypted archive is the retained source for historical access.
- After 365 days, local and cloud archives are purged by the scheduled retention job. This is a project default and must be changed to the hospital's legal/records-retention policy before production.
- Admins can export any requested period to a fresh `.xlsx` workbook. The source archive itself remains encrypted and is never turned into a permanently readable file.
- A daily encrypted archive can be emailed to the configured audit administrator as an attachment. SMTP failure does not affect stock operations.

## Encryption

Archive payloads are compressed with GZIP and encrypted with AES-256-GCM using `PULSE_AUDIT_ARCHIVE_KEY_BASE64`. The key is not stored in Git, the database, or the archive itself.

## Storage

- Local archive: `data/audit-archives/<hospitalId>/...pulse-audit`
- Cloud archive: PostgreSQL `audit_archives.encrypted_payload`

## Operational rule

Do not use a single hard-coded project key. Generate a 32-byte key and provide the same protected secret to the hospital instance and cloud service through environment/secrets management.
