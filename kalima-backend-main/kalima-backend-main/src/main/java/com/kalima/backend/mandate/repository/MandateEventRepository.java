package com.kalima.backend.mandate.repository;

import com.kalima.backend.mandate.entity.MandateEvent;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MandateEventRepository extends JpaRepository<MandateEvent, java.util.UUID> {

    @EntityGraph(attributePaths = {"recordedBy"})
    List<MandateEvent> findByMandate_Id(java.util.UUID mandateId, Sort sort);
}
