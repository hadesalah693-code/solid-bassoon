package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.CreditDebitNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditDebitNoteRepository extends JpaRepository<CreditDebitNote, UUID> {

    Optional<CreditDebitNote> findByCompany_IdAndId(UUID companyId, UUID id);

    @Query("""
            SELECT n FROM CreditDebitNote n
            WHERE n.company.id = :companyId
              AND (:clientId IS NULL OR n.client.id = :clientId)
              AND (:noteType IS NULL OR n.noteType = :noteType)
              AND n.noteDate BETWEEN :from AND :to
            ORDER BY n.noteDate DESC, n.id
            """)
    List<CreditDebitNote> search(@Param("companyId") UUID companyId,
                                 @Param("clientId") UUID clientId,
                                 @Param("noteType") CreditDebitNote.NoteType noteType,
                                 @Param("from") LocalDate from,
                                 @Param("to") LocalDate to);

    /** Credit notes already issued against one invoice, to cap how much can be credited back. */
    @Query("""
            SELECT COALESCE(SUM(n.total), 0) FROM CreditDebitNote n
            WHERE n.invoice.id = :invoiceId AND n.noteType = :noteType
            """)
    java.math.BigDecimal sumByInvoiceAndType(@Param("invoiceId") UUID invoiceId,
                                            @Param("noteType") CreditDebitNote.NoteType noteType);
}