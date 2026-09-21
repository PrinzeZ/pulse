# P.U.L.S.E — Phase 7 Medicine Request Workflow

Phase 7 introduces a scoped medicine-request workflow:

```text
Hospital Admin
    ↓ creates
PENDING_DISTRICT
    ↓
District Admin
    ├─ Review
    ├─ Approve
    ├─ Partial fulfill
    ├─ Fulfill
    ├─ Reject
    └─ Escalate to State
             ↓
      State Admin
        ├─ Approve
        ├─ Partial fulfill
        ├─ Fulfill
        └─ Reject
```

## Offline-first behavior

On a local hospital node, a hospital request is written to H2 first. If Supabase is unavailable:

- the request remains `pendingSync=true`;
- the hospital can continue working;
- the request is not lost;
- the local sync scheduler retries when connectivity returns.

The **Sync requests** action on the hospital request page is a manual retry. It is not required for normal offline operation.

## Cloud synchronization

When connectivity is available:

1. local pending requests are pushed to `medicine_requests`;
2. the cloud request ID is stored locally;
3. district/state status changes are pulled back to the hospital's local copy;
4. local requests remain pending if a cloud operation fails.

The cloud table is created lazily when a cloud request operation is attempted, so local startup does not depend on PostgreSQL.

## Scope and authorization

- Hospital Admin can create/view requests for their hospital.
- District Admin can act only on requests whose `districtId` matches the logged-in administrator.
- State Admin can act only on requests within the logged-in state.
- Staff accounts cannot create or approve supply requests.
- Fulfillment in Phase 7 records the workflow quantity/status; it does not yet transfer physical stock between hospitals.

## Test checklist

### Hospital
- Create a request online.
- Confirm it appears as `PENDING_DISTRICT`.
- Disconnect Internet.
- Create another request.
- Confirm it appears locally as pending cloud sync.
- Restore Internet.
- Use **Sync requests**.
- Confirm the pending request reaches the cloud.

### District
- Login as a district admin.
- Confirm only that district's requests appear.
- Review, approve, partially fulfill, fulfill, reject, and escalate.
- Confirm the status changes.

### State
- Login as state admin.
- Confirm only escalated requests appear.
- Approve, partially fulfill, fulfill, or reject.
- Confirm the hospital/local copy refreshes after synchronization.

## Phase 7 boundary

Phase 7 manages the request lifecycle. It intentionally does not automatically move stock between hospitals or warehouses. That allocation/transfer layer can be implemented in a later supply-chain phase once the request workflow has been validated.


## Fresh-node / offline behavior

Phase 7 is local-first on a `dev,local` node. A fresh installation initializes its H2 reference mirror from Supabase when available, or seeds the deterministic offline development baseline when the cloud is unavailable. Request creation, district review/actions, and state actions use the H2 request mirror while offline and remain queued for cloud synchronization.

The H2 database is runtime state and must not be shared through Git.
