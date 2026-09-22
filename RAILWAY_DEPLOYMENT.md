# P.U.L.S.E — Railway deployment (Phases 11–13 online acceptance)

Railway is the real cloud deployment target for P.U.L.S.E. The Spring Boot server runs on Railway, so a phone does **not** need to be on the same LAN and the development PC does not need to stay running after deployment.

## Architecture

```text
Phone / laptop / any Internet connection
                 |
                 v
        Railway public HTTPS URL
                 |
                 v
        P.U.L.S.E Spring Boot
                 |
                 v
        Supabase PostgreSQL
```

H2 remains a local/offline cache. It is not enabled on the Railway deployment because Railway runs without the `local` profile.

## 1. Push this project to GitHub

Use the P.U.L.S.E repository and push the tested code to the branch Railway is configured to deploy. If Railway is currently connected to `main` but development is happening on `h2-phase-integration`, either change Railway's source branch to `h2-phase-integration` or merge the tested branch into `main`. Do not expect Railway to pick up commits from a different branch automatically.

## 2. Create the Railway service

In Railway:

1. Create a new project.
2. Choose **Deploy from GitHub repo**.
3. Select the P.U.L.S.E repository.
4. Deploy the service.
5. Railway detects the root `Dockerfile` and builds the Spring Boot application from it.
6. In **Settings → Networking**, generate a public domain.

Railway's official Spring Boot guide supports GitHub deployment and Dockerfile deployment.

## 3. Add Railway variables

In the service's **Variables** tab, add:

```text
SPRING_DATASOURCE_URL=<PASTE THE EXACT JDBC URL FROM SUPABASE CONNECT>
SPRING_DATASOURCE_USERNAME=<PASTE THE EXACT USERNAME FROM SUPABASE CONNECT>
SPRING_DATASOURCE_PASSWORD=<YOUR SUPABASE DB PASSWORD>

PULSE_MAIL_HOST=smtp.gmail.com
PULSE_MAIL_PORT=587
PULSE_MAIL_USERNAME=yourgmail@gmail.com
PULSE_MAIL_PASSWORD=your-gmail-app-password
PULSE_MAIL_FROM=yourgmail@gmail.com
PULSE_BASE_URL=https://YOUR-RAILWAY-DOMAIN.up.railway.app
```

Do **not** put the real database password or Gmail app password into GitHub.

### Supabase connection — important

Do **not** manually type or guess the Supabase pooler hostname or username. Supabase's current shared pooler format can require the project reference in the username, and the pooler host must be copied from the Supabase **Connect** dialog. Use the exact JDBC host/port/username shown there. For the shared Supavisor session pooler, Supabase currently uses port `5432`; transaction mode uses `6543`. P.U.L.S.E is configured for session mode by default because it uses normal long-lived JPA/Hikari sessions. Keep `sslmode=require&prepareThreshold=0` in the JDBC URL.

If the application cannot connect to Supabase, verify that `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` exactly match the current Supabase Connect dialog. Do not copy credentials from an older project or guess the pooler username.

Railway supplies a `PORT` variable automatically. P.U.L.S.E uses `${PORT:8080}`, so Railway's assigned port is respected.

## 4. Health check

`GET /health` returns HTTP 200 when the application is running. `railway.toml` configures Railway to use `/health` as the deployment health check.

## 5. Recommended first deployment order

1. Make sure the exact Supabase Connect values work locally with the `dev,local` profiles.
2. Push the tested code to GitHub.
3. Create the Railway project and GitHub service.
4. Add the Supabase variables first and deploy.
5. Confirm Railway logs show Spring Boot started and `/health` returns 200.
6. Generate the Railway public domain.
7. Only then add the SMTP variables and test registration/verification email.

Railway service variables are runtime environment variables; secrets should stay in Railway rather than being committed to GitHub.

## 6. Verify from outside the LAN

After deployment, open the Railway HTTPS URL from a phone using **mobile data** with Wi-Fi disabled.

Test:

- `/health`
- Login
- District Admin → Verifications
- District Admin → Transfers
- State Admin → Transfers
- Hospital Admin → Alerts
- Analytics at each permitted tier
- Staff → Transfers
- Staff → Audit
- `/api/public/status`
- `/api/public/hospitals`
- `/api/public/stock`

The URL must still work when the development PC is shut down. That is the real online acceptance test.

## Important deployment boundary

This completes the **online deployment foundation** for Phases 11–13 only after the external mobile-data acceptance test passes. It does not mean production hardening is complete. Domain hardening, rate limiting, security headers, backups, monitoring, failure testing and production operations belong to later phases.
