-- Align client_retainers.currency with the entity mapping.
-- V70 declared it as CHAR(3) (bpchar) while ClientRetainer maps a String with
-- length = 3, which Hibernate schema validation reports as a type mismatch.
-- bank_transfers.currency was already created as VARCHAR(3), so only this
-- single column needs to change.

ALTER TABLE client_retainers
    ALTER COLUMN currency TYPE VARCHAR(3) USING TRIM(currency);