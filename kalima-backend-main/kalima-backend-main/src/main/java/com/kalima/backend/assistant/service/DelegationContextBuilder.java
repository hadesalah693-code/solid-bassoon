package com.kalima.backend.assistant.service;

import com.kalima.backend.delegation.entity.Delegation;
import com.kalima.backend.delegation.entity.DelegationCaseLink;
import com.kalima.backend.delegation.entity.DelegationDocument;
import com.kalima.backend.delegation.repository.DelegationCaseLinkRepository;
import com.kalima.backend.delegation.repository.DelegationDocumentRepository;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Renders a tenant-scoped delegation snapshot for the Lawyer Assistant prompt.
 * Staff-only; tenant filter on repositories enforces company scope.
 */
@Service
@Transactional(readOnly = true)
public class DelegationContextBuilder {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_CONTEXT_CHARS = 3_000;

    private final DelegationRepository delegationRepository;
    private final DelegationCaseLinkRepository caseLinkRepository;
    private final DelegationDocumentRepository documentRepository;

    public DelegationContextBuilder(DelegationRepository delegationRepository,
                                    DelegationCaseLinkRepository caseLinkRepository,
                                    DelegationDocumentRepository documentRepository) {
        this.delegationRepository = delegationRepository;
        this.caseLinkRepository = caseLinkRepository;
        this.documentRepository = documentRepository;
    }

    public String buildContext(UUID delegationId, Person actor) {
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Delegation context is not available to clients.");
        }
        Delegation d = delegationRepository.findScopedById(delegationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Delegation not found: " + delegationId));

        StringBuilder sb = new StringBuilder(512);
        sb.append("=== ملف التوكيل ===\n");
        appendLine(sb, "معرّف التوكيل", d.getId().toString());
        appendLine(sb, "رقم التوكيل", d.getDelegationNumber());
        appendLine(sb, "العنوان", d.getTitle());
        appendLine(sb, "النوع", arabicType(d.getDelegationType()));
        appendLine(sb, "الحالة", arabicStatus(d.getStatus()));
        appendLine(sb, "بلد الإصدار", d.getIssuingCountry());
        if (d.getIssuedAt() != null) {
            appendLine(sb, "تاريخ الإصدار", d.getIssuedAt().format(DATE_FMT));
        }
        if (d.getExpiresAt() != null) {
            appendLine(sb, "تاريخ الانتهاء", d.getExpiresAt().format(DATE_FMT));
            long days = ChronoUnit.DAYS.between(LocalDate.now(), d.getExpiresAt());
            if (days >= 0) {
                appendLine(sb, "متبقٍ على الانتهاء", days + " يوم");
            } else {
                appendLine(sb, "منتهٍ منذ", Math.abs(days) + " يوم");
            }
        }
        if (d.getNotes() != null && !d.getNotes().isBlank()) {
            sb.append("ملاحظات: ").append(truncate(d.getNotes(), 400)).append("\n");
        }

        sb.append("\n--- الأطراف ---\n");
        Person client = d.getClient();
        if (client != null) {
            appendLine(sb, "الموكل", client.getFullName());
        }
        Person agent = d.getAgent();
        appendLine(sb, "الوكيل", agent == null ? "لم يُعيّن" : agent.getFullName());

        appendLinkedCases(sb, delegationId);
        appendDocuments(sb, delegationId);

        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_CHARS) {
            return result.substring(0, MAX_CONTEXT_CHARS) + "\n…[تم اقتطاع باقي السياق]";
        }
        return result;
    }

    /** First linked case id, if any — for grounding chat in related matter. */
    public UUID resolveLinkedCaseId(UUID delegationId) {
        List<DelegationCaseLink> links = caseLinkRepository.findByDelegationIdWithCase(delegationId);
        if (links.isEmpty()) {
            return null;
        }
        return links.get(0).getCaseFile().getId();
    }

    private void appendLinkedCases(StringBuilder sb, UUID delegationId) {
        List<DelegationCaseLink> links = caseLinkRepository.findByDelegationIdWithCase(delegationId);
        sb.append("\n--- القضايا المرتبطة (").append(links.size()).append(") ---\n");
        if (links.isEmpty()) {
            sb.append("لا توجد قضايا مرتبطة بهذا التوكيل.\n");
            return;
        }
        int i = 1;
        for (DelegationCaseLink link : links) {
            var cf = link.getCaseFile();
            sb.append(i++).append(") #")
                    .append(cf.getCaseNumber())
                    .append(" — ")
                    .append(cf.getTitle())
                    .append(" [")
                    .append(cf.getId())
                    .append("]\n");
        }
    }

    private void appendDocuments(StringBuilder sb, UUID delegationId) {
        List<DelegationDocument> docs = documentRepository.findByDelegation_Id(
                delegationId, Sort.by(Sort.Direction.DESC, "createdAt"));
        sb.append("\n--- مستندات التوكيل (").append(docs.size()).append(") ---\n");
        if (docs.isEmpty()) {
            sb.append("لا توجد مستندات مرفقة.\n");
            return;
        }
        int shown = Math.min(docs.size(), 8);
        for (int i = 0; i < shown; i++) {
            DelegationDocument doc = docs.get(i);
            sb.append(i + 1).append(") ")
                    .append(doc.getOriginalFilename() == null ? "—" : doc.getOriginalFilename());
            if (doc.getDescription() != null && !doc.getDescription().isBlank()) {
                sb.append(" — ").append(truncate(doc.getDescription(), 80));
            }
            sb.append("\n");
        }
        if (docs.size() > shown) {
            sb.append("…و ").append(docs.size() - shown).append(" مستند(ات) أخرى.\n");
        }
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(label).append(": ").append(value).append("\n");
    }

    private static void appendLine(StringBuilder sb, String label, long value) {
        sb.append(label).append(": ").append(value).append("\n");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String arabicStatus(DomainEnums.DelegationStatus status) {
        if (status == null) return "غير محددة";
        return switch (status) {
            case ACTIVE -> "ساري";
            case EXPIRED -> "منتهٍ";
            case CANCELLED -> "ملغى";
        };
    }

    private static String arabicType(DomainEnums.DelegationType type) {
        if (type == null) return "غير محدد";
        return switch (type) {
            case GENERAL -> "عام";
            case SPECIAL -> "خاص";
            case JUDICIAL -> "قضائي";
            case COMMERCIAL -> "تجاري";
            case ADMINISTRATIVE -> "إداري";
            case OFFICIAL -> "رسمي";
            case OTHER -> "أخرى";
        };
    }
}
