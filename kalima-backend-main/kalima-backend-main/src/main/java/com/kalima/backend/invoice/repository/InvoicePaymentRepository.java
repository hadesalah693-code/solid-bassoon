package com.kalima.backend.invoice.repository;

import com.kalima.backend.invoice.entity.InvoicePayment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {

    @EntityGraph(attributePaths = {"bankAccount", "recordedBy", "journalEntry"})
    List<InvoicePayment> findByInvoice_IdOrderByPaymentDateAsc(UUID invoiceId);

        @EntityGraph(attributePaths = {"invoice", "journalEntry"})
        List<InvoicePayment> findByCompany_IdAndReceipt_Id(UUID companyId, UUID receiptId);

    @Query("""
            select coalesce(sum(p.amount), 0)
            from InvoicePayment p
            where p.invoice.id = :invoiceId
            """)
    BigDecimal sumByInvoice(@Param("invoiceId") UUID invoiceId);

    @Query("""
            select p from InvoicePayment p
            where p.company.id = :companyId
            order by p.paymentDate desc
            """)
    @EntityGraph(attributePaths = {"invoice", "bankAccount", "recordedBy"})
    List<InvoicePayment> findByCompany_OrderByPaymentDateDesc(@Param("companyId") UUID companyId);
}
