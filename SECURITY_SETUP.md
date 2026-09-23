# P.U.L.S.E Phase 15 Security Setup

## Security architecture

P.U.L.S.E uses three complementary layers:

1. **Spring Security** — the implementation framework enforcing authentication, authorization, sessions, CSRF and security headers.
2. **Zero Trust principles** — network location (including a hospital LAN) does not automatically make a request trusted; identity and authorization are still required.
3. **OWASP ASVS** — the security requirements/checklist used to review authentication, sessions, access control, validation, cryptography, logging, API security and configuration.

P.U.L.S.E-specific authorization is RBAC plus resource scope: State Admin, District Admin, Hospital Admin and Staff must only operate within resources they are authorized to access.

## LAN registration secret

The local P.U.L.S.E instance periodically tells the Railway instance its current LAN URL. The registration endpoint is now fail-closed: Railway will reject registration unless `PULSE_LAN_REGISTRATION_TOKEN` is configured and the incoming `X-Pulse-Lan-Token` matches it.

### Local computer

Create:

```text
pulse/.env.local
```

This file is intentionally ignored by Git.

Add:

```properties
PULSE_LAN_IP=YOUR_CURRENT_LAN_IP
PULSE_LAN_REGISTRATION_TOKEN=YOUR_RANDOM_SECRET
PULSE_SESSION_COOKIE_SECURE=false
```

`start.ps1` may populate `PULSE_LAN_IP` automatically; do not hard-code it if the script already manages it.

### Railway

Open:

**Railway project → P.U.L.S.E service → Variables**

Create:

```text
PULSE_LAN_REGISTRATION_TOKEN=THE_EXACT_SAME_RANDOM_SECRET
PULSE_SESSION_COOKIE_SECURE=true
```

Do not put the token in Java source, HTML, JavaScript, GitHub, or the ZIP.

### Generate a secret on Windows PowerShell

Run:

```powershell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Maximum 256 } | ForEach-Object {[byte]$_}))
```

Copy the resulting value into both places. Do not send the value in chat.

### Important

The token is only for **local P.U.L.S.E → Railway LAN registration**. It is not a user password and it is not the authentication mechanism for hospital staff/admins.

If the Railway variable is missing, `/api/public/lan/register` deliberately returns HTTP 503 rather than accepting unauthenticated registrations.
