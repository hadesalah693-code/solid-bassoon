package com.kalima.backend.accounting.repository;

import com.kalima.backend.accounting.entity.ClientAccount;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientAccountRepository extends JpaRepository<ClientAccount, UUID> {

    @EntityGraph(attributePaths = {"person", "account"})
    List<ClientAccount> findByCompany_IdOrderByPersonFullNameAsc(UUID companyId);

    @EntityGraph(attributePaths = {"person", "account"})
    Optional<ClientAccount> findByCompany_IdAndId(UUID companyId, UUID id);

    Optional<ClientAccount> findByCompany_IdAndPerson_Id(UUID companyId, UUID personId);

    @EntityGraph(attributePaths = {"person", "account"})
    @Query("""
            select ca from ClientAccount ca
            where ca.company.id = :companyId
              and (:search is null or :search = ''
                   or lower(ca.person.fullName) like lower(concat('%', :search, '%'))
                   or lower(coalesce(ca.person.email, '')) like lower(concat('%', :search, '%')))
            order by ca.person.fullName asc
            """)
    List<ClientAccount> search(
            @Param("companyId") UUID companyId,
            @Param("search") String search);

    /**
     * Per-client AR balance = opening + (debits - credits) on the client control
     * account. AR is an ASSET, so debit-positive means the client owes us.
     * The subquery is pinned to ca.account: the bank leg of a payment is also
     * client-tagged and would otherwise be counted as extra receivables.
     */
    @Query("""
            select ca.person.id,
                   ca.openingBalance + coalesce((
                       select sum(l.debit - l.credit)
                       from JournalLine l, JournalEntry e
                       where l.entry = e
                         and e.company.id = ca.company.id
                         and e.posted = true
                         and l.client.id = ca.person.id
                         and l.account.id = ca.account.id
                   ), 0)
            from ClientAccount ca
            where ca.company.id = :companyId
            """)
    List<Object[]> computeBalances(@Param("companyId") UUID companyId);
}
