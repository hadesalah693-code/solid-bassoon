package com.kalima.backend.auth.seed;

/**
 * Shared credentials for the local dev trial environment ({@code dev} profile).
 * Safe to surface in the login UI during development only.
 */
public final class DemoTrialAccounts {

    public static final String ADMIN_EMAIL = "admin@kalima.local";
    public static final String LAWYER_EMAIL = "lawyer@kalima.local";
    public static final String CLIENT_EMAIL = "client@kalima.local";
    public static final String PASSWORD = "admin123";
    public static final String CLIENT_PASSWORD = "client123";

    /** Portal (CLIENT_USER) demo login — linked to the portaled client profile. */
    public static final String PORTAL_USER_EMAIL = "portal@kalima.local";
    public static final String PORTAL_USER_PASSWORD = "portal123";

    public static final String DEMO_CASE_NUMBER = "DEMO-CLIENT-001";
    public static final String DEMO_CASE_TITLE = "قضية متابعة تجريبية للعميل";

    private DemoTrialAccounts() {
    }
}
