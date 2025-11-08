# GoodBuy Backend Architecture

This backend is structured using a **modular / hexagonal (ports & adapters)** design.

Goals:

- Keep domain models and contracts stable.
- Isolate frameworks (Spring), persistence (Postgres), and external APIs (EAN-DB, EAN-Search).
- Make it easy to swap providers or add new clients (iOS/web) without rewriting core logic.

---

## Module Overview

### 1. `goodbuy-core`

**Pure Java. No Spring. No HTTP. No DB.**

Contains:

- **DTOs (API contracts)**  
  - `ingredients/dto/IngredientDTO`
  - `products/dto/ProductDetailDto`
- **Ports (interfaces)**  
  - `ingredients/port/IngredientReadPort`
- **Utilities**
  - `products/util/BarcodeNormalizer` (if used here)

**Purpose**

Defines the **domain-facing model** and the interfaces the rest of the world must implement.  
Everything else depends *on this*, but `goodbuy-core` depends on nothing.

Think of it as: _"What is a Product? What is an Ingredient? What do I need to read them?"_

---

### 2. `goodbuy-adapters-catalog`

**Spring components that talk to external product catalog APIs.**

Contains:

- `CatalogProperties`
- `CatalogConfig`
- `CatalogTransportException`
- `ExternalCatalogClient` (interface used by services)
- `eandb/EanDbCatalogClient`
- `eansearch/EanSearchClient`

**What it does**

- Reads configuration to decide which external provider to use.
- Implements `ExternalCatalogClient` for each provider.
- Handles:
  - HTTP calls
  - auth (JWT / API key)
  - timeouts, retries, error mapping
  - JSON → `ProductDetailDto` mapping

**Key idea**

All provider-specific logic lives here.  
The app never directly calls `EAN-DB` or `EAN-Search`; it calls `ExternalCatalogClient`.

---

### 3. `goodbuy-api`

**The actual Spring Boot application.**  
Owns HTTP endpoints, DB access, and wiring of adapters/core.

Contains:

- `GoodBuyBackendApplication` (entrypoint)

#### Cross-Cutting (`app.goodbuy.api`)

- `RequestLoggingFilter`  
  Logs each request: method, path, status, duration, requestId.
- `GlobalExceptionHandler` + `ErrorResponse`  
  Normalizes errors into consistent JSON.
- (Optional) Logging config, etc.

#### Config (`app.goodbuy.config`)

- `CorsConfig` — CORS setup.
- `OpenApiConfig` — OpenAPI/Swagger metadata.

#### Ingredients (`app.goodbuy.ingredients`)

- `model/Ingredient`, `model/IngredientAlias`  
  JPA entities (Postgres).
- `IngredientRepository`  
  Spring Data repository.
- `IngredientMapper`  
  Maps JPA entities → `core`’s `IngredientDTO`.
- `CoreIngredientReadAdapter`  
  Implements `IngredientReadPort` using `IngredientRepository` + `IngredientMapper`.
- `IngredientReadService`  
  Orchestrates ingredient lookup using the port.
- `IngredientController`
  - HTTP endpoint (e.g. `GET /api/ingredients/{slug}`).
  - Calls `IngredientReadService`, returns `IngredientDTO`.

**Boundary**

- Controller → Service → `IngredientReadPort` → `CoreIngredientReadAdapter` → DB.
- API code depends on the **port** and **DTO**, not on JPA directly.

#### Products (`app.goodbuy.products`)

- `ProductService`
  - Injected with `Optional<ExternalCatalogClient>` (from `goodbuy-adapters-catalog`).
  - `activeSourceName()`: tells you which provider is currently used (`EAN-DB`, etc.).
  - `getByGtinOrNull(gtin14)`:
    - Uses `ExternalCatalogClient` to fetch product.
    - Returns a `ProductDetailDto` or `null`.
  - `getDetailByGtinOrNull(gtin14)`:
    - Preferred rich lookup:
      - Uses `EanDbCatalogClient.findDetailByGtin(...)` when available.
      - Falls back to simple lookup + wraps into `ProductDetailDto`.

- `ProductController` (`/v1/products`)
  - **`GET /v1/products/{code}`**
    - Normalizes barcode → GTIN-14.
    - Calls `ProductService.getByGtinOrNull`.
    - Wraps result into a `ProductView`:
      - `gtin, name, brand, category`
      - images (from `ProductDetailDto.ImageDto`)
      - ingredients (from `ProductDetailDto.IngredientDto`, flattened or mapped)
      - `source`
    - Intended as the **stable “simple” endpoint** (used by current iOS).
  - **`GET /v1/products/{code}/detail`**
    - Same normalization.
    - Calls `ProductService.getDetailByGtinOrNull`.
    - Returns full `ProductDetailDto`.
    - Intended for **richer future clients**.

**Why two endpoints?**

- `/v1/products/{code}`:
  - Backwards-compatible, simple view.
  - Safe for existing client code.
- `/v1/products/{code}/detail`:
  - Full structured payload for future / advanced UIs.
  - Lets you evolve without breaking old apps.

---

## End-to-End Request Flows

### 1. Ingredient Detail

1. Client calls `GET /api/ingredients/{slug}`.
2. `IngredientController`:
   - Validates slug.
   - Calls `IngredientReadService`.
3. `IngredientReadService`:
   - Uses `IngredientReadPort` (implemented by `CoreIngredientReadAdapter`).
4. `CoreIngredientReadAdapter`:
   - Uses `IngredientRepository` to load entities.
   - Uses `IngredientMapper` → `IngredientDTO` (from `goodbuy-core`).
5. Controller returns `IngredientDTO` JSON.

**Key point**: Controller never touches JPA types. It speaks DTOs.

---

### 2. Product Lookup via External Catalog

1. Client (iOS) calls `GET /v1/products/{code}`.
2. `ProductController`:
   - Logs the call.
   - Normalizes barcode → GTIN-14.
   - Calls `ProductService.getByGtinOrNull(gtin14)`.
3. `ProductService`:
   - Asks `ExternalCatalogClient` (from `goodbuy-adapters-catalog`).
4. `CatalogConfig`:
   - Based on properties, wires `EanDbCatalogClient` (or another) as the `ExternalCatalogClient`.
5. `EanDbCatalogClient`:
   - Calls EAN-DB HTTP API.
   - Parses JSON → `ProductDetailDto`.
6. `ProductService`:
   - Logs hit/miss.
   - Returns DTO.
7. `ProductController`:
   - Wraps into `ProductView`.
   - Adds `X-Product-Source` header.
   - Returns JSON.

Clients see a **clean, stable** shape; all the HTTP/provider mess lives in the adapter module.

---

## How to Pitch This (TL;DR for Recruiters / README)

> The backend is organized using a hexagonal architecture:
>
> - `goodbuy-core` defines stable domain models and ports (no framework deps).
> - `goodbuy-adapters-catalog` implements infrastructure: external catalog HTTP clients, config, error handling.
> - `goodbuy-api` is the Spring Boot app: it exposes REST endpoints, orchestrates use cases via services, and delegates to ports/adapters.
>
> Controllers are intentionally thin: validate input, call a service, map to response.
> Services depend on interfaces (`ExternalCatalogClient`, `IngredientReadPort`) instead of concrete implementations.
> This makes it easy to:
> - swap catalog providers,
> - change persistence,
> - add richer endpoints (like `/v1/products/{code}/detail`) without breaking old clients.

If you’d like, next step I can generate a short “Architecture” section tailored to your actual README style.