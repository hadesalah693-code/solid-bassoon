package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    @EntityGraph(attributePaths = {"lines", "lines.account", "createdBy"})
    Optional<JournalEntry> findByCompany_IdAndId(UUID companyId, UUID id);

    boolean existsByCompany_IdAndReferenceTypeAndReferenceId(
            UUID companyId, String referenceType, UUID referenceId);

    /** Guards the single-shot opening-balance entry for a company. */
    boolean existsByCompany_IdAndSource(UUID companyId, JournalEntry.Source source);

    /**
     * The revenue-recognition entry linked to a source document, used to
     * cross-link invoices in the UI. The first match is the original posting;
     * any later entry is a manual reversal.
     */
    @EntityGraph(attributePaths = {"createdBy"})
    @Query("""
            select e from JournalEntry e
            where e.company.id = :companyId
              and e.referenceType = :referenceType
              and e.referenceId = :referenceId
            order by e.entryDate asc, e.createdAt asc
            """)
    List<JournalEntry> findByReference(
            @Param("companyId") UUID companyId,
            @Param("referenceType") String referenceType,
            @Param("referenceId") UUID referenceId);

    Optional<JournalEntry> findByCompany_IdAndEntryNumber(UUID companyId, String entryNumber);

    @EntityGraph(attributePaths = {"createdBy"})
    @Query("""
            select e from JournalEntry e
            where e.company.id = :companyId
              and e.entryDate >= :from
              and e.entryDate <= :to
              and (:source is null or e.source = :source)
              and (:search is null or :search = ''
                   or lower(e.entryNumber) like lower(concat('%', :search, '%'))
                   or lower(coalesce(e.description, '')) like lower(concat('%', :search, '%')))
            order by e.entryDate desc, e.entryNumber desc
            """)
    Page<JournalEntry> search(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("source") JournalEntry.Source source,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            select e from JournalEntry e
            where e.company.id = :companyId
              and e.entryDate >= :from
              and e.entryDate <= :to
              and e.posted = true
            order by e.entryDate asc, e.entryNumber asc
            """)
    @EntityGraph(attributePaths = {"lines", "lines.account"})
    List<JournalEntry> findPostedRange(
            @Param("companyId") UUID companyId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @EntityGraph(attributePaths = {"lines", "lines.account", "lines.client"})
    @Query("""
            select e from JournalEntry e
            left join fetch e.lines l
            left join fetch l.client
            where e.company.id = :companyId
              and e.posted = true
              and e.entryDate >= :from
              and e.entryDate <= :to
              and l.client.id = :clientId
            order by e.entryDate asc, e.entryNumber asc
            """)
    List<JournalEntry> findClientStatement(
            @Param("companyId") UUID companyId,
            @Param("clientId") UUID clientId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    long countByCompany_IdAndPostedTrueAndEntryDateBetween(
            UUID companyId, LocalDate from, LocalDate to);
}
