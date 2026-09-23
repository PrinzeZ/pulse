# P.U.L.S.E Phase 16 — Zero Trust Resource & Scope Authorization

This phase adds a centralized authorization layer on top of Spring Security.

## Security model

- Spring Security authenticates the user and supplies a `UserPrincipal`.
- `PulseAuthorizationService` evaluates the principal's role and state/district/hospital scope before sensitive operations.
- Hospital-scoped roles (`ADMIN`, `STAFF`) are restricted to their authenticated hospital.
- `DISTRICT_ADMIN` is restricted to its authenticated district.
- `STATE_ADMIN` is restricted to its authenticated state.
- Existing controller/service scope checks remain in place as defense in depth.
- Sensitive staff-management and hospital-registration approval/rejection service operations now have method-level authorization.

## Credential minimization

`UserPrincipal` no longer stores the password hash in the Spring Security session principal. Password verification remains inside the authentication provider/login service. This reduces credential material retained in the serialized security identity.

## Important deployment note

The local-to-Railway LAN registration endpoint is intentionally separate from user authorization. It uses `PULSE_LAN_REGISTRATION_TOKEN` as a service-to-service secret and must be configured independently on the local instance and Railway.

## Verification

Run on the development machine:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

Then verify that:

1. An ADMIN can only operate on its own hospital.
2. STAFF can only operate on its own hospital.
3. A DISTRICT_ADMIN cannot access another district's resources.
4. A STATE_ADMIN cannot access another state's resources.
5. Attempts outside scope result in authorization failure rather than authentication failure.
