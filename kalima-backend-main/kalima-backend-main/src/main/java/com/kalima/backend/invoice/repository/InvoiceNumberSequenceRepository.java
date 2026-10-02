package com.kalima.backend.invoice.repository;

import com.kalima.backend.invoice.entity.InvoiceNumberSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceNumberSequenceRepository extends JpaRepository<InvoiceNumberSequence, UUID> {

    Optional<InvoiceNumberSequence> findByCompanyIdAndYear(UUID companyId, int year);
}
