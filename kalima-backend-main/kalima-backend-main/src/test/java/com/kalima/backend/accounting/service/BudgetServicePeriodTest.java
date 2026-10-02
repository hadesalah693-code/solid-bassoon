package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.BudgetCreateRequest;
import com.kalima.backend.accounting.entity.Budget;
import com.kalima.backend.accounting.repository.BudgetRepository;
import com.kalima.backend.accounting.repository.JournalLineRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Budget period resolution.
 *
 * <p>The rule under test is that a monthly budget covers one month. When MONTH and
 * QUARTER silently spanned the whole financial year, every monthly budget reported
 * against twelve times its own target and looked a third spent all year.
 */
class BudgetServicePeriodTest {

    private final BudgetRepository budgets = mock(BudgetRepository.class);
    private final JournalLineRepository lines = mock(JournalLineRepository.class);
    private final AccountingService accounting = mock(AccountingService.class);
    private final AuditLogService audit = mock(AuditLogService.class);

    @BeforeEach
    void setUp() {
        TenantContext.set(java.util.UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private BudgetService service() {
        return new BudgetService(budgets, lines, accounting, audit);
    }

    private BudgetCreateRequest request(Budget.Period period, Integer month, Integer quarter) {
        return new BudgetCreateRequest("مخزون", 2026, period, month, quarter,
                null, null, null, new BigDecimal("1000"), null);
    }

    @Test
    void monthlyBudgetCoversOnlyItsOwnMonth() {
        whenStubbedSave();

        BudgetService service = service();
        service.create(request(Budget.Period.MONTH, 2, null));

        assertThat(capturedPeriod()).satisfies(p -> {
            assertThat(p.getPeriodStart()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(p.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
            assertThat(p.getFiscalYear()).isEqualTo(2026);
        });
    }

    @Test
    void monthlyBudgetCoversALeapFebruary() {
        whenStubbedSave();

        BudgetService service = new BudgetService(budgets, lines, accounting, audit);
        service.create(new BudgetCreateRequest("مخزون", 2028, Budget.Period.MONTH, 2, null,
                null, null, null, new BigDecimal("1000"), null));

        assertThat(capturedPeriod().getPeriodEnd()).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @Test
    void quarterlyBudgetCoversThreeMonths() {
        whenStubbedSave();

        service().create(request(Budget.Period.QUARTER, null, 2));

        assertThat(capturedPeriod()).satisfies(p -> {
            assertThat(p.getPeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(p.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
        });
    }

    @Test
    void monthlyBudgetWithoutAMonthIsRejected() {
        assertThatThrownBy(() -> service().create(request(Budget.Period.MONTH, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("شهر");
    }

    @Test
    void outOfRangeQuarterIsRejected() {
        assertThatThrownBy(() -> service().create(request(Budget.Period.QUARTER, null, 5)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void customPeriodNeedsItsOwnDates() {
        assertThatThrownBy(() -> service().create(request(Budget.Period.CUSTOM, null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void yearlyBudgetCoversTheWholeYear() {
        whenStubbedSave();

        service().create(request(Budget.Period.YEAR, null, null));

        assertThat(capturedPeriod()).satisfies(p -> {
            assertThat(p.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(p.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 12, 31));
        });
    }

    private void whenStubbedSave() {
        org.mockito.Mockito.when(budgets.save(org.mockito.ArgumentMatchers.any(Budget.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Budget capturedPeriod() {
        org.mockito.ArgumentCaptor<Budget> captor = org.mockito.ArgumentCaptor.forClass(Budget.class);
        verify(budgets).save(captor.capture());
        return captor.getValue();
    }
}