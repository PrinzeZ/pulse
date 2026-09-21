# P.U.L.S.E — Phases 8–10: Fulfillment, Audit Ledger & Physical Stock Transfer

This package completes the next three functional layers after Phase 7.

## Phase 8 — Fulfillment / Supply Ledger

Phase 7 stops at a medicine request decision. Phase 8 introduces a physical `stock_transfers` workflow:

```text
Approved request
      ↓
District/State creates transfer
      ↓
Source hospital
   DISPATCH
      ↓
IN_TRANSIT
      ↓
Destination hospital
   RECEIVE
      ↓
Destination stock increases
```

A transfer stores:

- request ID
- source hospital
- destination hospital
- medicine
- quantity
- status
- creator/dispatcher/receiver
- timestamps
- operational note

A transfer cannot be created above the request's remaining unallocated quantity.

## Phase 9 — Tamper-evident stock audit ledger

Every stock mutation now produces a `stock_movements` event.

Recorded movement types include:

- `MANUAL_ADJUSTMENT`
- `TRANSFER_OUT`
- `TRANSFER_IN`

Each event contains:

- unique event ID
- hospital
- medicine
- quantity delta
- actor
- reference
- timestamp
- previous hash
- SHA-256 hash

The hash is calculated from the complete event payload plus the previous event hash. The result is therefore tamper-evident rather than merely a mutable "last updated" field.

The application exposes local ledger verification from:

- Hospital Admin → `/admin/audit`
- Pharmacy Staff → `/staff/audit`

The verifier checks the complete local chain and reports the first mismatch.

Cloud synchronization copies local ledger events using the immutable event ID and does not overwrite an existing cloud event.

## Phase 10 — Actual stock transfer

Dispatch and receipt now modify local H2 stock.

### Dispatch

```text
source stock = source stock - transfer quantity
```

and creates:

```text
TRANSFER_OUT
```

### Receipt

```text
destination stock = destination stock + transfer quantity
```

and creates:

```text
TRANSFER_IN
```

Both operations are local-first when the hospital is running with the `local` profile.

If Supabase is unavailable:

- H2 stock remains usable;
- the transfer remains pending synchronization;
- the movement ledger remains local;
- the scheduler retries synchronization later.

When connectivity returns:

1. local stock is synchronized;
2. transfers are synchronized;
3. movement ledger events are synchronized;
4. request fulfillment is refreshed from received transfers.

## Authorization

- District administrators can create transfers for hospitals in their district.
- State administrators can create transfers for hospitals in their state.
- Pharmacy staff can dispatch only transfers whose source is their hospital.
- Pharmacy staff can receive only transfers whose destination is their hospital.
- A transfer cannot be dispatched twice.
- A transfer cannot be received before dispatch.
- A completed/cancelled/rejected transfer cannot be dispatched.
- Source stock cannot go negative.

## Important consistency boundary

The system deliberately does not pretend that two independent hospital H2 databases are one transaction.

For a cross-hospital transfer:

```text
source H2
   ↓
dispatch
   ↓
cloud synchronization
   ↓
destination H2
   ↓
receipt
```

The cloud transfer record is the coordination layer. Each hospital's physical stock mutation is local and auditable.

## Testing checklist

### Transfer creation

1. Approve a medicine request.
2. Open District/State → Transfers.
3. Select the approved request.
4. Select a different source hospital.
5. Create a transfer.
6. Confirm it appears as `APPROVED`.

### Offline dispatch

1. Stop Internet/Supabase access.
2. Log in to the source hospital.
3. Open Staff → Transfers.
4. Dispatch the transfer.
5. Confirm source H2 stock decreases.
6. Confirm transfer becomes `IN_TRANSIT`.
7. Confirm an audit entry with `TRANSFER_OUT`.

### Offline receipt

1. On the destination hospital node, synchronize the transfer once it is available.
2. Receive the transfer.
3. Confirm destination H2 stock increases.
4. Confirm transfer becomes `RECEIVED`.
5. Confirm an audit entry with `TRANSFER_IN`.

### Audit verification

1. Open Staff → Audit ledger or Hospital Admin → Audit ledger.
2. Confirm `CHAIN VALID`.
3. Confirm the number of entries increases after manual stock changes, dispatches and receipts.

### Recovery

1. Perform dispatch/receipt while cloud is unavailable.
2. Restore connectivity.
3. Wait for the local scheduler or trigger synchronization.
4. Confirm transfer, stock and ledger events reach the cloud.

## Schema

Cloud tables are created lazily only when a cloud supply-chain operation is attempted:

- `stock_transfers`
- `stock_movements`

Local H2 tables are managed by the local JPA configuration:

- `local_stock_transfers`
- `local_stock_movements`

The application remains startable without Supabase.
