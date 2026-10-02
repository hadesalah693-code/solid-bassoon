CREATE TABLE persons (
    id UUID PRIMARY KEY,

    person_type VARCHAR(20) NOT NULL CHECK (person_type IN ('INDIVIDUAL', 'COMPANY')),
    system_role VARCHAR(30) NOT NULL CHECK (system_role IN ('ADMIN', 'LAWYER', 'SENIOR_LAWYER', 'SECRETARY', 'ACCOUNTANT', 'CLIENT')),

    full_name VARCHAR(150),
    company_name VARCHAR(200),
    national_id VARCHAR(50),
    commercial_register VARCHAR(100),

    phone VARCHAR(30),
    email VARCHAR(150),
    address TEXT,

    notes TEXT,

    password_hash VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_persons_email UNIQUE (email),

    CONSTRAINT chk_persons_name_by_type CHECK (
        (person_type = 'INDIVIDUAL' AND full_name IS NOT NULL)
        OR (person_type = 'COMPANY' AND company_name IS NOT NULL)
    ),

    CONSTRAINT chk_persons_staff_requirements CHECK (
        system_role = 'CLIENT'
        OR (
            person_type = 'INDIVIDUAL'
            AND email IS NOT NULL
            AND password_hash IS NOT NULL
        )
    )
);

CREATE INDEX idx_persons_system_role ON persons(system_role);
CREATE INDEX idx_persons_phone ON persons(phone);
