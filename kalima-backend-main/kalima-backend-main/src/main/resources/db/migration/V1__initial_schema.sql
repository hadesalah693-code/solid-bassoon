CREATE TABLE clients (
    id UUID PRIMARY KEY,
    client_type VARCHAR(20) NOT NULL CHECK (client_type IN ('INDIVIDUAL', 'COMPANY')),
    full_name VARCHAR(150),
    national_id VARCHAR(50),
    company_name VARCHAR(200),
    commercial_register VARCHAR(100),
    phone VARCHAR(30) NOT NULL,
    email VARCHAR(150),
    address TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_clients_name_presence CHECK (
        (client_type = 'INDIVIDUAL' AND full_name IS NOT NULL)
        OR (client_type = 'COMPANY' AND company_name IS NOT NULL)
    )
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(30),
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN', 'LAWYER', 'SENIOR_LAWYER', 'SECRETARY', 'ACCOUNTANT', 'CLIENT')),
    client_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_users_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT chk_users_client_role_mapping CHECK (
        (role = 'CLIENT' AND client_id IS NOT NULL)
        OR (role <> 'CLIENT' AND client_id IS NULL)
    )
);

CREATE TABLE cases (
    id UUID PRIMARY KEY,
    case_number VARCHAR(100) NOT NULL UNIQUE,
    court_case_number VARCHAR(100),
    title VARCHAR(250) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL CHECK (status IN ('NEW', 'IN_PROGRESS', 'ON_HOLD', 'CLOSED', 'ARCHIVED')),
    case_type VARCHAR(100),
    start_date DATE,
    close_date DATE,
    client_id UUID NOT NULL,
    assigned_lawyer_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_cases_client FOREIGN KEY (client_id) REFERENCES clients(id),
    CONSTRAINT fk_cases_assigned_lawyer FOREIGN KEY (assigned_lawyer_id) REFERENCES users(id),
    CONSTRAINT fk_cases_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE hearings (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    hearing_date TIMESTAMP NOT NULL,
    court_name VARCHAR(200) NOT NULL,
    chamber VARCHAR(100),
    result TEXT,
    notes TEXT,
    next_hearing_date TIMESTAMP,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_hearings_case FOREIGN KEY (case_id) REFERENCES cases(id),
    CONSTRAINT fk_hearings_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    priority VARCHAR(20) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    due_date TIMESTAMP,
    assigned_to UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tasks_case FOREIGN KEY (case_id) REFERENCES cases(id),
    CONSTRAINT fk_tasks_assigned_to FOREIGN KEY (assigned_to) REFERENCES users(id),
    CONSTRAINT fk_tasks_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    case_id UUID,
    type VARCHAR(50) NOT NULL CHECK (type IN ('HEARING_ADDED', 'HEARING_UPDATED', 'CASE_STATUS_CHANGED', 'NEW_MESSAGE')),
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_recipient_user FOREIGN KEY (recipient_user_id) REFERENCES users(id),
    CONSTRAINT fk_notifications_case FOREIGN KEY (case_id) REFERENCES cases(id)
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    sender_user_id UUID NOT NULL,
    receiver_user_id UUID NOT NULL,
    content TEXT NOT NULL,
    attachment_url TEXT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_messages_case FOREIGN KEY (case_id) REFERENCES cases(id),
    CONSTRAINT fk_messages_sender_user FOREIGN KEY (sender_user_id) REFERENCES users(id),
    CONSTRAINT fk_messages_receiver_user FOREIGN KEY (receiver_user_id) REFERENCES users(id),
    CONSTRAINT chk_messages_sender_receiver_diff CHECK (sender_user_id <> receiver_user_id)
);

CREATE INDEX idx_cases_client_id ON cases(client_id);
CREATE INDEX idx_cases_status ON cases(status);
CREATE INDEX idx_hearings_case_id_hearing_date ON hearings(case_id, hearing_date);
CREATE INDEX idx_tasks_case_id_status ON tasks(case_id, status);
CREATE INDEX idx_tasks_assigned_to_status ON tasks(assigned_to, status);
CREATE INDEX idx_notifications_recipient_read_created ON notifications(recipient_user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_case_id ON notifications(case_id);
CREATE INDEX idx_messages_case_created ON messages(case_id, created_at DESC);
CREATE INDEX idx_messages_receiver_read ON messages(receiver_user_id, is_read);
