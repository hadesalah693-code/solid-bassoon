package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.BankReconciliation;
import com.kalima.backend.accounting.entity.BankStatementLine;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.accounting.repository.BankReconciliationRepository;
import com.kalima.backend.accounting.repository.BankStatementLineRepository;
import com.kalima.backend.accounting.repository.VendorPaymentRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import java.time.LocalDate;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reconciliation closing.
 *
 * <p>The reconciled balance has to move when a line is matched, not only when one is
 * added. Summing just the unmatched lines made an agreed statement look like a
 * growing discrepancy and meant a reconciliation could never be closed.
 */
class ReconciliationServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID RECONCILIATION = UUID.randomUUID();

    private BankReconciliationRepository reconciliations;
    private BankStatementLineRepository statementLines;
    private ReconciliationService service;
    private BankReconciliation reconciliation;
    private BankStatementLine deposit;
    private BankStatementLine withdrawal;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);

        reconciliations = mock(BankReconciliationRepository.class);
        statementLines = mock(BankStatementLineRepository.class);
        service = new ReconciliationService(reconciliations, statementLines,
                mock(VendorPaymentRepository.class), mock(BankAccountRepository.class),
                mock(AccountingService.class), mock(AuditLogService.class));

reconciliation = BankReconciliation.of(mock(Company.class));
        reconciliation.setId(RECONCILIATION);
        reconciliation.setStatus(BankReconciliation.Status.IN_PROGRESS);
        reconciliation.setStatementDate(LocalDate.of(2026, 3, 31));
        BankAccount bank = mock(BankAccount.class);
        when(bank.getId()).thenReturn(ACCOUNT);
        reconciliation.setBankAccount(bank);
        reconciliation.setStatementClosingBalance(new BigDecimal("1500.00"));

        deposit = line(new BigDecimal("1000.00"));
        withdrawal = line(new BigDecimal("-500.00"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

@Test
    void matchedLinesStillCountTowardsTheReconciledBalance() {
        deposit.setMatchedId(UUID.randomUUID());
        withdrawal.setMatchedId(UUID.randomUUID());
        givenStatementLines();
        givenReconciliationInProgress();

        assertThatThrownBy(() -> service.complete(RECONCILIATION))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("-1000.00");

        assertThat(reconciliation.getReconciledBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void aStatementThatAgreesCanBeClosed() {
        reconciliation.setStatementClosingBalance(new BigDecimal("500.00"));
        givenStatementLines();
        givenReconciliationInProgress();
        deposit.setMatchedId(UUID.randomUUID());
        withdrawal.setMatchedId(UUID.randomUUID());

        service.complete(RECONCILIATION);

        assertThat(reconciliation.getStatus()).isEqualTo(BankReconciliation.Status.COMPLETED);
        assertThat(reconciliation.getDifference()).isEqualByComparingTo("0.00");
    }

@Test
    void unmatchedLinesBlockClosing() {
        givenStatementLines();
        givenReconciliationInProgress();

        assertThatThrownBy(() -> service.complete(RECONCILIATION))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("2");
    }

    @Test
    void anUnexplainedGapBlocksClosing() {
        reconciliation.setStatementClosingBalance(new BigDecimal("900.00"));
        givenStatementLines();
        givenReconciliationInProgress();
        deposit.setMatchedId(UUID.randomUUID());
        withdrawal.setMatchedId(UUID.randomUUID());

        assertThatThrownBy(() -> service.complete(RECONCILIATION))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("الفرق");
    }

    private void givenStatementLines() {
        when(statementLines.findForReconciliation(eq(TENANT), eq(ACCOUNT), any(LocalDate.class), anyBoolean()))
                .thenReturn(List.of(deposit, withdrawal));
    }

    private void givenReconciliationInProgress() {
        when(reconciliations.findByCompany_IdAndId(eq(TENANT), eq(RECONCILIATION)))
                .thenReturn(Optional.of(reconciliation));
        when(reconciliations.findLastCompleted(eq(TENANT), eq(ACCOUNT), any()))
                .thenReturn(List.of());
        when(reconciliations.save(any(BankReconciliation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BankStatementLine line(BigDecimal amount) {
        BankStatementLine line = BankStatementLine.of(mock(Company.class));
        line.setId(UUID.randomUUID());
        line.setAmount(amount);
        line.setReconciliation(reconciliation);
        return line;
    }
}