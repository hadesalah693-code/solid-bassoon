package com.kalima.backend.invoice.controller;

import com.kalima.backend.invoice.dto.InvoiceCreateRequest;
import com.kalima.backend.invoice.dto.InvoiceListItemResponse;
import com.kalima.backend.invoice.dto.InvoicePaymentRequest;
import com.kalima.backend.invoice.dto.InvoiceResponse;
import com.kalima.backend.invoice.dto.InvoiceStatisticsResponse;
import com.kalima.backend.invoice.dto.InvoiceStatusUpdateRequest;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import com.kalima.backend.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public ResponseEntity<InvoiceResponse> create(
            @Valid @RequestBody InvoiceCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        InvoiceResponse created = invoiceService.create(request);
        URI location = uriBuilder.path("/api/invoices/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public InvoiceStatisticsResponse statistics() {
        return invoiceService.statistics();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<InvoiceListItemResponse> list(
            @RequestParam(name = "clientId", required = false) UUID clientId,
            @RequestParam(name = "status", required = false) InvoiceStatus status,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return invoiceService.list(clientId, status, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public InvoiceResponse getById(@PathVariable UUID id) {
        return invoiceService.get(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public InvoiceResponse changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody InvoiceStatusUpdateRequest request) {
        return invoiceService.updateStatus(id, request);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public InvoiceResponse recordPayment(
            @PathVariable UUID id,
            @Valid @RequestBody InvoicePaymentRequest request) {
        return invoiceService.recordPayment(id, request);
    }
}
