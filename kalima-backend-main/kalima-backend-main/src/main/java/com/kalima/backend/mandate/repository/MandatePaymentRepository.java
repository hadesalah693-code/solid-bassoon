package com.kalima.backend.mandate.repository;

import com.kalima.backend.mandate.entity.MandatePayment;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MandatePaymentRepository extends JpaRepository<MandatePayment, UUID> {

    @EntityGraph(attributePaths = {"recordedBy", "mandate"})
    @Query("SELECT p FROM MandatePayment p WHERE p.id = :id")
    Optional<MandatePayment> findScopedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"recordedBy"})
    List<MandatePayment> findByMandate_Id(UUID mandateId, Sort sort);
}
