-- Administrative task category (Odoo-aligned office workflow types).

ALTER TABLE tasks
    ADD COLUMN admin_task_type VARCHAR(60);

CREATE INDEX idx_tasks_admin_task_type ON tasks (company_id, admin_task_type);
