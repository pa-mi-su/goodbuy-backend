# 🛒 GoodBuy Backend

[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue)](https://docs.docker.com/compose/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-lightblue)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-orange)](https://flywaydb.org/)

**GoodBuy Backend** is a Spring Boot service that powers the GoodBuy iOS app with REST APIs for barcode-based product lookups and ingredient metadata.

---

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started (Local, Docker)](#getting-started-local-docker)
  - [1) Create private configuration](#1-create-private-configuration)
  - [2) Build & run with Docker Compose](#2-build--run-with-docker-compose)
  - [3) Verify health & docs](#3-verify-health--docs)
  - [4) Connect with a SQL client (optional)](#4-connect-with-a-sql-client-optional)
- [Spring Profiles](#spring-profiles)
- [Environment & Secrets](#environment--secrets)
- [API Usage](#api-usage)
- [Logging](#logging)
- [Troubleshooting](#troubleshooting)
- [For Recruiters](#for-recruiters)
- [License](#license)

---

## Overview

- **Language:** Java 17
- **Framework:** Spring Boot 3.3.x
- **Database:** PostgreSQL 16 (Dockerized)
- **Migrations:** Flyway (auto-run on startup)
- **Docs:** OpenAPI/Swagger (`/v3/api-docs`)
- **Profiles:** `dev`, `prod`
- **Config:** `.env` + Docker secrets + Spring `application-*.properties`
- **Containers:** Docker Compose

---

## Architecture

```
iOS App (SwiftUI)
     │  GET /v1/products/{gtin}
     ▼
GoodBuy Backend (Spring Boot)
     │
     ├─ PostgreSQL 16 (Flyway migrations)
     └─ EAN-DB (external provider via JWT)
```

---

## Project Structure

```
goodbuy-backend/
├── goodbuy-api/
│   ├── src/main/java/app/goodbuy/...
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── application-dev.properties
│   │   ├── application-prod.properties
│   │   └── db/migration/
│   ├── Dockerfile
│   └── pom.xml
├── docker-compose.yml
├── .secrets/
│   └── app-secrets.properties
├── .env
└── README.md
```

> **Note:** All credentials and JWTs live in `.env` and `.secrets/app-secrets.properties`. These files are private and excluded via `.gitignore`.

---

## Getting Started (Local, Docker)

### 1) Create private configuration

**.env**

```dotenv
POSTGRES_DB=bpdb
POSTGRES_USER=bpuser
POSTGRES_PASSWORD=replace-with-strong-password
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
HOST_PORT_POSTGRES=5433
DB_URL=jdbc:postgresql://postgres:5432/bpdb
DB_URL_HOST=jdbc:postgresql://localhost:5433/bpdb
```

**.secrets/app-secrets.properties**

```properties
goodbuy.catalog.enabled=true
goodbuy.catalog.eandb.jwt=REPLACE_WITH_REAL_LONG_JWT_TOKEN
```

### 2) Build & run with Docker Compose

```bash
docker compose up -d --build postgres
docker compose up -d --build goodbuy-api
```

### 3) Verify health & docs

```bash
curl -fsS http://localhost:8080/actuator/health && echo
curl -fsS http://localhost:8080/actuator/info && echo
curl -fsS http://localhost:8080/v3/api-docs | head -c 400 && echo
```

### 4) Connect with SQL client (optional)

**DBeaver / psql connection:**
Host: `localhost`
Port: `5433`
DB: `bpdb`
User: `bpuser`
Pass: `replace-with-strong-password`

---

## Spring Profiles

```yaml
environment:
  SPRING_PROFILES_ACTIVE: dev   # or prod
```

Or via CLI:

```bash
java -jar app.jar --spring.profiles.active=prod
```

---

## API Usage

```bash
curl -fsS "http://localhost:8080/v1/products/0808124111042" | jq .
```

**Health / Info:**
```bash
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8080/actuator/info
```

---

## Logging and Running

•	Dev (plain logs):
        SPRING_PROFILES_ACTIVE=dev docker compose up -d --build && docker compose logs -f goodbuy-api

•	Prod (plain logs):
        SPRING_PROFILES_ACTIVE=prod docker compose up -d --build && docker compose logs -f goodbuy-api

•	Prod (JSON logs):
        SPRING_PROFILES_ACTIVE=prod,prod-json docker compose up -d --build && docker compose logs -f goodbuy-api

---

## Stack

- Spring Boot 3.3, Java 17
- Dockerized Postgres + Flyway migrations
- Modular POM, OpenAPI docs
- Ready for multi-service expansion
- Clean CI/CD readiness for AWS or bare EC2

---

## License

© 2025 GoodBuy. All rights reserved.
