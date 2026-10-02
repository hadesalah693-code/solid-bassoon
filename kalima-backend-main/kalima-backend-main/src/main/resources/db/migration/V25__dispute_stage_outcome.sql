-- Stage closure outcome (see UpdateDisputeStageRequest when status becomes CLOSED).
ALTER TABLE dispute_stages
    ADD COLUMN outcome VARCHAR(40) NULL,
    ADD COLUMN outcome_note TEXT NULL;
