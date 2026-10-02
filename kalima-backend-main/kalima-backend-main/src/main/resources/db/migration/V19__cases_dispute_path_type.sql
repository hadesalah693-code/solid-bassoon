-- Optional preset for auto-generated dispute path milestones (DisputePathType).
ALTER TABLE cases
    ADD COLUMN dispute_path_type VARCHAR(50);
