-- HR module (الموارد البشرية) — departments, employees, fingerprint devices, attendance.
-- Tenant-scoped (company_id) like every other module in this schema.

-- ---------------------------------------------------------------- departments
CREATE TABLE hr_departments (
    id              UUID        PRIMARY KEY,
    company_id      UUID        NOT NULL REFERENCES companies (id),
    name            VARCHAR(150) NOT NULL,
    code            VARCHAR(40),
    parent_id       UUID        REFERENCES hr_departments (id),
    manager_id      UUID        REFERENCES persons (id),
    description     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_departments_company_name UNIQUE (company_id, name)
);

CREATE INDEX idx_hr_departments_company ON hr_departments (company_id);

-- ------------------------------------------------------------------ positions
CREATE TABLE hr_positions (
    id              UUID        PRIMARY KEY,
    company_id      UUID        NOT NULL REFERENCES companies (id),
    title           VARCHAR(150) NOT NULL,
    code            VARCHAR(40),
    department_id   UUID        REFERENCES hr_departments (id),
    description     TEXT,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_positions_company_title UNIQUE (company_id, title)
);

CREATE INDEX idx_hr_positions_company ON hr_positions (company_id);
CREATE INDEX idx_hr_positions_department ON hr_positions (department_id);

-- ------------------------------------------------------------------ employees
CREATE TABLE hr_employees (
    id                  UUID        PRIMARY KEY,
    company_id          UUID        NOT NULL REFERENCES companies (id),
    employee_number     VARCHAR(40) NOT NULL,
    full_name           VARCHAR(250) NOT NULL,
    department_id       UUID        REFERENCES hr_departments (id),
    position_id         UUID        REFERENCES hr_positions (id),
    -- Links the employee to a system login (persons row) when one exists.
    person_id           UUID        REFERENCES persons (id),
    -- Punch ID stored inside the fingerprint device. This is what attendance
    -- syncs are matched on — never the employee name.
    device_user_id      VARCHAR(64),
    device_user_pin     VARCHAR(64),
    card_number         VARCHAR(64),
    national_id         VARCHAR(40),
    gender              VARCHAR(20),
    phone              VARCHAR(40),
    email               VARCHAR(150),
    address             VARCHAR(250),
    hire_date           DATE,
    contract_type       VARCHAR(30),
    contract_end_date   DATE,
    basic_salary        NUMERIC(14, 2),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes               TEXT,
    terminated_at       TIMESTAMP,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_employees_company_number UNIQUE (company_id, employee_number),
    CONSTRAINT chk_hr_employees_status CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED')),
    CONSTRAINT chk_hr_employees_contract_type CHECK (
        contract_type IS NULL
            OR contract_type IN ('PERMANENT', 'FIXED_TERM', 'TEMPORARY', 'TRAINING', 'OTHER')
    ),
    CONSTRAINT chk_hr_employees_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE'))
);

CREATE INDEX idx_hr_employees_company ON hr_employees (company_id);
CREATE INDEX idx_hr_employees_department ON hr_employees (department_id);
CREATE INDEX idx_hr_employees_status ON hr_employees (company_id, status);
CREATE INDEX idx_hr_employees_device_user ON hr_employees (company_id, device_user_id);

-- Punch IDs must be unique per tenant, otherwise a sync cannot tell which
-- employee a record belongs to.
CREATE UNIQUE INDEX uq_hr_employees_device_user
    ON hr_employees (company_id, device_user_id)
    WHERE device_user_id IS NOT NULL AND length(trim(device_user_id)) > 0;

-- --------------------------------------------------------- attendance devices
CREATE TABLE hr_attendance_devices (
    id                  UUID        PRIMARY KEY,
    company_id          UUID        NOT NULL REFERENCES companies (id),
    name                VARCHAR(150) NOT NULL,
    location            VARCHAR(250),
    ip_address          VARCHAR(64) NOT NULL,
    port                INTEGER     NOT NULL DEFAULT 4370,
    protocol            VARCHAR(30) NOT NULL DEFAULT 'ZK_TECO_TCP',
    -- Serial reported by iClock push devices; identifies the device without auth.
    serial_number       VARCHAR(64),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- Pointer into a full calendar day the device has already been synced for.
    last_sync_at        TIMESTAMP,
    last_sync_message   VARCHAR(500),
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hr_attendance_devices_company_ip UNIQUE (company_id, ip_address),
    CONSTRAINT chk_hr_attendance_devices_protocol CHECK (
        protocol IN ('ZK_TECO_TCP', 'I_CLOCK_PUSH', 'MANUAL')
    ),
    CONSTRAINT chk_hr_attendance_devices_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_hr_attendance_devices_company ON hr_attendance_devices (company_id);

CREATE UNIQUE INDEX uq_hr_attendance_devices_serial
    ON hr_attendance_devices (company_id, serial_number)
    WHERE serial_number IS NOT NULL AND length(trim(serial_number)) > 0;

-- -------------------------------------------------------- attendance records
CREATE TABLE hr_attendance_records (
    id                  UUID        PRIMARY KEY,
    company_id          UUID        NOT NULL REFERENCES companies (id),
    device_id           UUID        NOT NULL REFERENCES hr_attendance_devices (id) ON DELETE CASCADE,
    -- NULL when the punch ID on the device has no matching employee yet.
    employee_id         UUID        REFERENCES hr_employees (id) ON DELETE SET NULL,
    -- Punch ID as reported by the device; kept even after an employee is deleted.
    device_user_id      VARCHAR(64) NOT NULL,
    punched_at          TIMESTAMP   NOT NULL,
    punch_type          VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    verify_mode         VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    work_code           VARCHAR(40),
    -- Raw device payload, kept for troubleshooting mismatched punches.
    raw_payload         TEXT,
    created_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_hr_attendance_records_punch_type CHECK (
        punch_type IN ('CHECK_IN', 'CHECK_OUT', 'BREAK_OUT', 'BREAK_IN',
                       'OVERTIME_IN', 'OVERTIME_OUT', 'UNKNOWN')
    ),
    CONSTRAINT chk_hr_attendance_records_verify_mode CHECK (
        verify_mode IN ('PASSWORD', 'FINGERPRINT', 'FINGER_VEIN', 'FACE',
                        'PALM', 'CARD', 'MANUAL', 'UNKNOWN')
    )
);

-- The same punch reported twice (re-sync, or the same device seen by two
-- syncs) must not create a second row.
CREATE UNIQUE INDEX uq_hr_attendance_records_punch
    ON hr_attendance_records (device_id, device_user_id, punched_at);

CREATE INDEX idx_hr_attendance_records_company_date
    ON hr_attendance_records (company_id, punched_at DESC);
CREATE INDEX idx_hr_attendance_records_employee
    ON hr_attendance_records (employee_id, punched_at DESC);
CREATE INDEX idx_hr_attendance_records_device
    ON hr_attendance_records (device_id, punched_at DESC);
