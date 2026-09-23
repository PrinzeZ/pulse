# P.U.L.S.E Phase 16 — Zero Trust Resource & Scope Authorization

This phase adds a centralized authorization layer on top of Spring Security authentication.

## Model

- Authentication answers **who is the user?**
- Role authorization answers **what class of operation may they perform?**
- Scope authorization answers **which state, district, hospital, request, transfer, or staff account may they touch?**
- Network location is not treated as proof of trust.

## Scope rules

- `STATE_ADMIN`: resources inside the authenticated state.
- `DISTRICT_ADMIN`: resources inside the authenticated district.
- `ADMIN`: resources belonging to the authenticated hospital.
- `STAFF`: operational resources belonging to the authenticated hospital.

## Protected resource operations

The centralized `PulseScopeAuthorizationService` is used by Spring Security method authorization for:

- hospital scope checks
- district/state scope checks
- medicine request actions
- stock transfer dispatch/receive
- district/state transfer creation
- hospital-admin staff authorization changes

Existing controller checks remain as defense-in-depth and for legacy view/session compatibility.

## Important

This phase does not claim the application is fully secure. It establishes the resource-level authorization foundation. Later phases should cover API-specific authorization, rate limiting, audit integrity, secret management, validation, dependency/security scanning, and an OWASP ASVS review.
