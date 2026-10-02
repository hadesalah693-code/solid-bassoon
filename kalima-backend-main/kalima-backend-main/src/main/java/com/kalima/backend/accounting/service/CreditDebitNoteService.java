package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.CreditDebitNoteCreateRequest;
import com.kalima.backend.accounting.dto.CreditDebitNoteResponse;
import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.entity.AccountingSequence;
import com.kalima.backend.accounting.entity.CreditDebitNote;
import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import com.kalima.backend.accounting.entity.JournalEntry;
import com.kalima.backend.accounting.repository.CreditDebitNoteRepository;
import com.kalima.backend.audit.service.AuditLogService;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.domain.repository.PersonRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Credit and debit notes.
 *
 * <p>Direction is fixed by the note type, mirroring the invoice:
 * a credit note reduces what the client owes ({@code Dr 4110 + Dr 2200 / Cr 1200}),
 * a debit note increases it ({@code Dr 1200 / Cr 4100 + Cr 2200}).
 * VAT is relieved or charged on the same lines so 2200 always nets out correctly.
 */
@Service
public class CreditDebitNoteService {

    private final CreditDebitNoteRepository noteRepository;
    private final InvoiceRepository invoiceRepository;
    private final PersonRepository personRepository;
    private final AccountingService accounting;
    private final AuditLogService auditLogService;

    public CreditDebitNoteService(CreditDebitNoteRepository noteRepository,
                                  InvoiceRepository invoiceRepository,
                                  PersonRepository personRepository,
                                  AccountingService accounting,
                                  AuditLogService auditLogService) {
        this.noteRepository = noteRepository;
        this.invoiceRepository = invoiceRepository;
        this.personRepository = personRepository;
        this.accounting = accounting;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<CreditDebitNoteResponse> list(UUID clientId, CreditDebitNote.NoteType noteType,
                                              LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        return noteRepository.search(TenantContext.getRequired(), clientId, noteType, start, end)
                .stream().map(CreditDebitNoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CreditDebitNoteResponse get(UUID id) {
        return CreditDebitNoteResponse.from(load(id));
    }

    @Transactional
    public CreditDebitNoteResponse create(@Valid CreditDebitNoteCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        LocalDate date = request.noteDate() == null ? LocalDate.now() : request.noteDate();

        Invoice invoice = null;
        if (request.invoiceId() != null) {
            invoice = invoiceRepository.findByCompany_IdAndId(tenantId, request.invoiceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ø§Ù„ÙØ§ØªÙˆØ±Ø© ØºÙŠØ± Ù…ÙˆØ¬ÙˆØ¯Ø©"));
            if (invoice.getClient() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ø§Ù„ÙØ§ØªÙˆØ±Ø© ØºÙŠØ± Ù…Ø³Ø¬Ù„Ø© Ø¨Ø§Ø³Ù… Ø¹Ù…ÙŠÙ„");
            }
        }

        BigDecimal subtotal = nz(request.subtotal());
        if (subtotal.signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ÙŠØ¬Ø¨ Ø£Ù† ÙŠÙƒÙˆÙ† Ø§Ù„Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ù‚Ø¨Ù„ Ø§Ù„Ø¶Ø±ÙŠØ¨Ø© Ø£ÙƒØ¨Ø± Ù…Ù† ØµÙØ±");
        }
        BigDecimal taxRate = nz(request.taxRate());
        if (taxRate.signum() < 0 || taxRate.compareTo(new BigDecimal("100")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ù†Ø³Ø¨Ø© Ø§Ù„Ø¶Ø±ÙŠØ¨Ø© ÙŠØ¬Ø¨ Ø£Ù† ØªÙƒÙˆÙ† Ø¨ÙŠÙ† 0 Ùˆ 100");
        }
        BigDecimal taxAmount = nz(request.taxAmount());
        if (taxAmount.signum() == 0 && taxRate.signum() > 0) {
            taxAmount = subtotal.multiply(taxRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        }
        BigDecimal total = subtotal.add(taxAmount);

        if (invoice != null && request.noteType() == CreditDebitNote.NoteType.CREDIT) {
            // Cap credit notes at what is still owed, so a note can never wipe out
            // more than the invoice's remaining balance.
            BigDecimal remaining = invoice.remaining();
            BigDecimal alreadyCredited = nz(noteRepository.sumByInvoiceAndType(
                    invoice.getId(), CreditDebitNote.NoteType.CREDIT));
            BigDecimal available = remaining.add(alreadyCredited);
            if (total.compareTo(available) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ø¥Ø¬Ù…Ø§Ù„ÙŠ Ø¥Ø´Ø¹Ø§Ø± Ø§Ù„Ø®ØµÙ… (" + total + ") ÙŠØªØ¬Ø§ÙˆØ² Ø§Ù„Ø±ØµÙŠØ¯ Ø§Ù„Ù…ØªØ§Ø­ Ù„Ù„ÙØ§ØªÙˆØ±Ø© (" + available + ")");
            }
        }

        // The entity only exposes a protected constructor, so it is built through
        // a self-assigned instance factory on the entity itself.
        CreditDebitNote note = CreditDebitNote.of(accounting.currentTenantReference());
        note.setNoteType(request.noteType());
        note.setNoteDate(date);
        note.setClient(invoice != null ? invoice.getClient() : resolveClient(request.clientId()));
        note.setInvoice(invoice);
        note.setReason(request.reason());
        note.setSubtotal(subtotal);
        note.setTaxRate(taxRate);
        note.setTaxAmount(taxAmount);
        note.setTotal(total);
        note.setNotes(request.notes());
        note.setCreatedBy(accounting.currentUser());
        note.setNoteNumber(accounting.nextNumber(
                request.noteType() == CreditDebitNote.NoteType.CREDIT
                        ? AccountingSequence.Scope.CREDIT_NOTE
                        : AccountingSequence.Scope.DEBIT_NOTE, date));

        JournalEntry entry = postFor(note, date);
        note.setJournalEntry(entry);
        CreditDebitNote saved = noteRepository.save(note);
        entry.setReferenceType("CREDIT_DEBIT_NOTE");
        entry.setReferenceId(saved.getId());

        auditLogService.recordSuccess("ACCOUNTING_CREDIT_DEBIT_NOTE_ISSUED", accounting.currentUser(),
                "CreditDebitNote", saved.getId(),
                java.util.Map.of("noteNumber", saved.getNoteNumber(),
                        "noteType", saved.getNoteType().name(),
                        "total", total.toPlainString()));
        return CreditDebitNoteResponse.from(saved);
    }

    /**
     * Voids the note by reversing its entry rather than deleting it, so the
     * original document stays on the record and the ledger stays balanced.
     */
    @Transactional
    public void voidNote(UUID id) {
        CreditDebitNote note = load(id);
        if (note.getVoidedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ø§Ù„Ø¥Ø´Ø¹Ø§Ø± Ù…Ù„ØºÙ‰ Ø¨Ø§Ù„ÙØ¹Ù„");
        }
        accounting.reverse(note.getJournalEntry(), accounting.currentUser());
        note.setVoidedAt(java.time.Instant.now());
        noteRepository.save(note);
        auditLogService.recordSuccess("ACCOUNTING_CREDIT_DEBIT_NOTE_VOIDED", accounting.currentUser(),
                "CreditDebitNote", note.getId(), java.util.Map.of());
    }

    private JournalEntry postFor(CreditDebitNote note, LocalDate date) {
        Account salesReturns = accounting.requireAccount(AccountingService.ACC_SALES_RETURNS);
        Account feeIncome = accounting.requireAccount(AccountingService.ACC_FEE_INCOME);
        Account ar = accounting.requireAccount(AccountingService.ACC_AR);
        Account vat = accounting.requireAccount(AccountingService.ACC_VAT_PAYABLE);

        String description = note.getNoteType() == CreditDebitNote.NoteType.CREDIT
                ? "Ø¥Ø´Ø¹Ø§Ø± Ø®ØµÙ…" : "Ø¥Ø´Ø¹Ø§Ø± Ù…Ø¯ÙŠÙ†";
        List<AccountingService.Line> lines = new java.util.ArrayList<>();
        if (note.getNoteType() == CreditDebitNote.NoteType.CREDIT) {
            lines.add(AccountingService.Line.dr(note.getSubtotal(), salesReturns, description));
            lines.add(AccountingService.Line.cr(note.getTotal(), ar, description));
            if (note.getTaxAmount().signum() > 0) {
                lines.add(AccountingService.Line.dr(note.getTaxAmount(), vat, description));
            }
        } else {
            lines.add(AccountingService.Line.dr(note.getTotal(), ar, description));
            lines.add(AccountingService.Line.cr(note.getSubtotal(), feeIncome, description));
            if (note.getTaxAmount().signum() > 0) {
                lines.add(AccountingService.Line.cr(note.getTaxAmount(), vat, description));
            }
        }
        JournalEntry.Source source = note.getNoteType() == CreditDebitNote.NoteType.CREDIT
                ? JournalEntry.Source.CREDIT_NOTE : JournalEntry.Source.DEBIT_NOTE;
        return accounting.post(date, description, source, "CREDIT_DEBIT_NOTE", null,
                accounting.currentUser(), lines);
    }

    private CreditDebitNote load(UUID id) {
        return noteRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ø§Ù„Ø¥Ø´Ø¹Ø§Ø± ØºÙŠØ± Ù…ÙˆØ¬ÙˆØ¯"));
    }

    private com.kalima.backend.domain.entity.Person resolveClient(UUID clientId) {
        if (clientId == null) return null;
        return personRepository.findScopedById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ø§Ù„Ø¹Ù…ÙŠÙ„ ØºÙŠØ± Ù…ÙˆØ¬ÙˆØ¯"));
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}