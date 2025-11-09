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
- [System Architecture Diagram](#system-architecture-diagram)
- [Project Structure](#project-structure)
- [Logging and Running](#logging-and-running)
- [Product API Endpoints](#product-api-endpoints-overview)
- [Tech Stack](#tech-stack)
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

## System Architecture Diagram

```text
┌─────────────────────────────────────────────────────────────────────┐
│                             iOS / Frontend                         │
│─────────────────────────────────────────────────────────────────────│
│ - Scans barcode (e.g. 0033200011408)                               │
│ - Calls backend: GET /v1/products/{code}                           │
└─────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          goodbuy-api (Spring Boot)                 │
│─────────────────────────────────────────────────────────────────────│
│ REST Controllers                                                   │
│   • ProductController (/v1/products)                               │
│   • IngredientController (/api/ingredients)                        │
│                                                                     │
│ Services                                                           │
│   • ProductService → orchestrates catalog lookup                   │
│   • IngredientReadService → orchestrates ingredient DB reads       │
│                                                                     │
│ Shared Infrastructure                                              │
│   • RequestLoggingFilter, GlobalExceptionHandler                   │
│   • CORS & Swagger config                                          │
└─────────────────────────────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        goodbuy-core (Domain Layer)                 │
│─────────────────────────────────────────────────────────────────────│
│ DTOs & Ports (Pure Java)                                           │
│   • ProductDetailDto, IngredientDto                                │
│   • ExternalCatalogClient, IngredientReadPort                      │
│   • BarcodeNormalizer, enums, utils                                │
│                                                                     │
│ No Spring, no HTTP, no DB — pure data + contracts                  │
└─────────────────────────────────────────────────────────────────────┘
          │                                 │
          ▼                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│             goodbuy-adapters-catalog (External Providers)          │
│─────────────────────────────────────────────────────────────────────│
│ • EanDbCatalogClient                                               │
│     - Calls https://ean-db.com/api/v2/product/{gtin}               │
│     - Maps JSON → ProductDetailDto                                 │
│ • EanSearchClient (optional)                                       │
│ • CatalogConfig / CatalogProperties                                │
│     - Chooses provider, configures timeouts & API keys             │
└─────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    goodbuy-adapters-core + Postgres                │
│─────────────────────────────────────────────────────────────────────│
│ • CoreIngredientReadAdapter                                        │
│ • IngredientRepository (JPA)                                       │
│ • Tables: ingredients, aliases, hazards, tags, etc.                │
└─────────────────────────────────────────────────────────────────────┘
```

**End-to-end flow (simplified)**

- iOS → `ProductController` → `ProductService` → `EanDbCatalogClient` → EAN-DB API → `ProductDetailDto` → response to iOS
- iOS → `IngredientController` → `IngredientReadService` → `CoreIngredientReadAdapter` → `IngredientRepository` (Postgres) → `IngredientDto` → response to iOS

---

## Project Structure

```text
goodbuy-backend/
├─ pom.xml
├─ docker-compose.yml
│
├─ goodbuy-api/                          # REST API (Spring Boot)
│  └─ src/main/java/app/goodbuy/
│     ├─ GoodBuyBackendApplication.java
│     ├─ api/
│     │  ├─ RequestLoggingFilter.java
│     │  └─ GlobalExceptionHandler.java
│     ├─ config/
│     │  ├─ WebConfig.java
│     │  └─ AppProperties.java
│     ├─ products/
│     │  ├─ ProductController.java
│     │  └─ ProductService.java
│     └─ ingredients/
│        ├─ IngredientController.java
│        └─ IngredientReadService.java
│
├─ goodbuy-core/                         # Domain logic + DTOs + ports
│  └─ src/main/java/app/goodbuy/core/
│     ├─ products/
│     │  ├─ dto/ProductDetailDto.java
│     │  ├─ ports/ExternalCatalogClient.java
│     │  └─ util/BarcodeNormalizer.java
│     └─ ingredients/
│        ├─ dto/IngredientDto.java
│        └─ ports/IngredientReadPort.java
│
├─ goodbuy-adapters-catalog/             # External catalog integrations
│  └─ src/main/java/app/goodbuy/adapters/catalog/
│     ├─ CatalogConfig.java
│     ├─ CatalogProperties.java
│     ├─ eandb/EanDbCatalogClient.java
│     └─ eansearch/EanSearchClient.java
│
├─ goodbuy-adapters-core/                # Postgres adapter
│  └─ src/main/java/app/goodbuy/adapters/core/
│     ├─ CoreIngredientReadAdapter.java
│     ├─ repository/IngredientRepository.java
│     └─ entities/
│        ├─ IngredientEntity.java
│        ├─ AliasEntity.java
│        └─ HazardEntity.java
│
└─ goodbuy-migrations/                   # Flyway migrations
   └─ src/main/resources/db/migration/
      ├─ V1__ingredients_init.sql
      ├─ V2__aliases_table.sql
      └─ V3__hazards_table.sql
```

### Module Overview

The project uses a **modular, hexagonal architecture**:

- **goodbuy-api** – HTTP edge: controllers, filters, configs. Talks only to services/ports.
- **goodbuy-core** – Domain contracts + DTOs. No framework dependencies.
- **goodbuy-adapters-catalog** – External API clients implementing `ExternalCatalogClient`.
- **goodbuy-adapters-core** – Postgres adapter implementing `IngredientReadPort`.
- **goodbuy-migrations** – Flyway migrations for schema.

---

## Logging and Running

**Dev**

```bash
SPRING_PROFILES_ACTIVE=dev docker compose up -d --build
docker compose logs -f goodbuy-api
```

**Prod**

```bash
SPRING_PROFILES_ACTIVE=prod docker compose up -d --build
docker compose logs -f goodbuy-api
```

**Prod (JSON logs)**

```bash
SPRING_PROFILES_ACTIVE=prod,prod-json docker compose up -d --build
docker compose logs -f goodbuy-api
```

**Rebuild flow**

```bash
mvn -q -B -DskipTests clean package -pl goodbuy-api -am
docker compose build --no-cache goodbuy-api
docker compose up -d goodbuy-api
docker compose logs goodbuy-api --tail=200
```

---

## Product API Endpoints Overview

GoodBuy exposes two main endpoints under `/v1/products`:

### `GET /v1/products/{code}` — Simple / Mobile-Friendly

Flattened shape for the iOS app.

- `images`: array of URL strings
- `ingredients`: array of ingredient names
- Cached for 5 minutes
- Backward-compatible & lightweight

### `GET /v1/products/{code}/detail` — Rich / Future-Oriented

Returns full `ProductDetailDto`:

- Nested image objects (`url`, dimensions, etc.)
- Nested ingredient objects (ids, external IDs, vegan flags, etc.)
- Ideal for internal tools / future richer clients

Both endpoints share the same lookup + normalization logic; only the response shape differs.

---

## Tech Stack

| Layer          | Technology                        |
|----------------|-----------------------------------|
| Language       | Java 17                           |
| Framework      | Spring Boot 3.3.x                 |
| Database       | PostgreSQL 16                     |
| Migrations     | Flyway                            |
| Containerization | Docker / Docker Compose        |
| API Docs       | OpenAPI / Swagger                 |
| Logging        | Structured logs + Request IDs     |
| Architecture   | Modular Hexagonal                 |

---

## License

© 2025 GoodBuy. All rights reserved.
