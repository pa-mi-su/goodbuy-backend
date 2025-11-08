Module: goodbuy-api (Spring Boot app)

app.goodbuy.GoodBuyBackendApplication
	•	What it is: Main Spring Boot entrypoint.
	•	What it does:
	•	Boots the application.
	•	Triggers component scanning so controllers, services, adapters, configs all get wired.
	•	How you’d explain: “Standard Spring Boot launcher; no business logic.”

⸻

Package: app.goodbuy.api (Cross-cutting API stuff)

RequestLoggingFilter
	•	Type: OncePerRequestFilter.
	•	What it does:
	•	Logs every HTTP request with:
	•	method, path, status, duration, request ID, etc.
	•	Makes debugging & tracing much easier.
	•	Why: Observability. When something breaks, you see exactly what call did what.

GlobalExceptionHandler
	•	Type: @RestControllerAdvice.
	•	What it does:
	•	Catches unhandled exceptions from controllers/services.
	•	Normalizes them into JSON error responses (e.g. {status, error, message}).
	•	Why: Keeps controllers clean; consistent error shape for clients.

ErrorResponse
	•	What it does:
	•	Simple DTO used by the exception handler.
	•	Represents structured error payloads.

⸻

Package: app.goodbuy.config (App configuration)

CorsConfig
	•	What it does:
	•	Configures CORS (which domains can talk to your API).
	•	Why:
	•	So the iOS app / web clients can call the API from allowed origins without pain.

OpenApiConfig
	•	What it does:
	•	Configures OpenAPI/Swagger metadata for the API.
	•	Why:
	•	Generates docs / schema; helpful for clients and self-documentation.

⸻

Ingredients Flow

app.goodbuy.ingredients.model.Ingredient
	•	Type: JPA entity.
	•	What it does:
	•	Maps to the ingredients table in Postgres.
	•	Fields like id, canonical key, display name, description, tags, aliases, etc.
	•	Why:
	•	The persistent “source of truth” in your own database.

app.goodbuy.ingredients.model.IngredientAlias
	•	Type: JPA entity.
	•	What it does:
	•	Stores alternate names / synonyms for ingredients.
	•	Why:
	•	Lets you search/resolve ingredients from multiple names.

IngredientRepository
	•	Type: JpaRepository.
	•	What it does:
	•	DB access for Ingredient entities; e.g. findByCanonicalKey(...).
	•	Why:
	•	Thin DB abstraction; used by adapters/services.

app.goodbuy.core.ingredients.dto.IngredientDTO
	•	Lives in: goodbuy-core.
	•	What it does:
	•	API-facing DTO representing an ingredient:
	•	id, canonicalKey, displayName, summary, description, tags, aliases, etc.
	•	Has a static of(Ingredient) mapper to convert JPA entity → DTO.
	•	Why:
	•	Stable contract between backend and clients.
	•	Reusable in multiple modules.

IngredientMapper
	•	Type: Spring @Component.
	•	What it does:
	•	Wraps the mapping logic:
	•	toDto(Ingredient) → IngredientDTO
	•	toDtoList(Collection<Ingredient>)
	•	Why:
	•	Keeps mapping in one place; controller/service doesn’t repeat it.

app.goodbuy.core.ingredients.port.IngredientReadPort
	•	Lives in: goodbuy-core.
	•	What it does:
	•	An interface:
	•	“Given an identifier/slug, load an IngredientDTO.”
	•	Why:
	•	Abstraction: API code depends on this port, not on JPA.

CoreIngredientReadAdapter
	•	Type: Spring @Component.
	•	Implements: IngredientReadPort.
	•	What it does:
	•	Uses IngredientRepository + IngredientMapper under the hood.
	•	Resolves ingredient from DB and returns IngredientDTO.
	•	Why:
	•	This is your hexagonal adapter.
	•	Swappable: if you later fetch from another service, you change this, not the controller.

IngredientReadService
	•	Type: Spring @Service.
	•	What it does:
	•	Calls IngredientReadPort.
	•	Maybe adds small orchestration/validation logic.
	•	Why:
	•	Thin use-case layer between controller and adapter.

IngredientController
	•	Type: @RestController.
	•	Endpoints (typical pattern):
	•	GET /api/ingredients/{slug}:
	•	Uses IngredientReadService.
	•	Returns IngredientDTO as JSON.
	•	Why:
	•	Public HTTP surface for ingredient data.
	•	Uses DTOs from core and the port/adapter stack cleanly.

⸻

Products Flow

app.goodbuy.core.products.dto.ProductDetailDto
	•	Lives in: goodbuy-core.
	•	What it is:
	•	Rich product DTO:
	•	gtin, name, brand, category, description
	•	List<ImageDto> (url, width, height)
	•	List<IngredientDto> (id, original, canonical, externalIds, vegan/vegetarian)
	•	titles, manufacturer, source
	•	Why:
	•	One canonical model for product details.
	•	Consumers (API, adapters, other services) all share this.

ProductService
	•	Type: Spring @Service.
	•	Depends on: Optional<ExternalCatalogClient>.
	•	What it does:
	•	activeSourceName()
	•	Returns “EAN-DB”, “EAN-Search”, or “internal” for logging/metadata.
	•	getByGtinOrNull(gtin14)
	•	Normal “simple” lookup path.
	•	Uses ExternalCatalogClient to fetch product and return a ProductDetailDto (or null).
	•	getDetailByGtinOrNull(gtin14)
	•	Uses richer EanDbCatalogClient.findDetailByGtin(...) when available.
	•	Falls back to simple lookup + wraps into ProductDetailDto if only a basic client exists.
	•	Why:
	•	Orchestrates product lookup.
	•	Shields controllers from provider specifics and exception handling.

ProductController
	•	Type: @RestController.
	•	Base path: /v1/products
	•	Endpoints:
	1.	GET /v1/products/{code}
	•	Normalizes barcode → GTIN-14.
	•	Calls ProductService.getByGtinOrNull.
	•	Wraps into ProductView:
	•	gtin, name, brand, category
	•	images (from ProductDetailDto.ImageDto)
	•	ingredients (from ProductDetailDto.IngredientDto, depending how you present)
	•	source
	•	Returns 404 with structured error body if not found.
	•	For: existing iOS client (simple but now enriched).
	2.	GET /v1/products/{code}/detail
	•	Same normalization.
	•	Calls ProductService.getDetailByGtinOrNull.
	•	Returns full ProductDetailDto JSON.
	•	For: future clients needing richer structured data.
	•	Why:
	•	Clean separation:
	•	one stable “compat/simple” endpoint,
	•	one “rich/forward-looking” endpoint.

⸻

Module: goodbuy-adapters-catalog (External Providers)

CatalogProperties
	•	Type: @ConfigurationProperties.
	•	What it does:
	•	Holds config:
	•	provider name
	•	base URLs
	•	timeouts
	•	JWT/API keys (wired via secrets / env).
	•	Why:
	•	Central place to configure which external catalog is active and how to talk to it.

CatalogConfig
	•	Type: @Configuration.
	•	What it does:
	•	Reads CatalogProperties.
	•	Creates beans:
	•	EanDbCatalogClient
	•	EanSearchClient
	•	ExternalCatalogClient (the active one, based on config).
	•	Why:
	•	This is where you “plug in” the chosen provider.

ExternalCatalogClient
	•	Type: interface.
	•	What it does:
	•	Defines:
	•	Optional<ProductDetailDto> findDetailByGtin(String gtin14) (or simple findByGtin depending on version).
	•	Why:
	•	API/services depend on this interface, not on a concrete HTTP implementation.

EanDbCatalogClient
	•	Type: concrete implementation.
	•	What it does:
	•	Calls https://ean-db.com/api/v2/product/...
	•	Handles:
	•	URL building, auth header, timeouts.
	•	Parsing JSON into:
	•	basic info,
	•	images,
	•	ingredient metadata,
	•	manufacturer,
	•	localized titles.
	•	Maps to ProductDetailDto.
	•	Handles error codes, throttling, logging.
	•	Why:
	•	Isolates all EAN-DB quirks from business code.

EanSearchClient
	•	Type: alternative ExternalCatalogClient.
	•	What it does:
	•	Similar, but for api.ean-search.org.
	•	Returns simple mapped DTO.
	•	Why:
	•	Pluggable provider; same pattern.

CatalogTransportException
	•	What it does:
	•	Wraps network / HTTP / parsing errors from external catalog calls.
	•	Why:
	•	ProductService can handle catalog issues cleanly without exposing internals.
