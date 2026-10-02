package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.*;
import com.kalima.backend.accounting.entity.*;
import com.kalima.backend.accounting.repository.*;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChartOfAccountsService {

    private final AccountRepository accountRepository;
    private final JournalLineService journalLineService;
    private final BankAccountRepository bankAccountRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final AccountingService accountingService;

    public ChartOfAccountsService(AccountRepository accountRepository,
                                  JournalLineService journalLineService,
                                  BankAccountRepository bankAccountRepository,
                                  ClientAccountRepository clientAccountRepository,
                                  AccountingService accountingService) {
        this.accountRepository = accountRepository;
        this.journalLineService = journalLineService;
        this.bankAccountRepository = bankAccountRepository;
        this.clientAccountRepository = clientAccountRepository;
        this.accountingService = accountingService;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return accountRepository.findByCompany_IdOrderByCodeAsc(TenantContext.getRequired())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByType(Account.Type type) {
        return accountRepository.findByCompany_IdAndTypeOrderByCodeAsc(TenantContext.getRequired(), type)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccountResponse create(AccountCreateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        String code = request.code().trim();
        if (accountRepository.existsByCompany_IdAndCode(tenantId, code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "كود الحساب مستخدم مسبقاً");
        }
        Account account = new Account();
        account.setCompany(accountingService.currentTenantReference());
        account.setCode(code);
        account.setNameAr(request.nameAr().trim());
        account.setNameEn(StringUtils.hasText(request.nameEn()) ? request.nameEn().trim() : null);
        account.setType(request.type());
        account.setSystem(false);
        account.setActive(true);
        if (StringUtils.hasText(request.description())) {
            account.setDescription(request.description().trim());
        }
        if (request.parentId() != null) {
            Account parent = accountRepository.findByCompany_IdAndId(tenantId, request.parentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب الأب غير موجود"));
            account.setParent(parent);
        }
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountUpdateRequest request) {
        UUID tenantId = TenantContext.getRequired();
        Account account = accountRepository.findByCompany_IdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الحساب غير موجود"));
        if (account.isSystem()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "لا يمكن تعديل حساب نظامي");
        }
        if (journalLineService.hasMovements(tenantId, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "لا يمكن تعديل حساب عليه حركات؛ أنشئ حساباً جديداً بدلاً من ذلك");
        }
        account.setNameAr(request.nameAr().trim());
        account.setNameEn(StringUtils.hasText(request.nameEn()) ? request.nameEn().trim() : null);
        if (StringUtils.hasText(request.description())) {
            account.setDescription(request.description().trim());
        }
        if (request.active() != null) {
            account.setActive(request.active());
        }
        if (request.parentId() != null && !request.parentId().equals(id)) {
            Account parent = accountRepository.findByCompany_IdAndId(tenantId, request.parentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب الأب غير موجود"));
            if (parent.getType() != account.getType()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "نوع الحساب الأب يجب أن يطابق نوع الحساب (" + account.getType() + ")");
            }
            if (isDescendantOf(account, parent)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "لا يمكن جعل الحساب تابعاً لنفسه أو لأحد أبنائه");
            }
            account.setParent(parent);
        } else {
            account.setParent(null);
        }
        return toResponse(accountRepository.save(account));
    }

    /** Guards against parent cycles (A -> B -> A), which the UI's self-check misses. */
    private boolean isDescendantOf(Account candidate, Account parent) {
        Account cursor = parent;
        int guard = 0;
        while (cursor != null && guard++ < 50) {
            if (cursor.getId().equals(candidate.getId())) return true;
            cursor = cursor.getParent();
        }
        return false;
    }

    @Transactional
    public void archive(UUID id) {
        UUID tenantId = TenantContext.getRequired();
        Account account = accountRepository.findByCompany_IdAndId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "الحساب غير موجود"));
        if (account.isSystem()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "لا يمكن أرشفة حساب نظامي");
        }
        if (journalLineService.hasMovements(tenantId, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "لا يمكن أرشفة حساب عليه حركات");
        }
        if (bankAccountRepository.findByCompany_IdOrderByNameArAsc(tenantId).stream()
                .anyMatch(b -> b.getAccount().getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب مرتبط بخزينة");
        }
        if (clientAccountRepository.findByCompany_IdOrderByPersonFullNameAsc(tenantId).stream()
                .anyMatch(c -> c.getAccount().getId().equals(id))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "الحساب مرتبط بحساب عميل");
        }
        account.setActive(false);
        accountRepository.save(account);
    }

    private AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(), a.getCode(), a.getNameAr(), a.getNameEn(), a.getType(),
                a.getParent() == null ? null : a.getParent().getId(),
                a.getParent() == null ? null : a.getParent().getCode(),
                a.isActive(), a.isSystem(), a.getDescription());
    }
}
