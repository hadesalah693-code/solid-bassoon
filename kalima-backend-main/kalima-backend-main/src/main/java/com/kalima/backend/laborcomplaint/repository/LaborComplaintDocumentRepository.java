package com.kalima.backend.laborcomplaint.repository;

import com.kalima.backend.laborcomplaint.entity.LaborComplaintDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LaborComplaintDocumentRepository extends JpaRepository<LaborComplaintDocument, UUID> {

    List<LaborComplaintDocument> findByLaborComplaint_Id(UUID laborComplaintId, Sort sort);

    Optional<LaborComplaintDocument> findByIdAndLaborComplaint_Id(UUID id, UUID laborComplaintId);
}
