package com.kalima.backend.audit;

/**
 * Canonical names for audited actions. Stored verbatim in
 * {@code audit_logs.action}; treat renames as a breaking change for anyone
 * querying history. New values can be added freely.
 */
public final class AuditAction {

    private AuditAction() {
    }

    // Authentication
    public static final String LOGIN_SUCCESS       = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED        = "LOGIN_FAILED";

    // Staff lifecycle
    public static final String STAFF_CREATED       = "STAFF_CREATED";
    public static final String STAFF_UPDATED       = "STAFF_UPDATED";
    public static final String STAFF_DISABLED      = "STAFF_DISABLED";
    public static final String STAFF_ENABLED       = "STAFF_ENABLED";
    public static final String PASSWORD_CHANGED    = "PASSWORD_CHANGED";
    public static final String PASSWORD_RESET      = "PASSWORD_RESET";

    // Person (clients)
    public static final String PERSON_CREATED      = "PERSON_CREATED";
    public static final String PERSON_UPDATED      = "PERSON_UPDATED";
    public static final String PERSON_DELETED      = "PERSON_DELETED";
    public static final String PERSON_ARCHIVED     = "PERSON_ARCHIVED";

    // Cases
    public static final String CASE_CREATED        = "CASE_CREATED";
    public static final String CASE_UPDATED        = "CASE_UPDATED";
    public static final String CASE_STATUS_CHANGED = "CASE_STATUS_CHANGED";
    public static final String CASE_ARCHIVED       = "CASE_ARCHIVED";
    public static final String CASE_RESTORED       = "CASE_RESTORED";

    // Case participants
    public static final String PARTICIPANT_ADDED   = "PARTICIPANT_ADDED";
    public static final String PARTICIPANT_REMOVED = "PARTICIPANT_REMOVED";

    // Hearings
    public static final String HEARING_CREATED      = "HEARING_CREATED";
    public static final String HEARING_UPDATED      = "HEARING_UPDATED";

    // Tasks
    public static final String TASK_CREATED           = "TASK_CREATED";
    public static final String TASK_UPDATED           = "TASK_UPDATED";
    public static final String TASK_DELETED           = "TASK_DELETED";

    // Calendar
    public static final String CALENDAR_EVENT_CREATED = "CALENDAR_EVENT_CREATED";
    public static final String CALENDAR_EVENT_UPDATED = "CALENDAR_EVENT_UPDATED";
    public static final String CALENDAR_EVENT_DELETED = "CALENDAR_EVENT_DELETED";

    // Case documents
    public static final String DOCUMENT_UPLOADED      = "DOCUMENT_UPLOADED";
    public static final String DOCUMENT_DELETED       = "DOCUMENT_DELETED";

    // Dispute path (timeline milestones)
    public static final String DISPUTE_EVENT_CREATED  = "DISPUTE_EVENT_CREATED";
    public static final String DISPUTE_EVENT_UPDATED  = "DISPUTE_EVENT_UPDATED";
    public static final String DISPUTE_EVENT_DELETED  = "DISPUTE_EVENT_DELETED";

    // AI Agent layer
    public static final String AGENT_VOICE_COMMAND     = "AGENT_VOICE_COMMAND";
    public static final String AGENT_PENDING_CONFIRMED = "AGENT_PENDING_CONFIRMED";
    public static final String AGENT_CASE_CLOSED       = "AGENT_CASE_CLOSED";
    public static final String AGENT_CASE_MODIFIED     = "AGENT_CASE_MODIFIED";
    public static final String AGENT_TASK_ASSIGNED       = "AGENT_TASK_ASSIGNED";
    public static final String AGENT_NOTIFICATION_SENT   = "AGENT_NOTIFICATION_SENT";
    public static final String AGENT_DELEGATION_PREP     = "AGENT_DELEGATION_PREP";
    public static final String AGENT_NIGHT_RUN           = "AGENT_NIGHT_RUN";
    public static final String AGENT_NIGHT_AUTO_ACTION   = "AGENT_NIGHT_AUTO_ACTION";

    // Delegations (powers of attorney)
    public static final String DELEGATION_CREATED       = "DELEGATION_CREATED";
    public static final String DELEGATION_UPDATED       = "DELEGATION_UPDATED";
    public static final String DELEGATION_CANCELLED     = "DELEGATION_CANCELLED";
    public static final String DELEGATION_REINSTATED    = "DELEGATION_REINSTATED";
    public static final String DELEGATION_CASE_LINKED   = "DELEGATION_CASE_LINKED";
    public static final String DELEGATION_CASE_UNLINKED = "DELEGATION_CASE_UNLINKED";

    // Delegation Settings: office attorneys
    public static final String DELEGATION_ATTORNEY_CREATED = "DELEGATION_ATTORNEY_CREATED";
    public static final String DELEGATION_ATTORNEY_UPDATED = "DELEGATION_ATTORNEY_UPDATED";
    public static final String DELEGATION_ATTORNEY_DELETED = "DELEGATION_ATTORNEY_DELETED";

    public static final String OFFICE_SETTINGS_UPDATED = "OFFICE_SETTINGS_UPDATED";

    // Criminal reports (البلاغات الجنائية)
    public static final String CRIMINAL_REPORT_CREATED        = "CRIMINAL_REPORT_CREATED";
    public static final String CRIMINAL_REPORT_UPDATED        = "CRIMINAL_REPORT_UPDATED";
    public static final String CRIMINAL_REPORT_STATUS_CHANGED = "CRIMINAL_REPORT_STATUS_CHANGED";

    public static final String LABOR_COMPLAINT_CREATED        = "LABOR_COMPLAINT_CREATED";
    public static final String LABOR_COMPLAINT_UPDATED        = "LABOR_COMPLAINT_UPDATED";
    public static final String LABOR_COMPLAINT_STATUS_CHANGED = "LABOR_COMPLAINT_STATUS_CHANGED";
    public static final String LABOR_COMPLAINT_DOCUMENT_UPLOADED = "LABOR_COMPLAINT_DOCUMENT_UPLOADED";
    public static final String LABOR_COMPLAINT_DOCUMENT_DELETED  = "LABOR_COMPLAINT_DOCUMENT_DELETED";

    // OCR module
    public static final String OCR_UPLOADED             = "OCR_UPLOADED";
    public static final String OCR_PROCESS_STARTED      = "OCR_PROCESS_STARTED";
    public static final String OCR_COMPLETED            = "OCR_COMPLETED";
    public static final String OCR_FAILED               = "OCR_FAILED";
    public static final String OCR_UPDATED              = "OCR_UPDATED";
    public static final String OCR_DELETED              = "OCR_DELETED";
    public static final String OCR_RERUN                = "OCR_RERUN";

    // Mandates (إنابات المحامين)
    public static final String MANDATE_CREATED               = "MANDATE_CREATED";
    public static final String MANDATE_UPDATED               = "MANDATE_UPDATED";
    public static final String MANDATE_STATUS_CHANGED        = "MANDATE_STATUS_CHANGED";
    public static final String MANDATE_DELETED               = "MANDATE_DELETED";
    public static final String MANDATE_DOCUMENT_UPLOADED      = "MANDATE_DOCUMENT_UPLOADED";
    public static final String MANDATE_DOCUMENT_DELETED       = "MANDATE_DOCUMENT_DELETED";
    public static final String MANDATE_SESSION_RESULT         = "MANDATE_SESSION_RESULT";
    public static final String MANDATE_PAYMENT_RECORDED       = "MANDATE_PAYMENT_RECORDED";

    // HR module
    public static final String HR_DEPARTMENT_CREATED   = "HR_DEPARTMENT_CREATED";
    public static final String HR_DEPARTMENT_UPDATED   = "HR_DEPARTMENT_UPDATED";
    public static final String HR_DEPARTMENT_DELETED   = "HR_DEPARTMENT_DELETED";
    public static final String HR_POSITION_CREATED     = "HR_POSITION_CREATED";
    public static final String HR_POSITION_UPDATED     = "HR_POSITION_UPDATED";
    public static final String HR_POSITION_DELETED     = "HR_POSITION_DELETED";
    public static final String HR_EMPLOYEE_CREATED     = "HR_EMPLOYEE_CREATED";
    public static final String HR_EMPLOYEE_UPDATED     = "HR_EMPLOYEE_UPDATED";
    public static final String HR_EMPLOYEE_DELETED     = "HR_EMPLOYEE_DELETED";
    public static final String HR_DEVICE_CREATED       = "HR_DEVICE_CREATED";
    public static final String HR_DEVICE_UPDATED       = "HR_DEVICE_UPDATED";
    public static final String HR_DEVICE_DELETED       = "HR_DEVICE_DELETED";
    public static final String HR_ATTENDANCE_SYNCED    = "HR_ATTENDANCE_SYNCED";
    public static final String HR_ATTENDANCE_IMPORTED  = "HR_ATTENDANCE_IMPORTED";
    public static final String HR_ATTENDANCE_DELETED   = "HR_ATTENDANCE_DELETED";
}
