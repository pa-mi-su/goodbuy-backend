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

**Rebuild flow - nuke and boot fresh against dev**

```bash
# 1) Stop everything
docker compose down

# 2) Remove ALL volumes (wipes Postgres, caches, etc)
docker compose down -v

# 3) Make sure you're on dev (which now = refactor branch)
git status
# should say: On branch dev / working tree clean

# 4) Build fresh JARs (uses dev code)
mvn -q -B -DskipTests clean package

# 5) Rebuild images with no cache
docker compose build --no-cache

# 6) Start stack with dev profile
SPRING_PROFILES_ACTIVE=dev docker compose up -d

# 7) Tail API logs to confirm migrations + startup
docker compose logs -f goodbuy-api
```

---

## Product API Endpoints Overview

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
  • ✅ Shape matches the iOS Product model
  • images → array of string URLs
  • ingredients → array of string names
  • claims / hazards → arrays (currently empty but reserved)
  • ✅ Cached for 5 min for responsiveness
  • ✅ Safe, stable contract (no nested DTOs)
  • 🔄 Internally uses the richer DTO but flattens it for backward compatibility

Intended Use

Use this endpoint for:
  • Mobile and web clients needing fast lookups
  • Scanning flows where only name, brand, images, and ingredient names are required

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
  • 🧩 Returns full ProductDetailDto
  • 📦 Includes nested image and ingredient objects
  • 💡 Enables future enrichment (toxicity scores, regulation data, etc.)
  • 🔄 Ideal for dashboards, admin tools, or advanced clients

Intended Use

Use this endpoint for:
  • Internal APIs, analysis tools, or future app versions
  • When you need structured metadata (ingredient IDs, external references, etc.)

---

## Product Lookup Caching and ETag Revalidation

The GoodBuy platform now implements a multi-layer caching strategy across both the iOS client and the backend API.
This approach significantly reduces redundant network calls, improves response latency, and maintains consistency between the client and server.

### High-Level Overview

When a product barcode is scanned, the request passes through several cache layers before reaching the external catalog:

iOS Memory Cache  →  iOS URLCache (ETag)  →  Backend ProductCache  →  External EAN-DB
↓                     ↓                        ↓
Immediate hit         304 Not Modified        Remote fetch if cache miss

![Caching Flow](docs/goodbuy_caching_flow_v2.png)

---

### iOS Client Implementation

**Files:** `GoodBuyBackendProvider.swift`, `ResultViewModel.swift`

#### In-Memory TTL Cache (~15 seconds)

- Repeated scans of the same product within approximately 15 seconds are served directly from memory.
- This layer prevents any network request and provides an instantaneous user experience.

#### System URLCache with ETag Revalidation

- The client relies on the backend’s ETag headers for HTTP revalidation.
- When a cached item is requested again, iOS automatically includes an `If-None-Match` header.
- If the ETag matches, the backend returns `304 Not Modified`, and the cached body is reused.
- If the product has changed, the backend returns `200 OK` with updated data, which the client stores automatically.

---

### Backend Implementation

**File:** `ProductController.java`

#### ETag Support

- Each `/v1/products/{code}` response includes a weak ETag (`W/"sha256…"`) generated from the serialized JSON body.
- If the client provides `If-None-Match`, the controller compares hashes and returns `304 Not Modified` if the payload is unchanged.

#### Cache-Control Policy

```http
Cache-Control: public, max-age=300, stale-while-revalidate=60
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
