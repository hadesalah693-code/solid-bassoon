package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.Expense;
import com.kalima.backend.accounting.entity.Expense.Category;
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

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    @EntityGraph(attributePaths = {"account", "bankAccount", "client", "recordedBy", "journalEntry"})
    Optional<Expense> findByCompany_IdAndId(UUID companyId, UUID id);

    @EntityGraph(attributePaths = {"account", "bankAccount", "client", "recordedBy"})
    @Query("""
            select e from Expense e
            where e.company.id = :companyId
              and (:category is null or e.category = :category)
              and e.expenseDate >= :from
              and e.expenseDate <= :to
              and (:search is null or :search = ''
                   or lower(e.expenseNumber) like lower(concat('%', :search, '%'))
                   or lower(coalesce(e.description, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(e.payee, '')) like lower(concat('%', :search, '%')))
            order by e.expenseDate desc, e.createdAt desc
            """)
    Page<Expense> search(
            @Param("companyId") UUID companyId,
            @Param("category") Category category,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select e.category, coalesce(sum(e.total), 0)
            from Expense e
            where e.company.id = :companyId
              and e.expenseDate >= :from
              and e.expenseDate <= :to
            group by e.category
            """)
    List<Object[]> sumByCategory(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(e.total), 0), coalesce(sum(e.taxAmount), 0)
            from Expense e
            where e.company.id = :companyId
              and e.expenseDate >= :from
              and e.expenseDate <= :to
            """)
    List<Object[]> sumTotals(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    long countByCompany_IdAndExpenseDateBetween(UUID companyId, LocalDate from, LocalDate to);

    @Query("""
            select e from Expense e
            where e.company.id = :companyId
              and e.expenseDate >= :from
              and e.expenseDate <= :to
            order by e.expenseDate asc
            """)
    List<Expense> findRange(@Param("companyId") UUID companyId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);
}
