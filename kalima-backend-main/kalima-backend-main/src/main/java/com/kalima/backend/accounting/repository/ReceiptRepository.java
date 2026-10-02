package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    @EntityGraph(attributePaths = {"client", "bankAccount", "recordedBy", "journalEntry"})
    Optional<Receipt> findByCompany_IdAndId(UUID companyId, UUID id);

    @EntityGraph(attributePaths = {"client", "bankAccount", "recordedBy"})
    @Query("""
            select r from Receipt r
            where r.company.id = :companyId
              and (:clientId is null or r.client.id = :clientId)
              and r.receiptDate >= :from
              and r.receiptDate <= :to
              and (:search is null or :search = ''
                   or lower(r.receiptNumber) like lower(concat('%', :search, '%'))
                   or lower(coalesce(r.reference, '')) like lower(concat('%', :search, '%')))
            order by r.receiptDate desc, r.createdAt desc
            """)
    Page<Receipt> search(
            @Param("companyId") UUID companyId,
            @Param("clientId") UUID clientId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select coalesce(sum(r.amount), 0)
            from Receipt r
            where r.company.id = :companyId
              and r.receiptDate >= :from
              and r.receiptDate <= :to
            """)
    BigDecimal sumAmount(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    long countByCompany_IdAndReceiptDateBetween(UUID companyId, LocalDate from, LocalDate to);

    @Query("""
            select r from Receipt r
            where r.company.id = :companyId
              and r.receiptDate >= :from
              and r.receiptDate <= :to
            order by r.receiptDate asc
            """)
    List<Receipt> findRange(@Param("companyId") UUID companyId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);
}
