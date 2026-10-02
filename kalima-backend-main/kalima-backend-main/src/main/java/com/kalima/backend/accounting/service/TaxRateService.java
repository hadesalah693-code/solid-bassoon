package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.dto.TaxRateRequest;
import com.kalima.backend.accounting.dto.TaxRateResponse;
import com.kalima.backend.accounting.entity.TaxRate;
import com.kalima.backend.accounting.repository.TaxRateRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class TaxRateService {

    private final TaxRateRepository taxRateRepository;
    private final AccountingService accounting;

    public TaxRateService(TaxRateRepository taxRateRepository, AccountingService accounting) {
        this.taxRateRepository = taxRateRepository;
        this.accounting = accounting;
    }

    @Transactional(readOnly = true)
    public List<TaxRateResponse> list() {
        return taxRateRepository.findByCompany_IdOrderByRateDesc(TenantContext.getRequired())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public TaxRateResponse create(TaxRateRequest request) {
        TaxRate taxRate = new TaxRate();
        taxRate.setCompany(accounting.currentTenantReference());
        apply(taxRate, request);
        if (taxRate.isDefaultRate()) clearOtherDefaults(taxRate.getId());
        return toResponse(taxRateRepository.save(taxRate));
    }

    @Transactional
    public TaxRateResponse update(UUID id, TaxRateRequest request) {
        TaxRate taxRate = taxRateRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "معدل الضريبة غير موجود"));
        apply(taxRate, request);
        if (taxRate.isDefaultRate()) clearOtherDefaults(taxRate.getId());
        return toResponse(taxRateRepository.save(taxRate));
    }

    @Transactional
    public void delete(UUID id) {
        TaxRate taxRate = taxRateRepository.findByCompany_IdAndId(TenantContext.getRequired(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "معدل الضريبة غير موجود"));
        if (taxRate.isDefaultRate()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "لا يمكن حذف معدل الضريبة الافتراضي");
        }
        taxRateRepository.delete(taxRate);
    }

    private void apply(TaxRate taxRate, TaxRateRequest request) {
        taxRate.setNameAr(request.nameAr().trim());
        taxRate.setRate(request.rate());
        taxRate.setAccount(request.accountId() == null
                ? accounting.requireAccount(AccountingService.ACC_VAT_PAYABLE)
                : accounting.requireAccountById(request.accountId()));
        taxRate.setActive(request.active() == null || request.active());
        taxRate.setDefaultRate(Boolean.TRUE.equals(request.defaultRate()));
    }

    private void clearOtherDefaults(UUID keepId) {
        for (TaxRate other : taxRateRepository.findByCompany_IdOrderByRateDesc(TenantContext.getRequired())) {
            if (other.isDefaultRate() && !other.getId().equals(keepId)) {
                other.setDefaultRate(false);
                taxRateRepository.save(other);
            }
        }
    }

    private TaxRateResponse toResponse(TaxRate t) {
        return new TaxRateResponse(
                t.getId(), t.getNameAr(), t.getRate(),
                t.getAccount() == null ? null : t.getAccount().getId(),
                t.isActive(), t.isDefaultRate());
    }
}
