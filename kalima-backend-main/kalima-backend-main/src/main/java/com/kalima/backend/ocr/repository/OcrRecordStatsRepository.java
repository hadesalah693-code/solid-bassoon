package com.kalima.backend.ocr.repository;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.ocr.dto.OcrActiveUserStat;
import com.kalima.backend.ocr.entity.OcrRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OcrRecordStatsRepository extends JpaRepository<OcrRecord, UUID> {

    @Query("SELECT COUNT(r) FROM OcrRecord r")
    long countAllScoped();

    @Query("SELECT COUNT(r) FROM OcrRecord r WHERE r.status = :status")
    long countByStatus(@Param("status") DomainEnums.OcrStatus status);

    @Query("""
            SELECT COUNT(r) FROM OcrRecord r
            WHERE r.status = :completedStatus
              AND r.processingCompletedAt >= :start
              AND r.processingCompletedAt < :end
            """)
    long countCompletedBetween(
            @Param("completedStatus") DomainEnums.OcrStatus completedStatus,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT COUNT(r) FROM OcrRecord r
            WHERE r.status IN :terminalStatuses
              AND r.processingCompletedAt >= :since
            """)
    long countTerminalSince(
            @Param("terminalStatuses") List<DomainEnums.OcrStatus> terminalStatuses,
            @Param("since") LocalDateTime since);

    @Query("""
            SELECT COUNT(r) FROM OcrRecord r
            WHERE r.status = :completedStatus
              AND r.processingCompletedAt >= :since
            """)
    long countSuccessSince(
            @Param("completedStatus") DomainEnums.OcrStatus completedStatus,
            @Param("since") LocalDateTime since);

    @Query(value = """
            SELECT p.id, p.full_name, COUNT(r.id)
            FROM ocr_records r
            JOIN persons p ON p.id = r.uploaded_by
            WHERE r.company_id = :companyId
            GROUP BY p.id, p.full_name
            ORDER BY COUNT(r.id) DESC, p.full_name ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findMostActiveUploadersNative(
            @Param("companyId") UUID companyId,
            @Param("limit") int limit);

    default List<OcrActiveUserStat> findMostActiveUploaders(UUID companyId, int limit) {
        return findMostActiveUploadersNative(companyId, limit).stream()
                .map(row -> new OcrActiveUserStat(
                        row[0] instanceof UUID u ? u : UUID.fromString(row[0].toString()),
                        row[1] == null ? "—" : row[1].toString(),
                        row[2] instanceof Number n ? n.longValue() : 0L))
                .toList();
    }
}
