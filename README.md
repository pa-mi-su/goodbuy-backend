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

## 🧭 System Architecture Diagram

```text
┌─────────────────────────────────────────────────────────────────────┐
│                             iOS / Frontend                          │
│─────────────────────────────────────────────────────────────────────│
│ - Scans barcode (e.g. 0033200011408)                                │
│ - Calls backend: GET /v1/products/{code}                            │
└─────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          goodbuy-api (Spring Boot)                  │
│─────────────────────────────────────────────────────────────────────│
│ 🧩 REST Controllers                                                  │
│   • ProductController (/v1/products)                                │
│   • IngredientController (/api/ingredients)                         │
│                                                                     │
│ 🧠 Services                                                          │
│   • ProductService  → orchestrates catalog lookup                   │
│   • IngredientReadService → orchestrates ingredient DB reads        │
│                                                                     │
│ 🪄 Shared Infrastructure                                             │
│   • RequestLoggingFilter, GlobalExceptionHandler                    │
│   • CORS & Swagger config                                           │
└─────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        goodbuy-core (Domain Layer)                  │
│─────────────────────────────────────────────────────────────────────│
│ 🧱 DTOs & Ports (Pure Java)                                         │
│   • ProductDetailDto, IngredientDTO                                 │
│   • IngredientReadPort, ExternalCatalogClient                       │
│   • BarcodeNormalizer, Enums, Util classes                          │
│                                                                     │
│ ❗ No Spring, no HTTP, no DB — pure data and contracts.              │
└─────────────────────────────────────────────────────────────────────┘
          │                                 │
          ▼                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│             goodbuy-adapters-catalog (External Providers)           │
│─────────────────────────────────────────────────────────────────────│
│ 🌐 EanDbCatalogClient                                               │
│   - Calls https://ean-db.com/api/v2/product/{gtin}                  │
│   - Maps JSON → ProductDetailDto                                    │
│                                                                     │
│ 🌐 EanSearchClient (optional fallback)                              │
│   - Alternative provider (https://api.ean-search.org)               │
│                                                                     │
│ ⚙️ CatalogConfig / CatalogProperties                                │
│   - Chooses which provider to activate                              │
│   - Handles timeouts, API keys, etc.                                │
└─────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         Postgres Database                           │
│─────────────────────────────────────────────────────────────────────│
│ 🧬 IngredientRepository (JPA)                                       │
│   - Reads ingredient data                                           │
│   - Used by CoreIngredientReadAdapter                               │
│                                                                     │
│ 🧬 Tables: ingredients, aliases, hazards, tags, etc.                │
└─────────────────────────────────────────────────────────────────────┘

iOS → ProductController → ProductService → EanDbCatalogClient → EAN-DB API → JSON mapped to ProductDetailDto → returned to iOS

iOS → IngredientController → IngredientReadService → CoreIngredientReadAdapter → IngredientRepository (Postgres) → IngredientDTO → returned to iOS

🔄 End-to-End Request Flow

1️⃣ Product Lookup
	1.	iOS app scans a barcode and calls:

GET /v1/products/0033200011408

	2.	ProductController validates and normalizes the code → GTIN-14.
	3.	ProductService asks ExternalCatalogClient (e.g., EanDbCatalogClient) for details.
	4.	EanDbCatalogClient calls EAN-DB, maps the JSON response → ProductDetailDto.
	5.	The result is wrapped into a ProductView or returned directly for /detail.
	6.	Response sent back to iOS:

{
  "gtin": "0033200011408",
  "name": "Arm & Hammer Pure Baking Soda, 2 Lb Box",
  "brand": "Arm & Hammer",
  "category": "Baking Soda",
  "images": [...],
  "ingredients": [...],
  "source": "EAN-DB"
}

2️⃣ Ingredient Detail Lookup
	1.	Client requests:

GET /api/ingredients/sodium-bicarbonate

	2.	IngredientController → IngredientReadService → IngredientReadPort.
	3.	CoreIngredientReadAdapter queries Postgres (IngredientRepository).
	4.	Result mapped → IngredientDTO and returned as JSON.

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

> **Note:** All credentials and JWTs live in `.secrets/app-secrets.properties` and `.secrets/db-secrets.properties`. These files are private and excluded via `.gitignore`.

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


1) Clean + rebuild the JAR (to purge the old file from the classpath)
mvn -q -B -DskipTests clean package -pl goodbuy-api -am

2) Rebuild the Docker image (no cache) and start API
docker compose build --no-cache goodbuy-api
docker compose up -d goodbuy-api

3) Tail Logs
docker compose logs goodbuy-api --tail=200

----
## Stack

- Spring Boot 3.3, Java 17
- Dockerized Postgres + Flyway migrations
- Modular POM, OpenAPI docs
- Ready for multi-service expansion
- Clean CI/CD readiness for AWS or bare EC2

---

🧭 Product API Endpoints Overview

GoodBuy exposes two main product endpoints under /v1/products.
They serve different data shapes and use cases.

⸻

GET /v1/products/{code} — Simple / Mobile-Friendly

This endpoint returns a flattened product view designed for lightweight clients such as the iOS app.

Example Response:
{
  "gtin": "0033200011408",
  "name": "Arm & Hammer Pure Baking Soda, 2 Lb Box",
  "brand": "Arm & Hammer",
  "category": "Baking Soda",
  "images": [
    "https://images.ean-db.com/.../0033200011408/..."
  ],
  "ingredients": [
    "Sodium Bicarbonate"
  ],
  "claims": [],
  "hazards": [],
  "source": "EAN-DB"
}

Key Points
	•	✅ Shape matches the iOS Product model
	•	images → array of string URLs
	•	ingredients → array of string names
	•	claims / hazards → arrays (currently empty but reserved)
	•	✅ Cached for 5 min for responsiveness
	•	✅ Safe, stable contract (no nested DTOs)
	•	🔄 Internally uses the richer DTO but flattens it for backward compatibility

Intended Use

Use this endpoint for:
	•	Mobile and web clients needing fast lookups
	•	Scanning flows where only name, brand, images, and ingredient names are required

⸻

GET /v1/products/{code}/detail — Rich / Developer / Future-Oriented

This endpoint returns the full structured DTO with detailed fields.

Example Response

{
  "gtin": "0033200011408",
  "name": "Arm & Hammer Pure Baking Soda, 2 Lb Box",
  "brand": "Arm & Hammer",
  "category": "Baking Soda",
  "images": [
    { "url": "...", "width": 500, "height": 500 }
  ],
  "ingredients": [
    {
      "id": "e500-ii",
      "original": "Sodium Bicarbonate",
      "canonical": "Baking Soda (Sodium Bicarbonate, E500-ii)",
      "externalIds": { "cosIng": "37736" },
      "isVegan": true,
      "isVegetarian": true
    }
  ],
  "source": "EAN-DB"
}

Key Points
	•	🧩 Returns full ProductDetailDto
	•	📦 Includes nested image and ingredient objects
	•	💡 Enables future enrichment (toxicity scores, regulation data, etc.)
	•	🔄 Ideal for dashboards, admin tools, or advanced clients

Intended Use

Use this endpoint for:
	•	Internal APIs, analysis tools, or future app versions
	•	When you need structured metadata (ingredient IDs, external references, etc.)

⸻

📘 Summary

/v1/products/{code} * Simple, flattened product view * Arrays of strings * Current iOS app

/v1/products/{code}/detail * Full structured DTO * Nested objects * Admin tools, future clients

Design Philosophy
	•	Maintain backward-compatible responses for existing mobile apps.
	•	Allow gradual evolution toward richer, self-descriptive data models.
	•	Internally, both endpoints share the same lookup and normalization logic but differ only in serialization.

---

flowchart TD

subgraph IOS["📱 iOS App / Frontend"]
    IOSScan[Scan barcode]
    IOSReq[GET /v1/products/{code}\nGET /v1/products/{code}/detail\nGET /api/ingredients/{key}"]
    IOSScan --> IOSReq
end

subgraph API["goodbuy-api (Spring Boot)"]
    PC[ProductController]
    IC[IngredientController]
    PS[ProductService]
    IRS[IngredientReadService]
    LOG[RequestLoggingFilter]
    EX[GlobalExceptionHandler]
end

subgraph CORE["goodbuy-core (Domain & Ports)"]
    PDD[ProductDetailDto]
    IDTO[IngredientDTO]
    IRP[IngredientReadPort]
    ECC[ExternalCatalogClient]
    BN[BarcodeNormalizer]
end

subgraph ADAPT["goodbuy-adapters-catalog (External adapters)"]
    CFG[CatalogConfig & CatalogProperties]
    EDB[EanDbCatalogClient]
    ES[EanSearchClient]
end

subgraph DB["PostgreSQL"]
    IR[IngredientRepository (JPA)]
end

EXT_EANDB["EAN-DB API"]
EXT_EANS["EAN-Search API (optional)"]

IOSReq --> PC
IOSReq --> IC

PC --> PS
IC --> IRS

PS --> BN
PS --> ECC

ECC -.interface-. EDB
ECC -.interface-. ES

EDB --> EXT_EANDB
ES --> EXT_EANS

EDB --> PDD
ES --> PDD

PS --> PDD
PC --> IOSReq

IRS --> IRP
IRP -.implemented by-. IR
IR --> DB
IRS --> IDTO
IC --> IOSReq

---
## License

© 2025 GoodBuy. All rights reserved.
