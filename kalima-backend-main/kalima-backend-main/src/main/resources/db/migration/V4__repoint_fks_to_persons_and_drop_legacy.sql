-- Repoint all foreign keys that used to reference clients(id) or users(id)
-- to reference the unified persons(id). Then drop the legacy clients and users tables.
-- Column names are kept as-is (client_id, assigned_lawyer_id, created_by,
-- recipient_user_id, sender_user_id, receiver_user_id, assigned_to) because they
-- describe the semantic relationship, not the physical table they point to.

ALTER TABLE cases DROP CONSTRAINT fk_cases_client;
ALTER TABLE cases DROP CONSTRAINT fk_cases_assigned_lawyer;
ALTER TABLE cases DROP CONSTRAINT fk_cases_created_by;

ALTER TABLE hearings DROP CONSTRAINT fk_hearings_created_by;

ALTER TABLE tasks DROP CONSTRAINT fk_tasks_assigned_to;
ALTER TABLE tasks DROP CONSTRAINT fk_tasks_created_by;

ALTER TABLE notifications DROP CONSTRAINT fk_notifications_recipient_user;

ALTER TABLE messages DROP CONSTRAINT fk_messages_sender_user;
ALTER TABLE messages DROP CONSTRAINT fk_messages_receiver_user;

-- users must be dropped before clients because of fk_users_client
DROP TABLE users;
DROP TABLE clients;

-- Recreate foreign keys, now pointing to persons(id)
ALTER TABLE cases
    ADD CONSTRAINT fk_cases_client
    FOREIGN KEY (client_id) REFERENCES persons(id);

ALTER TABLE cases
    ADD CONSTRAINT fk_cases_assigned_lawyer
    FOREIGN KEY (assigned_lawyer_id) REFERENCES persons(id);

ALTER TABLE cases
    ADD CONSTRAINT fk_cases_created_by
    FOREIGN KEY (created_by) REFERENCES persons(id);

ALTER TABLE hearings
    ADD CONSTRAINT fk_hearings_created_by
    FOREIGN KEY (created_by) REFERENCES persons(id);

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_assigned_to
    FOREIGN KEY (assigned_to) REFERENCES persons(id);

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_created_by
    FOREIGN KEY (created_by) REFERENCES persons(id);

ALTER TABLE notifications
    ADD CONSTRAINT fk_notifications_recipient_user
    FOREIGN KEY (recipient_user_id) REFERENCES persons(id);

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_sender_user
    FOREIGN KEY (sender_user_id) REFERENCES persons(id);

ALTER TABLE messages
    ADD CONSTRAINT fk_messages_receiver_user
    FOREIGN KEY (receiver_user_id) REFERENCES persons(id);
