ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUESTED';

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_provider VARCHAR(64);

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_confidence INTEGER;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_domain VARCHAR(64);

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_category VARCHAR(255);

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_product_name VARCHAR(255);

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_brand VARCHAR(255);

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_summary TEXT;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS analysis_raw_payload TEXT;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS parsed_ingredient_count INTEGER;

ALTER TABLE product_evidence_report
    ADD COLUMN IF NOT EXISTS draft_created_at TIMESTAMP WITH TIME ZONE;
