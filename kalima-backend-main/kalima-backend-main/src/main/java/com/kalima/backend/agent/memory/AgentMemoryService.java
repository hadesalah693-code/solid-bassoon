package com.kalima.backend.agent.memory;

import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AgentMemoryService {

    private final AgentMemoryRepository repository;
    private final CompanyRepository companyRepository;

    public AgentMemoryService(AgentMemoryRepository repository,
                              CompanyRepository companyRepository) {
        this.repository = repository;
        this.companyRepository = companyRepository;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> getLastActiveCaseId(UUID personId) {
        return repository.findByPersonAndKey(personId, AgentMemory.KEY_LAST_ACTIVE_CASE)
                .map(m -> {
                    try {
                        return UUID.fromString(m.getMemoryValue());
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                });
    }

    public void rememberLastActiveCase(Person person, UUID caseId) {
        if (caseId == null) {
            return;
        }
        upsert(person, AgentMemory.KEY_LAST_ACTIVE_CASE, caseId.toString());
    }

    public void upsert(Person person, String key, String value) {
        AgentMemory row = repository.findByPersonAndKey(person.getId(), key)
                .orElseGet(() -> {
                    AgentMemory m = new AgentMemory();
                    m.setCompany(companyRepository.getReferenceById(TenantContext.getRequired()));
                    m.setPerson(person);
                    m.setMemoryKey(key);
                    return m;
                });
        row.setMemoryValue(value);
        repository.save(row);
    }

    @Transactional(readOnly = true)
    public String buildContextSnippet(UUID personId) {
        StringBuilder sb = new StringBuilder();
        getLastActiveCaseId(personId).ifPresent(id ->
                sb.append("آخر قضية نشطة: ").append(id).append("\n"));
        repository.findAllForPerson(personId).stream()
                .filter(m -> !AgentMemory.KEY_LAST_ACTIVE_CASE.equals(m.getMemoryKey()))
                .limit(5)
                .forEach(m -> sb.append(m.getMemoryKey()).append(": ")
                        .append(truncate(m.getMemoryValue(), 200)).append("\n"));
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
