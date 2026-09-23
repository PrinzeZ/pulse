# P.U.L.S.E Security & Scope Test Guide

## Local base URL

Use:

`http://localhost:8080`

Login:

`http://localhost:8080/login`

## Dev accounts currently seeded

These are development-only credentials from `Phase3AdminInitializer`:

| Account | Role | State | District | Hospital |
|---|---|---|---|---|
| `state_admin` | STATE_ADMIN | 1 | — | — |
| `ernakulam_admin` | DISTRICT_ADMIN | 1 | 1 | — |
| `kozhikode_admin` | DISTRICT_ADMIN | 1 | 2 | — |
| `ekm_hospital_admin` | ADMIN | 1 | 1 | 3 |
| `kozhikode_hospital_admin` | ADMIN | 1 | 2 | 1 |

Do not use these credentials outside the local development environment.

## What a normal user should see

### Hospital admin

`http://localhost:8080/admin/dashboard`

`http://localhost:8080/admin/staff`

`http://localhost:8080/admin/requests`

`http://localhost:8080/admin/audit`

The hospital admin only receives staff, requests, and dashboard data for their own hospital.

### Staff

`http://localhost:8080/staff/dashboard`

`http://localhost:8080/staff/stock`

`http://localhost:8080/staff/local-stock`

`http://localhost:8080/staff/transfers`

`http://localhost:8080/staff/audit`

The staff UI is hospital-scoped. The dashboard no longer contains the medicine/map/routing search. Medicine filtering is on **My Inventory** (`/staff/stock`) so staff can quickly find a medicine and change its quantity.

## Test 1 — Hospital A staff cannot operate on Hospital B transfer

This is a direct authorization test, not a test of whether the normal UI hides the record.

1. Log in as a staff account belonging to Hospital A.
2. Open:
   `http://localhost:8080/staff/transfers`
3. You should only see transfers returned for Hospital A.
4. In a separate private/incognito browser session, log in as a staff account belonging to Hospital B.
5. Open the same transfer page and find a transfer belonging to Hospital B.
6. Inspect the Dispatch/Receive form in browser DevTools and copy its action URL. It will look like:
   `/staff/transfers/17/dispatch`
   or
   `/staff/transfers/17/receive`
7. Return to the Hospital A staff session and submit that exact foreign-hospital action.
8. Expected result: **HTTP 403 Forbidden**.

The important point is that the attacker does not need the foreign transfer to be listed in their UI. A resource ID can be obtained by guessing, from a leaked link, from another session, from logs, or from an intercepted request. Authorization must therefore be enforced again on the server.

## Test 2 — Hospital A admin cannot manage Hospital B staff

1. Log in as `kozhikode_hospital_admin`.
2. Open:
   `http://localhost:8080/admin/staff`
3. Confirm the page only shows Kozhikode hospital staff.
4. In a separate private/incognito browser session, open the same page as `ekm_hospital_admin`.
5. In the EKM page, inspect a staff member's **Disable login / Authorize login** form in DevTools. Its action will look like:
   `/admin/staff/123/authorization`
6. Take the staff ID from a Kozhikode staff form instead.
7. While authenticated as the EKM hospital admin, submit the Kozhikode staff authorization endpoint.
8. Expected result: **HTTP 403 Forbidden**.

The server-side check is `pulseScope.canManageStaff(id)`, which loads the target user and requires the target's hospital ID to equal the authenticated admin's hospital ID.

## Test 3 — Hospital A admin cannot operate on Hospital B request

Hospital admin request pages are already hospital-scoped:

`http://localhost:8080/admin/requests`

The controller obtains the hospital ID from the authenticated admin and calls the hospital-scoped request service.

For cross-scope testing of district/state request actions, use the procedure below.

## Test 4 — Ernakulam district admin cannot act on a Kozhikode request

1. Log in as `kozhikode_admin`.
2. Open:
   `http://localhost:8080/district-admin/requests`
3. Find a Kozhikode request and inspect its action form. The endpoint will look like:
   `/district-admin/requests/17/action`
4. Log in as `ernakulam_admin` in another session.
5. Submit the Kozhikode request's action endpoint.
6. Expected result: **HTTP 403 Forbidden**.

The authorization check is:

`@pulseScope.canAccessRequest(id)`

For `DISTRICT_ADMIN`, the request's `districtId` must equal the authenticated admin's `districtId`.

## Test 5 — District transfer source-hospital isolation

1. Log in as `kozhikode_admin`.
2. Open:
   `http://localhost:8080/district-admin/transfers`
3. The hospital selector should contain only hospitals from that district.
4. Obtain a source hospital ID from the other district's session.
5. While authenticated as `ernakulam_admin`, submit:
   `/district-admin/transfers/create`
   with the foreign `sourceHospitalId`.
6. Expected result: **HTTP 403 Forbidden** before the controller can create the transfer.

The method is protected by both:

`@pulseScope.canAccessRequest(requestId)`

and

`@pulseScope.canAccessHospital(sourceHospitalId)`.

## Test 6 — State administrator scope

Open:

`http://localhost:8080/state-admin/dashboard`

`http://localhost:8080/state-admin/requests`

`http://localhost:8080/state-admin/transfers`

The current development seed has only one state (`stateId=1`), so a true **state A → state B** denial test cannot be performed from the seeded data.

To test it properly, create a second state fixture and resources belonging to that state. Then an authenticated state administrator from state A should receive **403** for a resource belonging to state B.

## What the 403 tests prove

A hidden link or filtered list is not the security boundary.

The real security boundary is the server-side authorization layer:

- `STATE_ADMIN` → state scope
- `DISTRICT_ADMIN` → district scope
- `ADMIN` → hospital scope
- `STAFF` → hospital scope

For transfers, dispatch checks the **source hospital** and receive checks the **destination hospital**.

## Security implementation currently in P.U.L.S.E

1. Spring Security request authorization
2. Form authentication
3. Custom `PulseAuthenticationProvider`
4. `UserPrincipal` carrying role + state/district/hospital scope
5. BCrypt password hashing
6. CSRF protection with `CookieCsrfTokenRepository`
7. Session fixation protection using `changeSessionId()`
8. Maximum two concurrent sessions
9. Secure logout/session invalidation
10. Security headers:
   - frame denial
   - content-type options
   - referrer policy
   - HSTS
11. Central `PulseScopeAuthorizationService`
12. Method-level `@PreAuthorize` checks
13. Hospital/district/state resource isolation
14. Transfer source/destination authorization
15. Hospital-admin staff-management authorization
16. Authentication audit events
17. Login failure handling
18. Role-based route protection
19. Local/cloud architecture does not treat network location as authorization
20. Legacy session attributes are retained for view compatibility, while resource authorization uses the authenticated Spring Security principal

Architecture:

**Spring Security** = implementation framework  
**OWASP ASVS Level 2** = security verification target  
**NIST Zero Trust** = architecture principle  
**P.U.L.S.E RBAC + state/district/hospital scope** = application authorization model
