# P.U.L.S.E Security Implementation

This security phase upgrades P.U.L.S.E from custom session checks to **Spring Security**, while keeping the application's existing cloud PostgreSQL + local H2/offline authentication flow.

## Architecture

- **Spring Security** — enforcement framework.
- **NIST Zero Trust principles** — architectural model: network location is not treated as proof of trust.
- **RBAC + scope** — P.U.L.S.E authorization model: STATE_ADMIN, DISTRICT_ADMIN, ADMIN, STAFF, with state/district/hospital scope carried by the authenticated principal and existing controller/service checks.
- **OWASP ASVS 5.0** — verification/security-requirements baseline.

## Implemented in this phase

1. Added `spring-boot-starter-security`.
2. Replaced the old MVC `SessionAccessInterceptor` authorization layer with Spring Security's filter chain.
3. Added a custom `AuthenticationProvider` so login still works against the existing `LoginService`, including local H2/offline authentication.
4. Added a serializable `UserPrincipal` so the JPA `User` entity is not placed directly in the Spring Security session.
5. Added explicit role boundaries:
   - `/admin/**` → ADMIN
   - `/staff/**` → STAFF
   - `/district-admin/**` → DISTRICT_ADMIN
   - `/state-admin/**` → STATE_ADMIN
6. Left public search, map, health, hospital registration/verification, and public APIs accessible without authentication.
7. Added session-fixation protection and a two-session concurrency limit.
8. Hardened session cookies: HttpOnly, SameSite=Lax, cookie-only tracking, 30-minute timeout.
9. Added CSRF protection. Thymeleaf forms use Spring MVC/Thymeleaf CSRF integration.
10. Added common security headers: frame denial, content-type protection, referrer policy, and HSTS for HTTPS deployments.
11. Added login brute-force throttling: five failures in the configured window trigger a temporary lockout for that username/IP key.
12. Added security audit logging for authentication success/failure and access-denied events. Passwords, tokens, and session IDs are never logged.
13. Added a dedicated access-denied page.
14. Preserved the existing staff post-login local stock synchronization behavior.
15. Preserved the existing session attributes (`role`, `stateId`, `districtId`, `hospitalId`) as a compatibility bridge for legacy controllers. Spring Security authorities remain the authoritative request-level authorization mechanism.

## Required deployment settings

### Railway / HTTPS
Set:

```text
PULSE_SESSION_COOKIE_SECURE=true
```

### Local LAN / HTTP
Keep:

```text
PULSE_SESSION_COOKIE_SECURE=false
```

The local LAN endpoint currently uses HTTP, so a Secure cookie would prevent the browser from sending the session cookie to the local node.

### LAN registration token (strongly recommended)
Set the same long random value on both the Railway environment and the local `.env.local`:

```text
PULSE_LAN_REGISTRATION_TOKEN=<long-random-secret>
```

The local node sends it as `X-Pulse-Lan-Token`; Railway verifies it before accepting the advertised LAN endpoint.

## First verification sequence

Run on the developer machine:

```powershell
./mvnw clean test
./mvnw spring-boot:run
```

If PowerShell blocks `mvnw.ps1`, use:

```powershell
bash mvnw clean test
```

or allow the wrapper script according to the machine's PowerShell execution policy.

Then test:

1. Public `/search`, `/map`, `/health` work while logged out.
2. `/admin/dashboard` redirects to `/login` while logged out.
3. ADMIN can reach `/admin/**` but receives the access-denied page for `/staff/**`.
4. STAFF can reach `/staff/**` but receives the access-denied page for `/admin/**`.
5. DISTRICT_ADMIN can reach `/district-admin/**` but not `/state-admin/**`.
6. STATE_ADMIN can reach `/state-admin/**` but not `/district-admin/**`.
7. Wrong passwords fail and repeated failures trigger temporary throttling.
8. Removing the CSRF token from a POST request causes Spring Security to reject the request.
9. Successful login changes the session identifier rather than preserving a pre-login identifier.
10. Logout removes the authenticated session and returns to `/login?logout=true`.
11. Local/offline staff login still works when the cloud is unavailable.
12. Staff login still performs the existing local stock synchronization when the local profile is active.
13. On Railway, verify `Set-Cookie` includes `Secure; HttpOnly; SameSite=Lax` for the session cookie.
14. Verify security response headers in browser DevTools/network or with an HTTP client.
15. Verify no `.env.local`, real passwords, CARTO keys, Supabase secrets, or LAN token are committed.

## Important limitation

The local hospital endpoint remains HTTP because the current LAN architecture intentionally uses `http://<LAN-IP>:8080`. Zero Trust does not make an unencrypted HTTP connection confidential. For a hostile/untrusted network, local HTTPS with a trusted certificate is still required. The current phase protects identity, authorization, session handling, CSRF, and application-level access, but it does not turn HTTP into HTTPS.

## Additional Phase 20 hardening

- Administrative request-decision archive routes are role-protected and scope-derived from the authenticated principal; hospital, district and state archive access cannot be widened by URL parameters.
- Archived decision pages send `Cache-Control: no-store, private` and `X-Robots-Tag: noindex, noarchive` because they contain sensitive operational history.
- A `Permissions-Policy` response header disables camera, microphone, payment and USB access and permits geolocation only for the application origin used by the map feature.
- Request decision archives are encrypted server-side and are never exposed as downloadable ciphertext to the browser.
- Retention purging is fail-safe: a hot request/stock row is deleted only when its corresponding encrypted archive exists. A missed scheduler run or cloud outage therefore does not turn into silent data loss.
- Monthly request-archive creation catches up the previous 12 closed months to tolerate temporary application downtime.
