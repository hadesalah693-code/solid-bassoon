package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.entity.Account;

import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for the system chart of accounts.
 *
 * <p>{@code V60__accounting_module.sql} contains the original SQL seed list and is
 * kept untouched (Flyway migrations are immutable). Everything created after V60 is
 * declared here instead, so the account list is not duplicated: this catalog is the
 * only place new system accounts are defined, and {@link ChartOfAccountsSeeder}
 * applies it to any company that is missing accounts.
 */
public final class SystemAccountCatalog {

    /**
     * @param code    stable code; referenced as a constant from
     *                {@link AccountingService} so the code is never duplicated as a literal
     * @param nameAr  Arabic label shown in the UI
     * @param nameEn  English label
     * @param type    account type; drives which report the account rolls up into
     */
    public record SystemAccount(String code, String nameAr, String nameEn, Account.Type type) {
    }

    /**
     * 1000-3000 mirror the V60 seed exactly. 1400 / 2300 / 2400 were added afterwards
     * for unbilled billable work, client retainers and deferred revenue; because
     * seeding is additive and idempotent, existing companies receive them on their
     * next ensure without any data migration.
     */
    public static final List<SystemAccount> ALL = List.of(
            // ---- Assets (1000) ----
            new SystemAccount("1000", "الأصول", "Assets", Account.Type.ASSET),
            new SystemAccount("1100", "النقدية في الصندوق", "Cash on Hand", Account.Type.ASSET),
            new SystemAccount("1110", "البنك", "Bank", Account.Type.ASSET),
            // Statement lines that arrived from the bank feed but have not been matched
            // to a receipt, bill or transfer yet. Reconciling a line into this account
            // parks it outside the reconciled bank balance instead of silently
            // inflating it.
            new SystemAccount("1160", "أرصدة بنكية غير مُسوّاة", "Unreconciled Bank Balance", Account.Type.ASSET),
            new SystemAccount("1200", "ذمم العملاء", "Accounts Receivable", Account.Type.ASSET),
            // WIP / unbilled billable work: a billable case expense or unbilled time
            // entry is capitalised here instead of hitting P&L, then relieved to AR
            // when the client invoice is issued.
            new SystemAccount("1400", "ذمم عملاء غير مفوترة", "Unbilled Receivables", Account.Type.ASSET),
            new SystemAccount("1500", "مصروفات مدفوعة مقدماً", "Prepaid Expenses", Account.Type.ASSET),

            // ---- Liabilities (2000) ----
            new SystemAccount("2000", "الالتزامات", "Liabilities", Account.Type.LIABILITY),
            new SystemAccount("2100", "ذمم الموردين", "Accounts Payable", Account.Type.LIABILITY),
            new SystemAccount("2200", "ضريبة القيمة المضافة المستحقة", "VAT Payable", Account.Type.LIABILITY),
            new SystemAccount("2210", "ضريبة القيمة المضافة على المشتريات", "VAT Input (Recoverable)", Account.Type.ASSET),
            // Client money held on account. Receiving a retainer is a liability, never
            // revenue, until it is applied to work actually performed.
            new SystemAccount("2300", "التزامات دفعة العميل المقدمة", "Client Retainer Liabilities", Account.Type.LIABILITY),
            new SystemAccount("2400", "إيرادات مؤجلة", "Deferred Revenue", Account.Type.LIABILITY),
            // Client cash received before we know which invoice it belongs to. Crediting
            // AR on receipt would let an unapplied payment understate the receivable,
            // so on-account money lands here and only moves to AR when allocated.
            new SystemAccount("2350", "أرصدة دائنة للعميل غير مخصصة", "Unapplied Client Credit", Account.Type.LIABILITY),

            // ---- Equity (3000) ----
            new SystemAccount("3000", "حقوق الملكية", "Equity", Account.Type.EQUITY),
            new SystemAccount("3100", "رأس المال", "Capital", Account.Type.EQUITY),
            new SystemAccount("3200", "المسحوبات", "Drawings", Account.Type.EQUITY),
            new SystemAccount("3300", "الأرباح المُرحَّلة", "Retained Earnings", Account.Type.EQUITY),

            // ---- Revenue (4000) ----
            new SystemAccount("4000", "الإيرادات", "Revenue", Account.Type.REVENUE),
            new SystemAccount("4100", "إيرادات الأتعاب القانونية", "Legal Fees Income", Account.Type.REVENUE),
            new SystemAccount("4200", "إيرادات الاستشارات", "Consulting Income", Account.Type.REVENUE),
            // Contra-revenue. Credit notes and sales returns are posted here rather
            // than to 4900 so net revenue stays meaningful on the income statement.
            new SystemAccount("4110", "مردودات المبيعات", "Sales Returns & Allowances", Account.Type.REVENUE),
            new SystemAccount("4900", "إيرادات أخرى", "Other Income", Account.Type.REVENUE),

            // ---- Expenses (5000) ----
            new SystemAccount("5000", "المصروفات", "Expenses", Account.Type.EXPENSE),
            new SystemAccount("5100", "الرواتب والأجور", "Salaries & Wages", Account.Type.EXPENSE),
            new SystemAccount("5200", "الإيجار", "Rent", Account.Type.EXPENSE),
            new SystemAccount("5300", "الخدمات والفواتير", "Utilities", Account.Type.EXPENSE),
            new SystemAccount("5400", "التسويق والإعلان", "Marketing", Account.Type.EXPENSE),
            new SystemAccount("5500", "البرمجيات والاشتراكات", "Software & Subscriptions", Account.Type.EXPENSE),
            new SystemAccount("5600", "الرسوم الحكومية والقضائية", "Government & Court Fees", Account.Type.EXPENSE),
            new SystemAccount("5700", "مصروفات قانونية", "Legal Fees Expense", Account.Type.EXPENSE),
            new SystemAccount("5800", "النقل والمواصلات", "Transport", Account.Type.EXPENSE),
            new SystemAccount("5900", "مستلزمات مكتب", "Office Supplies", Account.Type.EXPENSE),
            new SystemAccount("5950", "مصروفات متنوعة", "Other Expenses", Account.Type.EXPENSE));

    private SystemAccountCatalog() {
    }

    public static Optional<SystemAccount> byCode(String code) {
        return ALL.stream().filter(a -> a.code().equals(code)).findFirst();
    }
}
