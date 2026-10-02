package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.BankTransfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface BankTransferRepository extends JpaRepository<BankTransfer, UUID> {
    @EntityGraph(attributePaths = {"sourceBankAccount", "destinationBankAccount", "journalEntry"})
    @Query("SELECT t FROM BankTransfer t WHERE t.company.id = :companyId ORDER BY t.transferDate DESC, t.createdAt DESC")
    List<BankTransfer> findByCompany(@Param("companyId") UUID companyId);
}