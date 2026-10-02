package com.kalima.backend.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kalima.backend.agent.config.AgentProperties;
import com.kalima.backend.agent.safety.AgentSafetyService;
import com.kalima.backend.agent.tools.AgentToolContext;
import com.kalima.backend.agent.tools.AgentToolRegistry;
import com.kalima.backend.agent.tools.AgentToolResult;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class AgentPendingActionService {

    private final AgentPendingActionRepository repository;
    private final CompanyRepository companyRepository;
    private final AgentToolRegistry toolRegistry;
    private final AgentSafetyService safetyService;
    private final AgentProperties properties;
    private final ObjectMapper objectMapper;

    public AgentPendingActionService(AgentPendingActionRepository repository,
                                     CompanyRepository companyRepository,
                                     AgentToolRegistry toolRegistry,
                                     AgentSafetyService safetyService,
                                     AgentProperties properties,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.toolRegistry = toolRegistry;
        this.safetyService = safetyService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public AgentPendingAction create(
            Person person,
            String toolName,
            Map<String, Object> params,
            String summary,
            double confidence) {
        String normalized = normalizeTool(toolName);
        if (safetyService.isToolBlocked(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This action is not permitted.");
        }
        if (!safetyService.isRegisteredTool(normalized, toolRegistry.toolNames())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown agent tool: " + toolName);
        }
        if (!safetyService.requiresConfirmation(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tool does not require confirmation: " + toolName);
        }
        if (summary == null || summary.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Confirmation summary is required.");
        }
        AgentPendingAction row = new AgentPendingAction();
        row.setCompany(companyRepository.getReferenceById(TenantContext.getRequired()));
        row.setPerson(person);
        row.setToolName(normalized);
        row.setToolParams(writeParams(params));
        row.setSummary(summary.trim());
        row.setConfidence(confidence);
        row.setExpiresAt(LocalDateTime.now().plusMinutes(properties.getPendingActionTtlMinutes()));
        return repository.save(row);
    }

    public AgentToolResult executeConfirmed(Person person, UUID pendingId) {
        AgentPendingAction pending = repository.findScopedForPerson(pendingId, person.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pending action not found"));
        if (!pending.isOpen()) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "Pending action expired or already handled.");
        }
        String toolName = pending.getToolName();
        if (safetyService.isToolBlocked(toolName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This action is not permitted.");
        }
        if (!safetyService.requiresConfirmation(toolName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pending action is no longer valid.");
        }
        if (!safetyService.isRegisteredTool(toolName, toolRegistry.toolNames())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown agent tool.");
        }
        Map<String, Object> params = readParams(pending.getToolParams());
        AgentToolContext ctx = new AgentToolContext(person, TenantContext.getRequired(), false);
        AgentToolResult result = toolRegistry.invoke(toolName, ctx, params);
        if (result.success()) {
            pending.setExecutedAt(LocalDateTime.now());
        }
        return result;
    }

    private static String normalizeTool(String toolName) {
        return toolName == null ? "" : toolName.trim().toLowerCase();
    }

    private String writeParams(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialize tool params");
        }
    }

    private Map<String, Object> readParams(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid pending action params");
        }
    }
}
