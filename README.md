# GoodBuy Backend

[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-lightblue)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-orange)](https://flywaydb.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue)](https://docs.docker.com/compose/)

GoodBuy Backend is a modular Spring Boot service that powers the GoodBuy iOS app. It handles barcode-based product lookup, ingredient normalization, safety scoring, user scan history, favorites, and evidence-driven recovery when catalog data is incomplete.

## What It Does

- Serves fast product scan responses through a DB-first lookup path with async ingestion on cache miss
- Integrates with external catalog providers such as EAN-DB to bootstrap product metadata
- Persists normalized products, ingredients, aliases, evidence signals, and user-facing snapshots in PostgreSQL
- Scores ingredients and products using a rule-based safety engine instead of optimistic default grading
- Routes low-confidence and low-coverage scans into review queues instead of silently failing
- Accepts product evidence and ingredient evidence to improve bad scans over time

## Highlights

- **Async scan ingestion:** first-time scans return quickly while enrichment, snapshot persistence, and scoring continue in the background
- **Deterministic scoring:** product grades are driven by ingredient evidence and curated rules, not a “safe until proven unsafe” default
- **Recovery workflows:** missing products, unclear ingredient lists, and unmatched ingredients are all captured in explicit review queues
- **Seeded ingredient pipeline:** canonical ingredient seeds and aliases improve first-pass matching before manual review is needed
- **Evidence loop:** backend supports product evidence uploads and an ingredient-evidence ingestion path for repaired low-coverage scans

## Architecture

```text
iOS App
  -> GET /v1/products/{gtin}
  -> POST /api/v1/history/scan
  -> GET /api/ingredients/{key}
  -> POST /api/v1/products/evidence
  -> POST /api/v1/products/evidence/ingredients

goodbuy-api (Spring Boot)
  -> ProductController / ProductService
  -> IngredientController / IngredientReadService
  -> HistoryController / FavoriteController
  -> MagicLinkAuthController / UserRegistrationController
  -> ProductEvidenceReportController
  -> AsyncProductIngestionService + Worker

goodbuy-core (domain + ports)
  -> DTOs, scoring engines, ports, parsers

Adapters
  -> goodbuy-adapters-catalog: EAN-DB / EAN-Search
  -> goodbuy-adapters-core: JPA, Flyway, storage, notifications, lookup, snapshot persistence
  -> goodbuy-adapters-enrichment: enrichment pipeline integrations

PostgreSQL
  -> products, ingredients, aliases, product_ingredients
  -> ingredient_signals
  -> scan_history, favorite_product
  -> ingredient_missing_report, product_evidence_report
```

## End-To-End Scan Flow

### Existing product

1. iOS calls `GET /v1/products/{gtin}`
2. Backend reads GoodBuy DB first
3. If a snapshot already exists, the API returns it immediately
4. ETag support allows fast `304 Not Modified` revalidation for repeat reads

### First-time product

1. Backend checks GoodBuy DB
2. On miss, it fetches the product from the external catalog
3. The API returns the external result immediately
4. Background ingestion persists the snapshot, creates ingredient links, enriches, and scores
5. Later reads resolve from GoodBuy DB

### Low-coverage or bad ingredient list

1. Product still persists when possible instead of failing hard
2. Unmatched ingredients are routed into `ingredient_missing_report`
3. Product-level issues are routed into `product_evidence_report`
4. Ingredient evidence can be submitted later to reprocess the product with corrected ingredient text

## Project Structure

```text
goodbuy-backend/
├─ pom.xml
├─ docker-compose.yml
├─ docs/
│  └─ schema-notes.md
├─ goodbuy-api/
│  └─ src/main/java/app/goodbuy/
│     ├─ GoodBuyBackendApplication.java
│     ├─ auth/
│     ├─ favorites/
│     ├─ history/
│     ├─ ingredients/
│     ├─ products/
│     ├─ users/
│     └─ api/
├─ goodbuy-core/
│  └─ src/main/java/app/goodbuy/core/
│     ├─ ingredients/
│     ├─ products/
│     └─ storage/
├─ goodbuy-adapters-catalog/
│  └─ src/main/java/app/goodbuy/adapters/catalog/
│     ├─ eandb/
│     └─ eansearch/
├─ goodbuy-adapters-core/
│  └─ src/main/java/app/goodbuy/adapters/core/
│     ├─ ingredients/
│     ├─ notifications/
│     ├─ products/
│     ├─ storage/
│     └─ users/
└─ goodbuy-adapters-enrichment/
   └─ src/main/java/app/goodbuy/enrichment/
```

## Key Backend Capabilities

### Product APIs

- `GET /v1/products/{code}`: DB-first product read with async ingestion fallback
- `GET /v1/products/{code}/detail`: product detail variant
- `GET /v1/product-domains`: supported product domains

### Ingredient APIs

- `GET /api/ingredients/{nameOrKey}`: canonical ingredient details
- `GET /api/ingredients?q=...`: strict ingredient search
- `POST /api/ingredients/_batch`: batch ingredient lookup

### User APIs

- `POST /api/v1/auth/magic-link/request`
- `POST /api/v1/auth/magic-link/consume`
- `POST /api/v1/users/register`
- `GET /api/v1/users/me`
- `GET /api/v1/history`
- `POST /api/v1/history/scan`
- `GET /api/v1/favorites`
- `POST /api/v1/favorites`

### Evidence And Recovery APIs

- `POST /api/v1/products/evidence`: submit missing-product / unclear-ingredient / out-of-domain evidence with images
- `GET /api/v1/products/evidence/status`: evidence status lookup
- `POST /api/v1/products/evidence/ingredients`: submit corrected ingredient text or images for low-coverage products and trigger reprocessing
- `POST /api/v1/ingredients/missing`: report unmatched ingredient strings

## Data Model

Core catalog tables:

- `products`
- `ingredients`
- `ingredient_alias`
- `product_ingredients`
- `ingredient_signals`

User snapshot tables:

- `scan_history`
- `favorite_product`

Review and recovery tables:

- `ingredient_missing_report`
- `product_evidence_report`

Additional schema notes live in [docs/schema-notes.md](/Users/pms/Documents/Projects/goodbuy-backend/docs/schema-notes.md).

## Scoring Model

The backend uses a rule-based safety model for both ingredient and product scoring.

- High-signal ingredients and classes are handled through curated rules
- Unknown evidence does not automatically become a green score
- Product scoring is not a naive average; higher-risk ingredients cap the product more aggressively
- Low-confidence products stay unrated instead of receiving false-positive safety grades

Related docs:

- [SCORING.md](/Users/pms/Documents/Projects/goodbuy-backend/SCORING.md)
- [SEED.md](/Users/pms/Documents/Projects/goodbuy-backend/SEED.md)

## Local Development

### Requirements

- Java 17
- Maven
- Docker / Docker Compose

### Run locally

```bash
docker compose up --build
```

The API starts on `http://localhost:8080`.

### Reset local database

```bash
docker compose down -v
docker compose up --build
```

### Verify the backend

```bash
mvn verify
```

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.x |
| Database | PostgreSQL 16 |
| Persistence | Spring Data JPA |
| Migrations | Flyway |
| External Catalogs | EAN-DB, EAN-Search |
| Storage | Amazon S3 |
| Docs | OpenAPI / Swagger |
| Build | Maven |
| Local Runtime | Docker Compose |
| Architecture | Modular hexagonal / ports-and-adapters |

## Why This Project Is Interesting

This backend is not just a CRUD API. It combines:

- real-world catalog ingestion from imperfect third-party data
- normalization and alias matching over messy ingredient strings
- async workflows for latency-sensitive mobile scanning
- rule-based risk scoring
- explicit evidence and recovery loops for bad data

That makes it a good example of pragmatic backend engineering around unreliable inputs, user-facing performance, and data quality feedback loops.

## License

© 2026 GoodBuy. All rights reserved.
