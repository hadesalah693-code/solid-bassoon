-- Lock in tenant isolation: make company_id NOT NULL on every tenant-scoped
-- table and add FK constraints pointing to companies(id). Add indexes for the
-- row-level tenant filter (every tenant-scoped query adds WHERE company_id = ?).

-- persons
ALTER TABLE persons ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE persons
    ADD CONSTRAINT fk_persons_company
    FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_persons_company ON persons(company_id);

-- cases
ALTER TABLE cases ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE cases
    ADD CONSTRAINT fk_cases_company
    FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_cases_company ON cases(company_id);

-- hearings
ALTER TABLE hearings ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE hearings
    ADD CONSTRAINT fk_hearings_company
    FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_hearings_company ON hearings(company_id);

-- tasks
ALTER TABLE tasks ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_company
    FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_tasks_company ON tasks(company_id);

-- notifications
ALTER TABLE notifications ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_company
    FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_notifications_company ON notifications(company_id);

-- messages
ALTER TABLE messages ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE messages
    ADD CONSTRAINT fk_messages_company
    FOREIGN KEY (company_id) REFERENCES companies(id);
CREATE INDEX idx_messages_company ON messages(company_id);
