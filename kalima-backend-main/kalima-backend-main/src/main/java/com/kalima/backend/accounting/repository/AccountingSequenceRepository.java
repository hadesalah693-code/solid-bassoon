package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.AccountingSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AccountingSequenceRepository extends JpaRepository<AccountingSequence, UUID> {

    @Modifying
    @Query("""
            update AccountingSequence s
               set s.lastValue = s.lastValue + 1
             where s.company.id = :companyId
               and s.scope = :scope
               and s.year = :year
            """)
    int increment(@Param("companyId") UUID companyId,
                  @Param("scope") AccountingSequence.Scope scope,
                  @Param("year") int year);

    @Query("""
            select coalesce(max(s.lastValue), 0)
            from AccountingSequence s
            where s.company.id = :companyId
              and s.scope = :scope
              and s.year = :year
            """)
    long currentValue(@Param("companyId") UUID companyId,
                      @Param("scope") AccountingSequence.Scope scope,
                      @Param("year") int year);
}
