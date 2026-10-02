-- Optional court metadata on the case file (separate from per-hearing court/chamber).
ALTER TABLE cases
    ADD COLUMN court_name VARCHAR(200);

ALTER TABLE cases
    ADD COLUMN court_circuit VARCHAR(100);

ALTER TABLE cases
    ADD COLUMN procedure_stage VARCHAR(100);
