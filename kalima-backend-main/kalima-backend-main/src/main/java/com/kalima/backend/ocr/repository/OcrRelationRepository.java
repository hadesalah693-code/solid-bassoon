package com.kalima.backend.ocr.repository;

import com.kalima.backend.ocr.entity.OcrRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OcrRelationRepository extends JpaRepository<OcrRelation, UUID> {

    List<OcrRelation> findByOcrRecordId(UUID ocrRecordId);

    @Modifying
    @Query("DELETE FROM OcrRelation rel WHERE rel.ocrRecord.id = :ocrRecordId")
    void deleteByOcrRecordId(@Param("ocrRecordId") UUID ocrRecordId);
}
