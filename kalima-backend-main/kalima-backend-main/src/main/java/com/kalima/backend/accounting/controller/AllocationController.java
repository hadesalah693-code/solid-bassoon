package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.AllocationCreateRequest;
import com.kalima.backend.accounting.dto.AllocationResponse;
import com.kalima.backend.accounting.service.PaymentAllocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Settling collected money against invoices, and taking it back off.
 *
 * <p>Separate from {@link ReceiptController} because allocating an existing
 * receipt is a different operation from issuing one, and it needs its own
 * permission surface: reading allocations is open to the same roles as receipts,
 * but changing them is accountant-only.
 */
@RestController
@RequestMapping("/api/accounting/allocations")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')")
public class AllocationController {

    private static final String ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final PaymentAllocationService service;

    public AllocationController(PaymentAllocationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    public List<AllocationResponse> list(
            @RequestParam(name = "receiptId", required = false) UUID receiptId,
            @RequestParam(name = "invoiceId", required = false) UUID invoiceId) {
        if (receiptId != null) return service.listByReceipt(receiptId);
        if (invoiceId != null) return service.listByInvoice(invoiceId);
        throw new IllegalArgumentException("receiptId أو invoiceId مطلوب للاستعلام عن التخصيصات");
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<List<AllocationResponse>> create(
            @Valid @RequestBody AllocationCreateRequest request) {
        List<AllocationResponse> created = service.allocate(request);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AllocationResponse> unallocate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.unallocate(id));
    }
}