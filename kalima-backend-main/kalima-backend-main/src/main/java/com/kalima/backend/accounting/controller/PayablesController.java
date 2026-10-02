package com.kalima.backend.accounting.controller;

import com.kalima.backend.accounting.dto.VendorBillCreateRequest;
import com.kalima.backend.accounting.dto.VendorBillResponse;
import com.kalima.backend.accounting.dto.VendorCreateRequest;
import com.kalima.backend.accounting.dto.VendorPaymentCreateRequest;
import com.kalima.backend.accounting.dto.VendorPaymentResponse;
import com.kalima.backend.accounting.dto.VendorResponse;
import com.kalima.backend.accounting.entity.VendorBill;
import com.kalima.backend.accounting.service.PayablesService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vendors and payables.
 *
 * <p>Split by resource so each one carries its own permission: reading the vendor
 * list is harmless, while recording a bill or a payment posts to the ledger and is
 * limited to roles that can already move money.
 */
@RestController
@RequestMapping("/api/accounting/vendors")
public class PayablesController {

    private static final String READ_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT','SENIOR_LAWYER')";
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN','ACCOUNTANT')";

    private final PayablesService service;

    public PayablesController(PayablesService service) {
        this.service = service;
    }

    // ---------------------------------------------------------------
    // Vendors
    // ---------------------------------------------------------------
    @GetMapping
    @PreAuthorize(READ_ROLES)
    public List<VendorResponse> listVendors(
            @RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly,
            @RequestParam(name = "q", required = false) String term) {
        return service.listVendors(activeOnly, term);
    }

    @GetMapping("/{id}")
    @PreAuthorize(READ_ROLES)
    public VendorResponse getVendor(@PathVariable UUID id) {
        return service.getVendor(id);
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VendorResponse> createVendor(
            @Valid @RequestBody VendorCreateRequest request) {
        VendorResponse created = service.createVendor(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public VendorResponse updateVendor(@PathVariable UUID id,
                                       @Valid @RequestBody VendorCreateRequest request) {
        return service.updateVendor(id, request);
    }

    /** Deactivates rather than deletes, so historical bills keep their reference. */
    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> deactivateVendor(@PathVariable UUID id) {
        service.deactivateVendor(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Bills
    // ---------------------------------------------------------------
    @GetMapping("/bills")
    @PreAuthorize(READ_ROLES)
    public List<VendorBillResponse> listBills(
            @RequestParam(name = "vendorId", required = false) UUID vendorId,
            @RequestParam(name = "status", required = false) VendorBill.Status status,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return service.listBills(vendorId, status, from, to);
    }

    @PostMapping("/bills")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VendorBillResponse> createBill(
            @Valid @RequestBody VendorBillCreateRequest request) {
        VendorBillResponse created = service.createBill(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/bills/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> cancelBill(@PathVariable UUID id) {
        service.cancelBill(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // Payments
    // ---------------------------------------------------------------
    @GetMapping("/payments")
    @PreAuthorize(READ_ROLES)
    public List<VendorPaymentResponse> listPayments(
            @RequestParam(name = "billId", required = false) UUID billId,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return service.listPayments(billId, from, to);
    }

    @PostMapping("/payments")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VendorPaymentResponse> payBill(
            @Valid @RequestBody VendorPaymentCreateRequest request) {
        VendorPaymentResponse created = service.payBill(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/payments/{id}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<Void> voidPayment(@PathVariable UUID id) {
        service.voidPayment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/payables-summary")
    @PreAuthorize(READ_ROLES)
    public PayablesService.PayablesSummary payablesSummary(
            @RequestParam(name = "on", required = false) LocalDate on) {
        return service.summary(on);
    }
}