ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS ocr_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED';

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS ocr_provider VARCHAR(64);

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS ocr_raw_text TEXT;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS parsed_ingredient_text TEXT;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS last_reprocessed_at TIMESTAMPTZ;
