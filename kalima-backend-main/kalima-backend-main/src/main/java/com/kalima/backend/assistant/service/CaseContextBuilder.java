package com.kalima.backend.assistant.service;

import com.kalima.backend.casedocument.entity.CaseDocument;
import com.kalima.backend.casedocument.repository.CaseDocumentRepository;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Loads a tenant-scoped, access-checked snapshot of a single case and renders
 * it as Arabic text suitable for injection into the Lawyer Assistant prompt.
 *
 * <p><b>Security:</b> {@link #buildContext(UUID, Person)} delegates to
 * {@link CaseAccessService} — the same matrix as messaging and chatbot.
 * If the actor cannot read the case, this returns a {@code 404} so the
 * existence of the case isn't leaked.
 *
 * <p>Only safe metadata is included: case fields, hearings (most recent
 * window), document counts/categories, and the next upcoming hearing. Raw
 * document content and private message bodies are intentionally excluded.
 */
@Service
@Transactional(readOnly = true)
public class CaseContextBuilder {

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Hearings window included in the snapshot. */
    private static final int RECENT_HEARINGS_LIMIT = 5;

    /** Hard upper bound on the rendered context length sent to Gemini. */
    private static final int MAX_CONTEXT_CHARS = 4_000;

    private final CaseFileRepository caseFileRepository;
    private final HearingRepository hearingRepository;
    private final CaseDocumentRepository caseDocumentRepository;
    private final CaseAccessService caseAccessService;

    public CaseContextBuilder(CaseFileRepository caseFileRepository,
                              HearingRepository hearingRepository,
                              CaseDocumentRepository caseDocumentRepository,
                              CaseAccessService caseAccessService) {
        this.caseFileRepository = caseFileRepository;
        this.hearingRepository = hearingRepository;
        this.caseDocumentRepository = caseDocumentRepository;
        this.caseAccessService = caseAccessService;
    }

    /**
     * Returns an Arabic, human-readable snapshot for the given case, or throws
     * 404 when the actor has no access. The snapshot is bounded and safe to
     * embed in an LLM prompt.
     */
    public String buildContext(UUID caseId, Person actor) {
        CaseFile caseFile = caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
        caseAccessService.requireReadableCase(caseFile, actor);

        StringBuilder sb = new StringBuilder(1_024);
        sb.append("=== ملف القضية ===\n");
        appendLine(sb, "رقم القضية الداخلي", caseFile.getCaseNumber());
        appendLine(sb, "رقم القضية بالمحكمة", caseFile.getCourtCaseNumber());
        appendLine(sb, "العنوان", caseFile.getTitle());
        appendLine(sb, "النوع", caseFile.getCaseType());
        appendLine(sb, "المحكمة", caseFile.getCourtName());
        appendLine(sb, "الدائرة", caseFile.getCourtCircuit());
        appendLine(sb, "المرحلة", caseFile.getProcedureStage());
        appendLine(sb, "الحالة", arabicStatus(caseFile.getStatus()));
        if (caseFile.getStartDate() != null) {
            appendLine(sb, "تاريخ البدء", caseFile.getStartDate().format(DATE_FMT));
        }
        if (caseFile.getCloseDate() != null) {
            appendLine(sb, "تاريخ الإغلاق", caseFile.getCloseDate().format(DATE_FMT));
        }
        if (caseFile.getArchivedAt() != null) {
            appendLine(sb, "مؤرشفة منذ",
                    caseFile.getArchivedAt().format(DATE_TIME_FMT));
        }
        if (caseFile.getDescription() != null
                && !caseFile.getDescription().isBlank()) {
            sb.append("الوصف: ")
                    .append(truncate(caseFile.getDescription(), 600))
                    .append("\n");
        }

        sb.append("\n--- الأطراف ---\n");
        Person client = caseFile.getClient();
        if (client != null) {
            appendLine(sb, "العميل", client.getFullName());
        }
        Person lawyer = caseFile.getAssignedLawyer();
        appendLine(sb, "المحامي المسؤول",
                lawyer == null ? "لم يُعيّن بعد" : lawyer.getFullName());

        appendHearings(sb, caseFile.getId());
        appendDocuments(sb, caseFile.getId());

        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_CHARS) {
            return result.substring(0, MAX_CONTEXT_CHARS)
                    + "\n…[تم اقتطاع باقي السياق لطوله]";
        }
        return result;
    }

    private void appendHearings(StringBuilder sb, UUID caseId) {
        Page<Hearing> recent = hearingRepository.findByCaseIdForList(
                caseId,
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, RECENT_HEARINGS_LIMIT,
                        Sort.by(Sort.Direction.DESC, "hearingDate")));
        sb.append("\n--- أحدث ").append(recent.getContent().size())
                .append(" جلسات (الأحدث أولاً) ---\n");
        if (recent.getContent().isEmpty()) {
            sb.append("لا توجد جلسات مسجّلة على هذه القضية.\n");
        } else {
            int i = 1;
            for (Hearing h : recent.getContent()) {
                sb.append(i++).append(") ");
                if (h.getHearingDate() != null) {
                    sb.append(h.getHearingDate().format(DATE_TIME_FMT));
                }
                if (h.getCourtName() != null && !h.getCourtName().isBlank()) {
                    sb.append(" — ").append(h.getCourtName());
                }
                if (h.getChamber() != null && !h.getChamber().isBlank()) {
                    sb.append(" / ").append(h.getChamber());
                }
                if (h.getResult() != null && !h.getResult().isBlank()) {
                    sb.append(" — النتيجة: ").append(truncate(h.getResult(), 200));
                }
                if (h.getNextHearingDate() != null) {
                    sb.append(" — التالية: ")
                            .append(h.getNextHearingDate().format(DATE_TIME_FMT));
                }
                sb.append("\n");
            }
        }

        Page<Hearing> upcomingPage = hearingRepository.findByCaseIdForList(
                caseId,
                LocalDateTime.now(),
                null,
                null,
                null,
                null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "hearingDate")));
        LocalDateTime now = LocalDateTime.now();
        Hearing nextUpcoming = upcomingPage.getContent().stream()
                .filter(h -> h.getHearingDate() != null
                        && !h.getHearingDate().isBefore(now))
                .findFirst()
                .orElse(null);
        if (nextUpcoming != null) {
            sb.append("الجلسة القادمة: ")
                    .append(nextUpcoming.getHearingDate().format(DATE_TIME_FMT));
            if (nextUpcoming.getCourtName() != null) {
                sb.append(" — ").append(nextUpcoming.getCourtName());
            }
            sb.append("\n");
        } else {
            sb.append("الجلسة القادمة: لا توجد جلسات قادمة مسجّلة.\n");
        }
    }

    private void appendDocuments(StringBuilder sb, UUID caseId) {
        List<CaseDocument> docs = caseDocumentRepository.findByCaseId(
                caseId, null, Sort.by(Sort.Direction.DESC, "createdAt"));
        sb.append("\n--- المستندات (").append(docs.size()).append(") ---\n");
        if (docs.isEmpty()) {
            sb.append("لا توجد مستندات مرفقة.\n");
            return;
        }
        int shown = Math.min(docs.size(), 10);
        for (int i = 0; i < shown; i++) {
            CaseDocument d = docs.get(i);
            sb.append(i + 1).append(") ")
                    .append(d.getOriginalFilename() == null
                            ? "—" : d.getOriginalFilename())
                    .append(" — ").append(arabicCategory(d.getCategory()));
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                sb.append(" — ").append(truncate(d.getDescription(), 120));
            }
            sb.append("\n");
        }
        if (docs.size() > shown) {
            sb.append("…و ").append(docs.size() - shown)
                    .append(" مستند(ات) أخرى.\n");
        }
    }

    /* ─── small render helpers ─── */

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(label).append(": ").append(value).append("\n");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String arabicStatus(DomainEnums.CaseStatus status) {
        if (status == null) return "غير محددة";
        return switch (status) {
            case NEW -> "جديدة";
            case IN_PROGRESS -> "قيد التنفيذ";
            case ON_HOLD -> "معلّقة";
            case CLOSED -> "مغلقة";
            case ARCHIVED -> "مؤرشفة";
        };
    }

    private static String arabicCategory(DomainEnums.CaseDocumentCategory c) {
        if (c == null) return "أخرى";
        return switch (c) {
            case POWER_OF_ATTORNEY -> "توكيل";
            case CONTRACT -> "عقد";
            case COURT_FILING -> "إيداع/مذكرة محكمة";
            case JUDGMENT -> "حكم";
            case EVIDENCE -> "دليل/إثبات";
            case RECEIPT -> "إيصال";
            case OTHER -> "أخرى";
        };
    }
}
