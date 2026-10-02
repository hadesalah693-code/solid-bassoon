package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByCompany_IdAndId(UUID companyId, UUID id);

    Optional<Vendor> findByCompany_IdAndName(UUID companyId, String name);

    @Query("""
            SELECT v FROM Vendor v
            WHERE v.company.id = :companyId
              AND (:activeOnly = FALSE OR v.active = TRUE)
              AND (:term IS NULL OR LOWER(v.name) LIKE LOWER(CONCAT('%', :term, '%'))
                   OR LOWER(COALESCE(v.nameAr, '')) LIKE LOWER(CONCAT('%', :term, '%')))
            ORDER BY v.name
            """)
    List<Vendor> search(@Param("companyId") UUID companyId,
                        @Param("activeOnly") boolean activeOnly,
                        @Param("term") String term);
}