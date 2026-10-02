package com.kalima.backend.accounting.service;

import com.kalima.backend.accounting.entity.Account;
import com.kalima.backend.accounting.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class JournalLineService {

    private final AccountRepository accountRepository;

    public JournalLineService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * True when the account has ever been posted to. Used to block archiving
     * accounts that still have history.
     */
    @Transactional(readOnly = true)
    public boolean hasMovements(UUID companyId, UUID accountId) {
        return accountRepository.countMovements(companyId, accountId) > 0;
    }
}
