# GoodBuy Backend

[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-lightblue)](https://www.postgresql.org/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-orange)](https://flywaydb.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue)](https://docs.docker.com/compose/)

GoodBuy Backend is a modular Spring Boot service that powers the GoodBuy iOS app. It handles barcode-based product lookup, ingredient normalization, ingredient and product scoring, magic-link auth, user history and favorites, and manual product reporting when a scan is missing, weak, or incomplete.

## What It Does

- Serves fast product scan responses through a DB-first lookup path with async ingestion on cache miss
- Integrates with external catalog providers such as Open Facts, UPCitemdb, EAN-Search, and EAN-DB to bootstrap product metadata
- Persists normalized products, ingredients, aliases, evidence signals, and user-facing snapshots in PostgreSQL
- Creates immediate first-pass ingredient reads so scans can return populated ingredient lists and provisional scores before deeper enrichment finishes
- Scores ingredients and products using a rule-based safety engine instead of optimistic default grading
- Accepts simple product reports for unsupported, missing, or weak scans and sends them to the team for manual review

## Highlights

- **Async scan ingestion:** first-time scans return quickly while snapshot persistence, matching, and scoring continue in the background
- **Immediate ingredient reads:** fresh scans can create provisional ingredient records and first-pass product scoring on the initial response
- **Manual report intake:** weak or missing scans can be reported straight to the team with the UPC and any known product context
- **Current product focus:** vitamins and supplements are the main supported barcode flow, while other categories can still be reported for manual review
- **Evidence queue:** reported products are stored, deduped by UPC + reason, and forwarded to Slack for manual review
- **Deterministic scoring:** product grades are driven by ingredient evidence and curated rules, not a “safe until proven unsafe” default

## Architecture

```text
iOS App
  -> GET /v1/products/{gtin}
  -> POST /api/v1/history/scan
  -> GET /api/ingredients/{key}
  -> POST /api/v1/products/evidence

goodbuy-api (Spring Boot)
  -> ProductController / ProductService
  -> IngredientController / IngredientReadService
  -> HistoryController / FavoriteController
  -> MagicLinkAuthController / UserRegistrationController
  -> ProductEvidenceReportController
  -> SessionTokenAuthFilter / admin APIs
  -> AsyncProductIngestionService + AsyncIngredientResearchWorker

goodbuy-core (domain + ports)
  -> DTOs, scoring engines, ports, parsers

Adapters
  -> goodbuy-adapters-catalog: Open Facts / UPCitemdb / EAN-Search / EAN-DB
  -> goodbuy-adapters-core: JPA, Flyway, storage, notifications, lookup, snapshot persistence
  -> goodbuy-adapters-enrichment: PubChem enrichment integrations

PostgreSQL
  -> products, ingredients, aliases, product_ingredients
  -> ingredient_signals, citations, ingredient_citations
  -> scan_history, favorite_product
  -> ingredient_missing_report, product_evidence_report

Amazon S3
  -> mirrored product imagery
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
3. The API can prime ingredient records on demand and attach provisional ingredient reads and a first-pass product score immediately
4. Background ingestion persists the snapshot, creates ingredient links, enriches where needed, and scores
5. Later reads resolve from GoodBuy DB

### Missing, weak, or unsupported product

1. The app posts `POST /api/v1/products/evidence` with the UPC, reason, and any known product metadata
2. The backend stores or updates a report row keyed by UPC + reason
3. The backend sends a Slack notification so the team can manually research the product
4. Once the product is fixed in the catalog, later scans resolve through the normal product path

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
│     ├─ config/
│     ├─ root/
│     ├─ auth/
│     ├─ favorites/
│     ├─ history/
│     ├─ ingredients/
│     ├─ products/
│     ├─ users/
│     └─ api/
│        ├─ admin/
│        ├─ auth/
│        └─ uploads/
├─ goodbuy-core/
│  └─ src/main/java/app/goodbuy/core/
│     ├─ auth/
│     ├─ ingredients/
│     ├─ products/
│     └─ storage/
├─ goodbuy-adapters-catalog/
│  └─ src/main/java/app/goodbuy/adapters/catalog/
│     ├─ eandb/
│     └─ eansearch/
├─ goodbuy-adapters-core/
│  └─ src/main/java/app/goodbuy/adapters/core/
│     ├─ citations/
│     ├─ favorites/
│     ├─ history/
│     ├─ ingredients/
│     ├─ notifications/
│     ├─ ocr/
│     ├─ products/
│     ├─ sessions/
│     ├─ sources/
│     ├─ storage/
│     └─ users/
└─ goodbuy-adapters-enrichment/
   └─ src/main/java/app/goodbuy/enrichment/
```

## Key Backend Capabilities

### Product APIs

- `GET /v1/products/{code}`: DB-first product read with async ingestion fallback
- `GET /v1/products/{code}/detail`: product detail variant
- `GET /v1/product-domains`: configured product domains

### Ingredient APIs

- `GET /api/ingredients/{nameOrKey}`: canonical ingredient details
- `GET /api/ingredients?q=...`: strict ingredient search
- `POST /api/ingredients/_batch`: batch ingredient lookup

### User APIs

- `POST /api/v1/auth/magic-link/request`
- `POST /api/v1/auth/magic-link/consume`
- `POST /api/v1/users/register`
- `GET /api/v1/users/me`
- `PUT /api/v1/users/me/email`
- `GET /api/v1/history`
- `DELETE /api/v1/history/{historyId}`
- `POST /api/v1/history/scan`
- `POST /api/v1/history/delete`
- `GET /api/v1/favorites`
- `GET /api/v1/favorites/exists`
- `POST /api/v1/favorites`
- `DELETE /api/v1/favorites`

### Evidence And Recovery APIs

- `POST /api/v1/products/evidence`: report a missing, weak, or unsupported product for manual review
- `POST /api/v1/ingredients/missing`: report unmatched ingredient strings

### Admin APIs

- `POST /api/v1/admin/products/recalc-scores`
- `POST /api/v1/admin/ingredients/recalc-scores`
- `GET /api/v1/admin/ingredients/missing`
- `POST /api/v1/admin/ingredients/missing/resolve`

## Data Model

Core catalog tables:

- `products`
- `ingredients`
- `ingredient_alias`
- `product_ingredients`
- `ingredient_signals`
- `sources`
- `citations`
- `ingredient_citations`

User snapshot tables:

- `scan_history`
- `favorite_product`

Review and recovery tables:

- `ingredient_missing_report`
- `product_evidence_report`

`product_evidence_report` stores the UPC, reason, latest app metadata, and notes associated with a manual review request.

Additional schema notes live in [docs/schema-notes.md](/Users/pms/Documents/Projects/goodbuy-backend/docs/schema-notes.md).

## Scoring Model

The backend uses a rule-based safety model for both ingredient and product scoring.

- High-signal ingredients and classes are handled through curated rules
- Unknown evidence does not automatically become a green score
- First-response scores may be provisional when the backend has just created ingredient reads from a fresh scan
- Product scoring is not a naive average; higher-risk ingredients cap the product more aggressively
- Low-confidence products stay unrated instead of receiving false-positive safety grades
- AI-built drafts are allowed to be useful early, then sharpen over time as stronger evidence and enrichment arrive

## Local Development

### Requirements

- Java 17
- Maven
- Docker / Docker Compose
- AWS credentials for any enabled S3 / SES integrations

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

### Integration config notes

The local stack can use:

- `GOODBUY_SES_ACCESS_KEY`
- `GOODBUY_SES_SECRET_KEY`
- `OPENAI_API_KEY`

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.x |
| Database | PostgreSQL 16 |
| Persistence | Spring Data JPA |
| Migrations | Flyway |
| External Catalogs | EAN-DB, EAN-Search |
| AI Product Analysis | OpenAI |
| Ingredient Enrichment | OpenAI, PubChem |
| Storage | Amazon S3 |
| Docs | OpenAPI / Swagger |
| Build | Maven |
| Local Runtime | Docker Compose |
| Architecture | Modular hexagonal / ports-and-adapters |

## Why This Project Is Interesting

This backend is not just a CRUD API. It combines:

- real-world catalog ingestion from imperfect third-party data
- normalization and alias matching over messy ingredient strings
- immediate provisional ingredient authoring for first-scan usability
- manual review for missing or weak barcode results
- async workflows for latency-sensitive mobile scanning
- rule-based risk scoring
- explicit evidence and retry/review loops for bad data

That makes it a good example of pragmatic backend engineering around unreliable inputs, user-facing performance, and data quality feedback loops.

## License

© 2026 GoodBuy. All rights reserved.
