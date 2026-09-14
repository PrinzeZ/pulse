# P.U.L.S.E — Real-Time Medicine Availability & Stock Alert System

> A Spring Boot enterprise application built for government hospitals to streamline pharmacy inventory tracking, automate low-stock alerts, and provide public transparency for medicine availability.

---

## System Architecture & Core Design

PULSE is structured using a clean, layered Spring Boot architecture ensuring strict separation of concerns:
- **Model Layer (`com.pulse.model`)**: Object-Oriented domain entities leveraging JPA/Hibernate (Abstract `User` hierarchy, `StockEntry` junction mapping, and dynamic `Medicine` thresholds).
- **Repository Layer (`com.pulse.repository`)**: Spring Data JPA repositories handling automated persistence and custom JPQL/Native queries.
- **Service Layer (`com.pulse.service`)**: Core business logic pipelines, including automated event-driven threshold checks and credential verification.
- **Controller Layer (`com.pulse.controller`)**: MVC routing handling REST endpoints and Thymeleaf views.

---

## Tech Stack

- **Backend Framework:** Spring Boot (v4.1.1)
- **Persistence & ORM:** Spring Data JPA / Hibernate, MySQL 8.0
- **Security:** Spring Security Crypto (`BCryptPasswordEncoder`)
- **Frontend Engine:** Thymeleaf, HTML5, CSS3
- **Build Tool:** Maven
- **Version Control:** Git / GitHub

---

## Core Pipeline (The Alert Engine)

When pharmacy staff update stock, the system triggers an automated execution pipeline:
1. **Data Ingestion**: `StaffService.updateStock()` captures hospital ID, medicine ID, and new quantity.
2. **Threshold Verification**: `StockEntry.checkThreshold(Medicine)` dynamically evaluates the stock against the medicine's minimum requirement.
3. **Event-Driven Alerting**: If the threshold condition fails, `AlertService` (decoupled via the `Notifiable` interface) instantly fires a low-stock alert to the persistence layer and admin dashboard.

---

##  Project Structure

```text
com.pulse
├── PulseApplication.java       # Main entry point (Embedded Tomcat launcher)
├── model/                      # Domain entities (@Entity maps to MySQL)
├── repository/                 # Spring Data JPA repositories
├── service/                    # Business logic & alert pipelines
├── controller/                 # Web traffic controllers & routers
└── exception/                  # Custom runtime exception handling