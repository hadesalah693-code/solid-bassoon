package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.TaxRate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxRateRepository extends JpaRepository<TaxRate, UUID> {

    @EntityGraph(attributePaths = "account")
    List<TaxRate> findByCompany_IdOrderByRateDesc(UUID companyId);

    @EntityGraph(attributePaths = "account")
    Optional<TaxRate> findByCompany_IdAndId(UUID companyId, UUID id);

    Optional<TaxRate> findFirstByCompany_IdAndDefaultRateTrueAndActiveTrue(UUID companyId);

    /** Output VAT = tax charged on issued invoices. */
    @Query("""
            select coalesce(sum(i.taxAmount), 0)
            from Invoice i
            where i.company.id = :companyId
              and i.status <> 'CANCELLED'
              and i.status <> 'DRAFT'
              and i.issueDate >= :from
              and i.issueDate <= :to
            """)
    BigDecimal sumOutputVat(
            @Param("companyId") UUID companyId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);

    @Query("""
            select coalesce(sum(i.taxAmount), 0), coalesce(sum(i.subtotal), 0), coalesce(sum(i.total), 0)
            from Invoice i
            where i.company.id = :companyId
              and i.status <> 'CANCELLED'
              and i.status <> 'DRAFT'
              and i.issueDate >= :from
              and i.issueDate <= :to
            """)
    List<Object[]> sumInvoiceAmounts(
            @Param("companyId") UUID companyId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to);
}
