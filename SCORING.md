# GoodBuy Ingredient & Product Scoring Spec (v1)

## 1. Purpose & Scope

This document defines how GoodBuy:

1. Scores **individual ingredients**.
2. Aggregates ingredient scores into a **product-level grade (A–F)**.

Goals:

- Be **transparent, explainable, and consistent**.
- Provide **practical guidance**, not medical/toxicology advice.
- Make it easy to **tune** the system later without changing the whole architecture.

This spec governs:

- DB fields: `ingredients.safety_score`, `ingredients.rating_letter`, `ingredients.references_count`, `ingredients.concerns`, `ingredients.category`, `ingredients.regulation_notes`, etc.
- DTO fields: `IngredientDTO.safetyScore`, `safetyGrade`, `riskLevel`, `dataConfidence`, etc.
- Product-level scoring logic (A–F).

---

## 2. Definitions

- **Hazard statement**: Text describing a risk, usually derived from GHS or other official classifications (e.g. “Causes severe skin burns and eye damage”).
- **Ingredient Health Score**: Score 0–100 based on human health hazards.
- **Ingredient Environmental Score**: Score 0–100 based on environmental hazards.
- **Ingredient Data Confidence Score**: Score 0–100 based on how many credible references we have.
- **Ingredient Overall Safety Score**: Weighted combination of health, environment, and data confidence (0–100).
- **Ingredient Grade**: Letter `A/B/C/D/F` derived from overall safety score.
- **Product Score**: Score 0–100 for a specific product, derived from its ingredients’ grades.
- **Product Grade**: Letter `A/B/C/D/F` derived from product score.

---

## 3. Ingredient Scoring

### 3.1 Inputs per ingredient

Each ingredient may have:

- **Hazard statements** (from PubChem, ECHA, etc.).
- **Environmental hazard statements** (e.g. “very toxic to aquatic life”).
- **Number of references** (e.g. `ingredients.references_count`).
- **Category** (e.g. “Bleach / disinfectant”).
- **Concerns text** (`ingredients.concerns`) used for DTO explanation.

### 3.2 Health Score (0–100)

Start from 100 and subtract penalties based on **health-related** hazard statements.

Hazard text is normalized to lowercase and checked for key phrases.

#### Health penalties

| Condition (hazard text contains…)                             | Health penalty |
|--------------------------------------------------------------|----------------|
| "fatal" OR "carcinogenic" OR "cancer" OR "mutagen"           | -50            |
| "severe skin burns" OR "serious eye damage"                  | -35            |
| "respiratory irritation" OR "respiratory sensitization"      | -20            |
| "skin irritation" OR "eye irritation"                        | -10            |
| Any other generic *irritation* term                          | -5             |

**Algorithm:**

```
healthScore = 100

for each hazard in healthHazards:
  h = hazard.toLowerCase()
  if h contains any of ["fatal", "carcinogenic", "cancer", "mutagen"]:
    healthScore -= 50
  if h contains "severe skin burns" or "serious eye damage":
    healthScore -= 35
  if h contains "respiratory irritation" or "respiratory sensitization":
    healthScore -= 20
  if h contains "skin irritation" or "eye irritation":
    healthScore -= 10
  else if h contains "irritation":
    healthScore -= 5

healthScore = clamp(healthScore, 0, 100)
```

If **no health hazards** are available:

```
healthScore = 80
```

### 3.3 Environmental Score (0–100)

Start from 100 and subtract penalties based on **environmental** hazard statements.

#### Environmental penalties

| Condition (hazard text contains…)        | Env penalty |
|------------------------------------------|-------------|
| "very toxic to aquatic life"             | -40         |
| "toxic to aquatic life"                  | -20         |
| "harmful to aquatic life"                | -10         |
| "persistent, bioaccumulative"            | -30         |

**Algorithm:**

```
envScore = 100

for each hazard in envHazards:
  h = hazard.toLowerCase()
  if h contains "very toxic to aquatic life":
    envScore -= 40
  else if h contains "toxic to aquatic life":
    envScore -= 20
  else if h contains "harmful to aquatic life":
    envScore -= 10
  if h contains "persistent" and "bioaccumulative":
    envScore -= 30

envScore = clamp(envScore, 0, 100)
```

If **no environmental hazards** are available:

```
envScore = 80
```

### 3.4 Data Confidence Score (0–100)

From `references_count`:

| refs                | Data confidence |
|---------------------|-----------------|
| 0 or null           | 30              |
| 1–2                 | 50              |
| 3–7                 | 75              |
| 8+                  | 90              |

### 3.5 Overall Ingredient Safety Score

```
overallSafetyScore = round(
    0.7 * healthScore
  + 0.2 * envScore
  + 0.1 * dataConfidenceScore
)
```

### 3.6 Ingredient Grade Bands

| Score | Grade |
|-------|-------|
| 85–100 | A |
| 70–84  | B |
| 55–69  | C |
| 40–54  | D |
| 0–39   | F |

### 3.7 Risk Level

| Score | riskLevel |
|--------|-----------|
| 80–100 | low |
| 50–79  | moderate |
| 0–49   | high |

---

## 4. Product Scoring

### 4.1 Base

```
productScore = 100
```

### 4.2 Ingredient Penalties

| Ingredient grade | Penalty |
|------------------|---------|
| A                | 0       |
| B                | -2      |
| C                | -6      |
| D                | -15     |
| F                | -30     |

### 4.3 Hard Caps

- If **any F** ingredient → product grade **max = D**
- If **2+ D** ingredients → product grade **max = C**

### 4.4 Product Grade Bands

| Score | Grade |
|--------|--------|
| 85–100 | A |
| 70–84  | B |
| 55–69  | C |
| 40–54  | D |
| 0–39   | F |

---

## 5. Transparency Notes

- This is **guidance**, not medical advice.
- Scoring is based on hazard classifications and conservative assumptions.
- Ingredient scores justify product scores.

---
