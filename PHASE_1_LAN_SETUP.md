# P.U.L.S.E LAN Setup Guide

## 1. LAN Architecture

P.U.L.S.E runs as a central Spring Boot server inside the hospital LAN.

```text
Hospital LAN
    |
    +-- Admin PC
    +-- Staff PC
    +-- Pharmacy PC
    |
    v
P.U.L.S.E Spring Boot Server :8080
    |
    v
Central PostgreSQL Database
```

Client computers use a normal web browser and do not connect directly to PostgreSQL.

## 2. Requirements

- Java 17 JDK
- Maven
- Existing P.U.L.S.E PostgreSQL configuration
- A computer capable of acting as the hospital server
- Client computers connected to the same LAN

## 3. Database Configuration

P.U.L.S.E currently uses PostgreSQL. Preserve the existing JDBC configuration, including `prepareThreshold=0` and `sslmode=require`. PostgreSQL must not be exposed directly to client computers.

## 4. Starting the Server

Open a terminal in `C:\Projects\pulse` and run:

```bash
mvn spring-boot:run
```

The server listens on port `8080` and is configured to listen on LAN interfaces using `server.address=0.0.0.0`.

## 5. Finding the Server LAN IP on Windows

`start.ps1` detects the active private IPv4 address automatically and prints it.

You can also run:

```text
ipconfig
```

and use the IPv4 address of the active Ethernet/Wi-Fi adapter. Do not hard-code an example IP in the application.

## 6. LAN Access

From another computer on the same LAN, open:

`http://<SERVER-LAN-IP>:8080/`

The server itself can continue to use `http://localhost:8080/`.

## 7. Firewall

If LAN clients cannot connect, allow inbound TCP port `8080` on the hospital server's private LAN network. Do not expose the PostgreSQL port to client computers.

## 8. Login

Open `/login` and use the existing P.U.L.S.E credentials. Do not store passwords or secrets in this document.

## 9. Troubleshooting

- Verify Spring Boot is running.
- Verify the server's current LAN IPv4 address with `ipconfig`.
- Verify client and server are on the same LAN.
- Verify TCP port `8080` is permitted by the server firewall.
- If the database is unavailable, inspect the server logs and existing PostgreSQL/Supabase configuration.

## 10. Safe Shutdown

Press `Ctrl+C` in the terminal running P.U.L.S.E.

## 11. Phase 1 Scope

Phase 1 provides LAN access to the existing application. Cloud synchronization, offline synchronization, maps, Docker, State/District administration, supply-chain workflows, and public multi-hospital infrastructure remain outside this phase.
