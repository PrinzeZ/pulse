# P.U.L.S.E Security Hardening Phase

This phase hardens the application beyond the existing RBAC, Zero Trust scope checks, CSRF, session, audit, and offline credential controls.

## Changes in this phase

- Policy acknowledgement is now part of authentication completion. A successful login is not returned unless the current policy acknowledgement is durably recorded in the cloud user record or, for an eligible offline hospital account, the hospital-local credential mirror.
- Policy persistence failures are no longer silently swallowed.
- Access-denied handling keeps the HTTP response at `403 Forbidden` while rendering the branded access-denied page.
- Apache Tomcat is pinned to `11.0.26`, the current security-fixed Tomcat 11.x release used with this project.
- Added a Content-Security-Policy covering the application's local assets and its explicitly used map/routing/CDN origins.
- TRACE requests are denied.
- The machine-to-machine LAN registration endpoint is exempted from browser CSRF because it authenticates with the dedicated `X-Pulse-Lan-Token` secret. The token comparison uses constant-time comparison and requires a non-trivial configured token length.
- Error responses no longer expose framework exception, message, stack-trace, or binding details.
- Docker runtime now uses a dedicated unprivileged `pulse` user.
- Development bootstrap passwords are no longer hardcoded in Java. They can be supplied through `PULSE_DEV_*_PASSWORD` variables; otherwise a random development-only password is generated at startup.
- Added unit tests for principal password isolation, local-network gating, access-denied status, and policy-acceptance durability/failure behavior.

## Required deployment settings

For an HTTPS deployment:

```text
PULSE_SESSION_COOKIE_SECURE=true
```

For a hospital-local node:

```text
PULSE_LOCAL_HOSPITAL_ID=<hospital_id>
PULSE_LAN_REGISTRATION_TOKEN=<long-random-secret>
PULSE_LAN_IP=<hospital-lan-ip>
```

The LAN registration token should be a long randomly generated secret and must be identical on the cloud registration endpoint and local node.

## Verification

Run locally before pushing:

```powershell
.\mvnw.cmd clean test
```

Then:

```powershell
.\mvnw.cmd spring-boot:run
```

The current execution environment could not download Maven 3.9.16 from Maven Central, so this artifact has **not** been falsely marked as Maven-test-passed here. Static source checks were completed successfully.
