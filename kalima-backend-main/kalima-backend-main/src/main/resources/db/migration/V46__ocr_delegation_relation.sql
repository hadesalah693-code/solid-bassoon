-- Phase 3: allow OCR records to link to delegations (intake audit trail).

ALTER TABLE ocr_relations DROP CONSTRAINT IF EXISTS chk_ocr_relations_type;
ALTER TABLE ocr_relations ADD CONSTRAINT chk_ocr_relations_type CHECK (
    relation_type IN ('CASE', 'DISPUTE', 'CLIENT', 'TASK', 'DOCUMENT', 'DELEGATION')
);
