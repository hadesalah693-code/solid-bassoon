package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.AccountingReportResponse.AgingBucket;
import com.kalima.backend.accounting.dto.JournalEntryCreateRequest;
import com.kalima.backend.accounting.dto.JournalEntryCreateRequest.LineRequest;
import com.kalima.backend.accounting.dto.JournalEntryResponse;
import com.kalima.backend.accounting.dto.JournalEntryResponse.LineResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.BankAccount;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.entity.JournalEntry.Source;
import com.kalima.backend.accounting.entity.JournalLine;
import com.kalima.backend.accounting.repository.JournalEntryRepository;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class JournalService {

    private final JournalEntryRepository journalEntryRepository;
    private final AccountingService accounting;

    public JournalService(JournalEntryRepository journalEntryRepository, AccountingService accounting) {
        this.journalEntryRepository = journalEntryRepository;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public Page<JournalEntryResponse> list(LocalDate from, LocalDate to, Source source,
                                            String search, Pageable pageable) {
        String term = StringUtils.hasText(search) ? search.trim() : null;
        return journalEntryRepository
                .search(TenantContext.getRequired(), AccountingDates.from(from), AccountingDates.to(to),
                        source, term, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse get(UUID id) {
        return journalEntryRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "القيد غير موجود"));
    }

    @Transactional
    public JournalEntryResponse create(JournalEntryCreateRequest request) {
        List<AccountingService.Line> lines = new ArrayList<>();
        for (LineRequest l : request.lines()) {
            BigDecimal debit = l.debit() == null ? BigDecimal.ZERO : l.debit();
            BigDecimal credit = l.credit() == null ? BigDecimal.ZERO : l.credit();
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "كل سطر يجب أن يحتوي على مبلغ مدين أو دائن");
            }
            Account account = accounting.requireAccountById(l.accountId());
            if (!account.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يمكن الترحيل إلى حساب غير نشط: " + account.getCode());
            }
            Person client = accounting.requirePerson(l.clientId());
                BankAccount bank = l.bankAccountId() == null ? null
                    : accounting.findBankAccount(l.bankAccountId())
                        .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "الحساب البنكي غير موجود"));
            if (bank != null && !bank.isActive()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب البنكي موقوف");
            }
            String description = StringUtils.hasText(l.description()) ? l.description() : request.description();

            lines.add(new AccountingService.Line(debit, credit, account, description, client, bank));
        }

        try {
            JournalEntry entry = accounting.post(request.entryDate(), request.description().trim(),
                    Source.MANUAL, "JOURNAL_ENTRY", null, accounting.currentUser(), lines);
            return toResponse(entry);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @Transactional
    public JournalEntryResponse reverse(UUID id) {
        JournalEntry entry = journalEntryRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "القيد غير موجود"));
        try {
            return toResponse(accounting.reverse(entry, accounting.currentUser()));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public boolean isPostedToPeriod(LocalDate date) {
        return accounting.isPeriodOpen(date);
    }

    private JournalEntryResponse toResponse(JournalEntry e) {
        List<LineResponse> lines = e.getLines().stream()
                .sorted((a, b) -> Integer.compare(a.getLineNo(), b.getLineNo()))
                .map(this::toLineResponse)
                .toList();
        return new JournalEntryResponse(
                e.getId(), e.getEntryNumber(), e.getEntryDate(), e.getDescription(),
                e.getReferenceType(), e.getReferenceId(), e.getSource(), e.isPosted(),
                e.getTotalDebit(), e.getTotalCredit(),
                e.getCreatedBy() == null ? null : e.getCreatedBy().getFullName(),
                lines);
    }

    private LineResponse toLineResponse(JournalLine l) {
        Account a = l.getAccount();
        return new LineResponse(
                l.getId(), l.getLineNo(),
                a.getId(), a.getCode(), a.getNameAr(), a.getType(),
                l.getDebit(), l.getCredit(), l.getDescription(),
                l.getClient() == null ? null : l.getClient().getId(),
                l.getClient() == null ? null : l.getClient().getFullName());
    }
}
