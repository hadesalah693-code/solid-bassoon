package com.kalima.backend.template.service;

import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.template.dto.GenerateDocumentRequest;
import com.kalima.backend.template.dto.GeneratedDocumentResponse;
import com.kalima.backend.template.dto.UpdateDocumentStatusRequest;
import com.kalima.backend.template.entity.GeneratedDocument;
import com.kalima.backend.template.entity.Template;
import com.kalima.backend.template.repository.GeneratedDocumentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a {@link Template} into a persisted {@link GeneratedDocument}.
 *
 * <p>Supported placeholders (only these — anything else is left untouched):
 * <ul>
 *   <li>{@code {{today}}} — current date in ISO format ({@code yyyy-MM-dd}).</li>
 *   <li>{@code {{client.name}}} — display name of the linked client; empty
 *       string when no client is provided or the client has no displayable
 *       name.</li>
 *   <li>{@code {{case.number}}} — {@code case_number} of the linked case;
 *       empty string when no case is provided.</li>
 *   <li>{@code {{company.name}}} — name of the current tenant company.</li>
 * </ul>
 *
 * <p>Tenant-isolation: all entity lookups go through {@code findScopedById}
 * (which honours the Hibernate {@code tenantFilter}), so a caller cannot
 * generate a document referencing another tenant's client or case even if
 * they guess valid UUIDs.
 *
 * <p>Newly created documents always start with status
 * {@link DomainEnums.DocumentStatus#DRAFT}.
 */
@Service
@Transactional
public class DocumentGenerationService {

    /**
     * Matches Mustache-style placeholders: {@code {{ key }}}. Whitespace
     * around the key is allowed and the key itself is captured in group 1.
     */
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    private static final DateTimeFormatter TODAY_FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE;

    private final TemplateService templateService;
    private final GeneratedDocumentRepository generatedDocumentRepository;
    private final PersonRepository personRepository;
    private final CaseFileRepository caseFileRepository;
    private final CompanyRepository companyRepository;

    public DocumentGenerationService(
            TemplateService templateService,
            GeneratedDocumentRepository generatedDocumentRepository,
            PersonRepository personRepository,
            CaseFileRepository caseFileRepository,
            CompanyRepository companyRepository) {
        this.templateService = templateService;
        this.generatedDocumentRepository = generatedDocumentRepository;
        this.personRepository = personRepository;
        this.caseFileRepository = caseFileRepository;
        this.companyRepository = companyRepository;
    }

    public GeneratedDocumentResponse generate(GenerateDocumentRequest request) {
        Template template = templateService.loadEntity(request.templateId());

        Person client = request.clientId() == null
                ? null
                : personRepository.findScopedById(request.clientId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Client not found: " + request.clientId()));

        CaseFile caseFile = request.caseId() == null
                ? null
                : caseFileRepository.findScopedById(request.caseId())
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Case not found: " + request.caseId()));

        Company tenant = currentTenantReference();
        String rendered = render(template.getContent(), client, caseFile, tenant);

        GeneratedDocument doc = new GeneratedDocument();
        doc.setCompany(tenant);
        doc.setTemplate(template);
        doc.setClient(client);
        doc.setCaseFile(caseFile);
        doc.setTitle(resolveTitle(request.title(), template));
        doc.setStatus(DomainEnums.DocumentStatus.DRAFT);
        doc.setRenderedContent(rendered);

        return toResponse(generatedDocumentRepository.save(doc));
    }

    @Transactional(readOnly = true)
    public GeneratedDocumentResponse findById(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<GeneratedDocumentResponse> list(DomainEnums.DocumentStatus status,
                                                UUID caseId,
                                                UUID clientId) {
        return generatedDocumentRepository.search(
                        status,
                        caseId,
                        clientId,
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public GeneratedDocumentResponse updateStatus(UUID id,
                                                  UpdateDocumentStatusRequest request) {
        GeneratedDocument doc = loadOrThrow(id);
        doc.setStatus(request.status());
        return toResponse(doc);
    }

    public void delete(UUID id) {
        GeneratedDocument doc = loadOrThrow(id);
        generatedDocumentRepository.delete(doc);
    }

    /**
     * Replaces every supported placeholder in {@code content} with the
     * resolved value. Unknown placeholders are returned unchanged so the
     * author can spot typos in the rendered output instead of silently
     * losing information.
     */
    private String render(String content, Person client, CaseFile caseFile, Company tenant) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        Map<String, String> values = buildValueMap(client, caseFile, tenant);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
        StringBuilder out = new StringBuilder(content.length());
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.containsKey(key)
                    ? values.get(key)
                    : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private Map<String, String> buildValueMap(Person client, CaseFile caseFile, Company tenant) {
        Map<String, String> values = new HashMap<>();
        values.put("today", LocalDate.now().format(TODAY_FORMATTER));
        values.put("client.name", clientDisplayName(client));
        values.put("case.number", caseFile == null ? "" : safe(caseFile.getCaseNumber()));
        values.put("company.name", tenant == null ? "" : safe(tenant.getName()));
        return values;
    }

    private static String clientDisplayName(Person client) {
        if (client == null) {
            return "";
        }
        if (client.getFullName() != null && !client.getFullName().isBlank()) {
            return client.getFullName().trim();
        }
        if (client.getCompanyName() != null && !client.getCompanyName().isBlank()) {
            return client.getCompanyName().trim();
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String resolveTitle(String requested, Template template) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        return template.getName();
    }

    private GeneratedDocument loadOrThrow(UUID id) {
        return generatedDocumentRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Generated document not found: " + id));
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private GeneratedDocumentResponse toResponse(GeneratedDocument doc) {
        Template template = doc.getTemplate();
        Person client = doc.getClient();
        CaseFile caseFile = doc.getCaseFile();
        return new GeneratedDocumentResponse(
                doc.getId(),
                template == null ? null : template.getId(),
                template == null ? null : template.getName(),
                template == null ? null : template.getType(),
                client == null ? null : client.getId(),
                client == null ? null : clientDisplayName(client),
                caseFile == null ? null : caseFile.getId(),
                caseFile == null ? null : caseFile.getCaseNumber(),
                doc.getTitle(),
                doc.getStatus(),
                doc.getRenderedContent(),
                doc.getCreatedAt(),
                doc.getUpdatedAt());
    }
}
