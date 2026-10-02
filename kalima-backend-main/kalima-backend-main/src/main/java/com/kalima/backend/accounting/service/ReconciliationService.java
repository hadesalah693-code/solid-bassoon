package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.BankReconciliationCreateRequest;
import com.kalima.backend.accounting.dto.BankReconciliationResponse;
import com.kalima.backend.accounting.dto.StatementLineCreateRequest;
import com.kalima.backend.accounting.dto.StatementLineResponse;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.BankReconciliation;
import com.kalima.backend.accounting.entity.BankStatementLine;
import com.kalima.backend.accounting.repository.BankAccountRepository;
import com.kalima.backend.accounting.repository.BankReconciliationRepository;
import com.kalima.backend.accounting.repository.BankStatementLineRepository;
import com.kalima.backend.accounting.repository.VendorPaymentRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bank reconciliation.
 *
 * <p>Matching deliberately posts nothing. Both sides of every match are already in
 * the ledger, so re-posting them would double-count; reconciliation only asserts that
 * they agree. Completion requires every line to be matched and the reconciled balance
 * to be within a cent of the statement, otherwise the reconciliation would bless an
 * unexplained gap.
 */
@Service
public class ReconciliationService {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final BankReconciliationRepository reconciliationRepository;
    private final BankStatementLineRepository statementLineRepository;
    private final VendorPaymentRepository paymentRepository;
    private final BankAccountRepository bankAccountRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public ReconciliationService(BankReconciliationRepository reconciliationRepository,
                                 BankStatementLineRepository statementLineRepository,
                                 VendorPaymentRepository paymentRepository,
                                 BankAccountRepository bankAccountRepository,
                                 AccountingService accounting,
                                 AuditLogService auditLogService) {
        this.reconciliationRepository = reconciliationRepository;
        this.statementLineRepository = statementLineRepository;
        this.paymentRepository = paymentRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public BankReconciliationResponse open(@Valid BankReconciliationCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        BankAccount bank = loadBank(request.bankAccountId());
        LocalDate date = request.statementDate();

        if (reconciliationRepository
                .findByCompany_IdAndBankAccount_IdAndStatementDate(tenantId, bank.getId(), date)
                .isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "يوجد تسوية مكشوفة لنفس الحساب في نفس التاريخ");
        }

        BankReconciliation rec = BankReconciliation.of(accounting.currentTenantReference());
        rec.setBankAccount(bank);
        rec.setStatementDate(date);
        rec.setStatementClosingBalance(request.statementClosingBalance());
        BigDecimal opening = openingBalance(rec);
        rec.setReconciledBalance(opening);
        rec.setDifference(opening.subtract(request.statementClosingBalance()));
        rec.setStatus(BankReconciliation.Status.IN_PROGRESS);
        rec.setNotes(request.notes());
        rec.setCreatedBy(accounting.currentUser());
        BankReconciliation saved = reconciliationRepository.save(rec);

        auditLogService.recordSuccess("ACCOUNTING_RECONCILIATION_OPENED", accounting.currentUser(),
                "BankReconciliation", saved.getId(),
                java.util.Map.of("statementDate", date.toString()));
        return BankReconciliationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BankReconciliationResponse get(UUID id) {
        return BankReconciliationResponse.from(load(id));
    }

    @Transactional(readOnly = true)
    public List<BankReconciliationResponse> list(UUID bankAccountId) {
        return reconciliationRepository.findByAccount(TenantContext.getRequired(), bankAccountId)
                .stream().map(BankReconciliationResponse::from).toList();
    }

    @Transactional
    public StatementLineResponse addLine(UUID reconciliationId, @Valid StatementLineCreateRequest request) {
        BankReconciliation rec = load(reconciliationId);
        if (rec.getStatus() != BankReconciliation.Status.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن إضافة سطور بعد اكتمال التسوية");
        }
        if (request.amount().signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "قيمة سطر كشف الحساب لا يمكن أن تكون صفراً");
        }

        BankStatementLine line = BankStatementLine.of(accounting.currentTenantReference());
        line.setBankAccount(rec.getBankAccount());
        line.setReconciliation(rec);
        line.setLineDate(request.lineDate() == null ? rec.getStatementDate() : request.lineDate());
        line.setDescription(request.description());
        line.setReference(request.reference());
        line.setAmount(request.amount());
        line.setNotes(request.notes());
        line.setCreatedBy(accounting.currentUser());
        BankStatementLine saved = statementLineRepository.save(line);
        recalculate(rec);
        return StatementLineResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<StatementLineResponse> listLines(UUID reconciliationId, boolean onlyUnmatched) {
        BankReconciliation rec = load(reconciliationId);
        return linesOf(rec).stream()
                .filter(l -> !onlyUnmatched || l.getMatchedId() == null)
                .map(StatementLineResponse::from)
                .toList();
    }

    /**
     * Ties a statement line to a receipt, payment or transfer.
     *
     * <p>Nothing is posted: the counterpart is already in the ledger. Matching only
     * settles the sign, so the reconciled balance moves the way the bank did.
     */
    @Transactional
    public StatementLineResponse match(UUID lineId, BankStatementLine.MatchType type, UUID matchedId) {
        BankStatementLine line = statementLineRepository
                .findByCompany_IdAndId(TenantContext.getRequired(), lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "سطر الكشف غير موجود"));
        if (line.getMatchedId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "السطر مطابق بالفعل");
        }
        if (type == null || matchedId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "نوع المطابقة والمعرّف مطلوبان");
        }
        line.setMatchedType(type);
        line.setMatchedId(matchedId);
        line.setMatchedAt(Instant.now());
        BankStatementLine saved = statementLineRepository.save(line);
        if (line.getReconciliation() != null) {
            recalculate(line.getReconciliation());
        }
        return StatementLineResponse.from(saved);
    }

    @Transactional
    public StatementLineResponse unmatch(UUID lineId) {
        BankStatementLine line = statementLineRepository
                .findByCompany_IdAndId(TenantContext.getRequired(), lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "سطر الكشف غير موجود"));
        line.setMatchedType(null);
        line.setMatchedId(null);
        line.setMatchedAt(null);
        BankStatementLine saved = statementLineRepository.save(line);
        if (line.getReconciliation() != null) {
            recalculate(line.getReconciliation());
        }
        return StatementLineResponse.from(saved);
    }

    /**
     * Suggestions for an unmatched statement line: receipts and vendor payments on
     * the same account, day and amount. Purely a reading aid, so matching stays an
     * explicit human decision.
     */
    @Transactional(readOnly = true)
    public List<MatchSuggestion> suggest(UUID lineId) {
        BankStatementLine line = statementLineRepository
                .findByCompany_IdAndId(TenantContext.getRequired(), lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "سطر الكشف غير موجود"));
        if (line.getMatchedId() != null) {
            return List.of();
        }
        BigDecimal absolute = line.getAmount().abs();
        LocalDate date = line.getLineDate();
        UUID bankAccountId = line.getBankAccount().getId();
        UUID tenantId = TenantContext.getRequired();

        List<MatchSuggestion> out = new ArrayList<>();
        if (line.getAmount().signum() > 0) {
            for (com.kalima.backend.accounting.entity.Receipt r :
                    statementLineRepository.suggestReceipts(tenantId, bankAccountId, date, absolute)) {
                out.add(new MatchSuggestion(BankStatementLine.MatchType.RECEIPT, r.getId(),
                        r.getReceiptNumber(), r.getAmount(), r.getReceiptDate()));
            }
        } else {
            for (com.kalima.backend.accounting.entity.VendorPayment p :
                    paymentRepository.suggestForLine(tenantId, bankAccountId, date, absolute)) {
                out.add(new MatchSuggestion(BankStatementLine.MatchType.PAYMENT, p.getId(),
                        p.getBill() == null ? null : p.getBill().getBillNumber(),
                        p.getAmount(), p.getPaymentDate()));
            }
        }
        return out;
    }

    public record MatchSuggestion(BankStatementLine.MatchType type, UUID id,
                                  String reference, BigDecimal amount, LocalDate date) {
    }

    /**
     * Closes the reconciliation, but only once the reconciled balance agrees with the
     * statement to within a cent.
     */
    @Transactional
    public BankReconciliationResponse complete(UUID id) {
        BankReconciliation rec = load(id);
        if (rec.getStatus() != BankReconciliation.Status.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "التسوية غير مكتملة");
        }
        recalculate(rec);
        List<BankStatementLine> unmatched = linesOf(rec).stream()
                .filter(l -> l.getMatchedId() == null)
                .toList();
        if (!unmatched.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن إكمال التسوية ويوجد " + unmatched.size() + " سطر غير مطابق");
        }
        if (rec.getDifference().abs().compareTo(TOLERANCE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "لا يمكن إكمال التسوية والفرق " + rec.getDifference()
                            + " أكبر من الحد المسموح");
        }
        rec.setStatus(BankReconciliation.Status.COMPLETED);
        rec.setCompletedAt(Instant.now());
        rec.setCompletedBy(accounting.currentUser());
        BankReconciliation saved = reconciliationRepository.save(rec);

        auditLogService.recordSuccess("ACCOUNTING_RECONCILIATION_COMPLETED", accounting.currentUser(),
                "BankReconciliation", saved.getId(),
                java.util.Map.of("difference", rec.getDifference().toPlainString()));
        return BankReconciliationResponse.from(saved);
    }

    /**
     * Recomputes the reconciled balance from the statement lines of this
     * reconciliation, matched or not.
     *
     * <p>Every line counts, because the point of a reconciliation is that the closing
     * balance on the bank's paper equals the closing balance on ours. Summing only
     * the unmatched lines would move the figure every time a line was matched and
     * make an agreed statement look like a growing discrepancy.
     */
    private void recalculate(BankReconciliation rec) {
        BigDecimal movement = BigDecimal.ZERO;
        for (BankStatementLine line : linesOf(rec)) {
            movement = movement.add(line.getAmount());
        }
        BigDecimal reconciled = openingBalance(rec).add(movement);
        rec.setReconciledBalance(reconciled);
        rec.setDifference(reconciled.subtract(rec.getStatementClosingBalance()));
        reconciliationRepository.save(rec);
    }

    /**
     * Carries the previous statement's closing position forward, so each
     * reconciliation starts from where the last one ended instead of from zero.
     */
    private BigDecimal openingBalance(BankReconciliation rec) {
        return reconciliationRepository
                .findLastCompleted(TenantContext.getRequired(), rec.getBankAccount().getId(),
                        rec.getStatementDate().minusDays(1))
                .stream()
                .findFirst()
                .map(BankReconciliation::getReconciledBalance)
                .orElse(BigDecimal.ZERO);
    }

    /** The statement lines belonging to this reconciliation and no other. */
    private List<BankStatementLine> linesOf(BankReconciliation rec) {
        return statementLineRepository
                .findForReconciliation(TenantContext.getRequired(),
                        rec.getBankAccount().getId(), rec.getStatementDate(), false)
                .stream()
                .filter(l -> l.getReconciliation() != null
                        && rec.getId().equals(l.getReconciliation().getId()))
                .toList();
    }

    private BankAccount loadBank(UUID bankAccountId) {
        return bankAccountRepository.findByCompany_IdAndId(TenantContext.getRequired(), bankAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "الحساب البنكي غير موجود"));
    }

    private BankReconciliation load(UUID id) {
        return reconciliationRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "التسوية غير موجودة"));
    }
}