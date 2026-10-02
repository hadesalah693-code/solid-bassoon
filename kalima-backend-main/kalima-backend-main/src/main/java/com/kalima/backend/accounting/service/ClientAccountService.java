package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.ClientAccountRequest;
import com.kalima.backend.accounting.dto.ClientAccountResponse;
import com.kalima.backend.accounting.entity.ClientAccount;
import com.kalima.backend.accounting.repository.ClientAccountRepository;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.repository.InvoiceRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ClientAccountService {

    private static final List<InvoiceStatus> OPEN_STATUSES = List.of(
            InvoiceStatus.DRAFT, InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE);

    private final ClientAccountRepository clientAccountRepository;
    private final InvoiceRepository invoiceRepository;
    private final PersonRepository personRepository;
    private final AccountingService accounting;

    public ClientAccountService(ClientAccountRepository clientAccountRepository,
                                InvoiceRepository invoiceRepository,
                                PersonRepository personRepository,
                                AccountingService accounting) {
        this.clientAccountRepository = clientAccountRepository;
        this.invoiceRepository = invoiceRepository;
        this.personRepository = personRepository;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public List<ClientAccountResponse> list(String search) {
        UUID tenantId = TenantContext.getRequired();
        Map<UUID, BigDecimal> balances = new HashMap<>();
        for (Object[] row : clientAccountRepository.computeBalances(tenantId)) {
            balances.put((UUID) row[0], toDecimal(row[1]));
        }
        Map<UUID, BigDecimal> invoiced = new HashMap<>();
        Map<UUID, BigDecimal> paid = new HashMap<>();
        for (Invoice i : invoiceRepository.findByCompany_Id(tenantId)) {
            if (i.getClient() == null) continue;
            UUID cid = i.getClient().getId();
            invoiced.merge(cid, nvl(i.getTotal()), BigDecimal::add);
            paid.merge(cid, nvl(i.getAmountPaid()), BigDecimal::add);
        }

        String term = StringUtils.hasText(search) ? search.trim().toLowerCase() : null;
        return clientAccountRepository
                .search(tenantId, term)
                .stream()
                .map(ca -> {
                    UUID cid = ca.getPerson().getId();
                    BigDecimal inv = invoiced.getOrDefault(cid, BigDecimal.ZERO);
                    BigDecimal pay = paid.getOrDefault(cid, BigDecimal.ZERO);
                    return toResponse(ca, balances.getOrDefault(cid, ca.getOpeningBalance()), inv, pay);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientAccountResponse get(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        return withTotals(clientAccountRepository.findByCompany_IdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "حساب العميل غير موجود")));
    }

    @Transactional
    public ClientAccountResponse create(UUID personId, ClientAccountRequest request) {
        UUID tenantId = TenantContext.getRequired();
        if (clientAccountRepository.findByCompany_IdAndPerson_Id(tenantId, personId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "يوجد حساب محاسبي مرتبط بهذا العميل مسبقًا");
        }
        // Tenant-scoped lookup: a plain findById bypasses the Hibernate tenant filter
        // and would accept a person belonging to another company.
        Person person = accounting.requirePerson(personId);

        // `openingBalance` is a control-account offset, not a journal entry, so a
        // non-zero value would never reach the trial balance. Opening balances
        // belong in an OPENING journal entry.
        BigDecimal opening = nvl(request.openingBalance());
        if (opening.signum() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "الرصيد الافتتاحي غير مدعوم هنا؛ سجّله بقيد افتتاحي في دفتر اليومية");
        }

        ClientAccount ca = new ClientAccount();
        ca.setCompany(accounting.currentTenantReference());
        ca.setPerson(person);
        ca.setAccount(accounting.requireAccount(AccountingService.ACC_AR));
        ca.setOpeningBalance(opening);
        ca.setCreditLimit(nvl(request.creditLimit()));
        ca.setNotes(request.notes());
        return withTotals(clientAccountRepository.save(ca));
    }

    @Transactional
    public ClientAccountResponse update(UUID id, ClientAccountRequest request) {
        UUID tenantId = TenantContext.getRequired();
        ClientAccount ca = clientAccountRepository.findByCompany_IdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "حساب العميل غير موجود"));

        if (request.creditLimit() != null) ca.setCreditLimit(request.creditLimit());
        if (request.notes() != null) ca.setNotes(request.notes());
        return withTotals(clientAccountRepository.save(ca));
    }

    /**
     * Single source of truth for the response so create/update never report a
     * zeroed balance while the ledger already has movement for that client.
     */
    private ClientAccountResponse withTotals(ClientAccount ca) {
        UUID tenantId = TenantContext.getRequired();
        UUID personId = ca.getPerson().getId();

        BigDecimal balance = ca.getOpeningBalance();
        for (Object[] row : clientAccountRepository.computeBalances(tenantId)) {
            if (personId.equals(row[0])) {
                balance = toDecimal(row[1]);
                break;
            }
        }

        BigDecimal invoiced = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (Invoice i : invoiceRepository.findByCompany_Id(tenantId)) {
            if (i.getClient() == null || !personId.equals(i.getClient().getId())) continue;
            invoiced = invoiced.add(nvl(i.getTotal()));
            paid = paid.add(nvl(i.getAmountPaid()));
        }
        return toResponse(ca, balance, invoiced, paid);
    }

    private ClientAccountResponse toResponse(ClientAccount ca, BigDecimal balance,
                                             BigDecimal invoiced, BigDecimal paid) {
        return new ClientAccountResponse(
                ca.getId(), ca.getPerson().getId(), ca.getPerson().getFullName(),
                ca.getAccount().getId(), ca.getAccount().getCode(), ca.getOpeningBalance(),
                balance, ca.getCreditLimit(), invoiced, paid, nvl(invoiced).subtract(nvl(paid)), ca.getNotes());
    }

    private static BigDecimal toDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
