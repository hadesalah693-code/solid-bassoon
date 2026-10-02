package com.kalima.backend.invoice.repository;

import com.kalima.backend.invoice.entity.Invoice;
import com.kalima.backend.invoice.entity.InvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @EntityGraph(attributePaths = {"client", "recordedBy"})
    Optional<Invoice> findByCompany_IdAndId(UUID companyId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"client", "recordedBy", "caseFile"})
    @Query("SELECT i FROM Invoice i WHERE i.company.id = :companyId AND i.id = :id")
    Optional<Invoice> findForUpdateByCompanyIdAndId(
            @Param("companyId") UUID companyId, @Param("id") UUID id);

    @EntityGraph(attributePaths = {"client", "recordedBy", "caseFile"})
    @Query("""
            select i from Invoice i
            where i.company.id = :companyId
              and (:clientId is null or i.client.id = :clientId)
              and (:status is null or i.status = :status)
              and (:search is null or :search = ''
                   or lower(i.invoiceNumber) like lower(concat('%', :search, '%'))
                   or lower(coalesce(i.notes, '')) like lower(concat('%', :search, '%')))
            order by i.issueDate desc, i.createdAt desc
            """)
    Page<Invoice> search(
            @Param("companyId") UUID companyId,
            @Param("clientId") UUID clientId,
            @Param("status") InvoiceStatus status,
            @Param("search") String search,
            Pageable pageable);

    boolean existsByInvoiceNumber(String invoiceNumber);

    List<Invoice> findByCompany_Id(UUID companyId);

    /**
     * Outstanding (total - paid) per client, plus how many of those invoices are
     * already past their due date.
     */
    @Query("""
            select i.client.id,
                   i.client.fullName,
                   count(i),
                   coalesce(sum(i.total), 0),
                                   coalesce(sum((select coalesce(sum(p.amount), 0) from InvoicePayment p
                                           where p.invoice = i and p.paymentDate <= :asOf)), 0),
                                   coalesce(sum(case when i.total > (select coalesce(sum(p.amount), 0) from InvoicePayment p
                                           where p.invoice = i and p.paymentDate <= :asOf)
                                           then i.total - (select coalesce(sum(p.amount), 0) from InvoicePayment p
                                           where p.invoice = i and p.paymentDate <= :asOf) else 0 end), 0),
                                   sum(case when i.dueDate is not null and :asOf > i.dueDate
                                           and i.total > (select coalesce(sum(p.amount), 0) from InvoicePayment p
                                                   where p.invoice = i and p.paymentDate <= :asOf)
                                           then 1 else 0 end)
            from Invoice i
            where i.company.id = :companyId
                          and i.issueDate <= :asOf
              and i.status in :statuses
              and i.client is not null
            group by i.client.id, i.client.fullName
            order by coalesce(sum(i.total - i.amountPaid), 0) desc
            """)
    List<Object[]> arAging(
            @Param("companyId") UUID companyId,
            @Param("statuses") List<InvoiceStatus> statuses,
            @Param("asOf") LocalDate asOf);

    /**
     * AR aging buckets as a single aggregate query so the report never has to
     * load every invoice into memory.
     */
    @Query("""
            select case
                     when i.dueDate is null then 'UNDATED'
                                                                                 when :asOf <= i.dueDate then 'CURRENT'
                                                                                 when :asOf - i.dueDate <= 30 then 'D1_30'
                                                                                 when :asOf - i.dueDate <= 60 then 'D31_60'
                                                                                 when :asOf - i.dueDate <= 90 then 'D61_90'
                     else 'D90_PLUS' end,
                   count(i),
                                                                         coalesce(sum(case when i.total > (select coalesce(sum(p.amount), 0) from InvoicePayment p
                                                                                         where p.invoice = i and p.paymentDate <= :asOf)
                                                                                         then i.total - (select coalesce(sum(p.amount), 0) from InvoicePayment p
                                                                                         where p.invoice = i and p.paymentDate <= :asOf) else 0 end), 0)
            from Invoice i
            where i.company.id = :companyId
                                                        and i.issueDate <= :asOf
              and i.status in :statuses
              and i.client is not null
                                                        and i.total > (select coalesce(sum(p.amount), 0) from InvoicePayment p
                                                                        where p.invoice = i and p.paymentDate <= :asOf)
            group by case
                     when i.dueDate is null then 'UNDATED'
                                                                                 when :asOf <= i.dueDate then 'CURRENT'
                                                                                 when :asOf - i.dueDate <= 30 then 'D1_30'
                                                                                 when :asOf - i.dueDate <= 60 then 'D31_60'
                                                                                 when :asOf - i.dueDate <= 90 then 'D61_90'
                     else 'D90_PLUS' end
            """)
    List<Object[]> arAgingBuckets(
            @Param("companyId") UUID companyId,
            @Param("statuses") List<InvoiceStatus> statuses,
            @Param("asOf") LocalDate asOf);
}
