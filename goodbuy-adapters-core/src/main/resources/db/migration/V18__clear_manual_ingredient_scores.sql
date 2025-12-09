BEGIN;

-- We’re saying: from now on, scores belong to the engine only.
UPDATE ingredients
SET safety_score = NULL,
    rating_letter = NULL;

COMMIT;
