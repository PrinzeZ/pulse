# P.U.L.S.E Audit Ledger Test Guide

## 1. Configure encryption

Generate a key:

```powershell
.\scripts\generate-audit-key.ps1
```

Put the returned Base64 value in `.env.local` as `PULSE_AUDIT_ARCHIVE_KEY_BASE64`. Do not commit `.env.local`.

Optionally set `PULSE_AUDIT_ADMIN_EMAIL` for daily encrypted-archive email notifications. SMTP must also be configured.

## 2. Verify every stock change is logged

1. Log in as hospital staff.
2. Open **My Inventory**.
3. Change a medicine from one quantity to another.
4. Open **Audit history**.
5. Confirm timestamp, medicine, before quantity, change, after quantity, movement type and actor appear.
6. Repeat using a transfer dispatch/receive and confirm `TRANSFER_OUT` / `TRANSFER_IN` entries.

A zero-delta edit is intentionally not logged because the stock count did not change.

## 3. Verify staff retention boundary

- The staff page shows only the most recent 30 days.
- There is no staff Excel/download endpoint.
- Request an older period using the request form.
- The request appears in the hospital admin's **Staff historical-access requests** section.
- Until approved, the staff member cannot open the requested period.
- After approval, `/staff/audit/approved?...` renders the approved period in the web UI only.

## 4. Verify archive creation

On the admin audit page, use **Manual archive seal** for a date that contains movement records.

Confirm:

- a `.pulse-audit` file appears under `data/audit-archives/<hospitalId>/`;
- the file is not a readable CSV/XLSX/JSON file;
- the archive is also written to the cloud `audit_archives` table when PostgreSQL is available;
- the admin archive list shows the period and SHA-256 digest.

## 5. Verify Excel export

Click **Create Excel** beside an archive or use the date-range export.

The server decrypts the archive and creates an `.xlsx` workbook with:

- timestamp
- medicine
- medicine ID
- before quantity
- change
- after quantity
- movement type
- reference
- actor
- note
- event ID
- hash

The encrypted source archive remains encrypted.

## 6. Verify monthly compaction and cold storage

The normal schedule creates daily archives. On the first day of the next month it creates one hospital-month archive and removes that month's daily archive files after the monthly archive exists.

At 90 days the hot ledger rows are removed after successful archive creation. Historical data remains in the encrypted monthly archive.

At 365 days the project default retention job removes expired local and cloud archives. Change this before production to the hospital's approved records-retention policy.

## 7. Verify integrity

Change a byte in a `.pulse-audit` file and try to open/export it. The SHA-256 check or AES-GCM authentication must reject it.

For hot ledger data, use the Audit Ledger verification badge. The ledger uses a SHA-256 chain and includes quantity-before/quantity-after in new entries. Existing pre-feature entries are verified using the legacy hash format for backward compatibility.
