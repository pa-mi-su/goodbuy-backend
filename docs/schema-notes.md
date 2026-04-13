# Schema Notes

## Source Of Truth

- `products`
  - one row per GTIN/EAN product known to GoodBuy
  - owns normalized product metadata, domain, image URLs, product-level score, and `raw_ingredient_text`
- `ingredients`
  - one row per canonical ingredient identity
  - owns display content and ingredient-level score
- `ingredient_alias`
  - synonym/variant matching table for canonical ingredient identities
- `product_ingredients`
  - join table from product to ingredient plus the product-specific display label
- `ingredient_signals`
  - structured hazard/regulatory evidence used by scoring

## Review And Recovery Flows

- `ingredient_missing_report`
  - unmatched ingredient queue
  - one row per `(ingredient_name, product_ean)`
- `product_evidence_report`
  - product-level review queue for missing product / unclear ingredients / out-of-domain
  - stores lightweight metadata and notes for manual review
  - one row per `(ean, reason)`

## User Snapshot Tables

- `scan_history`
  - lightweight per-user scan history snapshot
  - current source-of-truth timestamp is `scanned_at`
- `favorite_product`
  - lightweight per-user saved-product snapshot for faster UI reads

## Design Rules

- canonical domain codes are lowercase text everywhere:
  - `vitamins`, `cleaning`, `baby`, `food`, `other`, `unknown`
  - the current client rollout is vitamins-first even though the schema still supports broader domains
- `products` and `ingredients` are the durable catalog entities
- history/favorites are UI convenience snapshots, not product catalog truth
- unmatched ingredients should be routed into review queues, not silently discarded
- missing evidence should result in `NR`/review states rather than optimistic scores
