# P.U.L.S.E Phase 4 Email Setup

Phase 4 sends real verification and activation emails through SMTP.

Before starting P.U.L.S.E, set these environment variables in PowerShell:

```powershell
$env:PULSE_MAIL_HOST="smtp.gmail.com"
$env:PULSE_MAIL_PORT="587"
$env:PULSE_MAIL_USERNAME="your-sender@gmail.com"
$env:PULSE_MAIL_PASSWORD="your-gmail-app-password"
$env:PULSE_MAIL_FROM="your-sender@gmail.com"
$env:PULSE_BASE_URL="http://localhost:8080"
```

For a Gmail sender, use a Google App Password rather than your normal Google password. The recipient can be a Brave email alias that forwards to the real mailbox.

For a LAN deployment, change `PULSE_BASE_URL` to the address that hospital users can reach, for example:

```powershell
$env:PULSE_BASE_URL="http://192.168.1.50:8080"
```

Do not commit SMTP passwords or app passwords to Git.

If SMTP is not configured, hospital registration email delivery will fail instead of pretending that an email was sent.
