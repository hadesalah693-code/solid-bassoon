package com.kalima.backend.assistant.service;

import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.entity.DisputeTimelineEvent;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.dispute.repository.DisputeTimelineEventRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Renders a tenant-scoped dispute snapshot for the Lawyer Assistant prompt.
 */
@Service
@Transactional(readOnly = true)
public class DisputeContextBuilder {

    private static final int MAX_CONTEXT_CHARS = 3_500;
    private static final int TIMELINE_LIMIT = 6;

    private final DisputeRepository disputeRepository;
    private final DisputeTimelineEventRepository timelineRepository;

    public DisputeContextBuilder(DisputeRepository disputeRepository,
                                 DisputeTimelineEventRepository timelineRepository) {
        this.disputeRepository = disputeRepository;
        this.timelineRepository = timelineRepository;
    }

    public String buildContext(UUID disputeId, Person actor) {
        if (actor.getSystemRole() == DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Dispute context is not available to clients.");
        }
        Dispute d = disputeRepository.findScopedById(disputeId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Dispute not found: " + disputeId));

        StringBuilder sb = new StringBuilder(512);
        sb.append("=== ملف النزاع ===\n");
        appendLine(sb, "معرّف النزاع", d.getId().toString());
        appendLine(sb, "رقم النزاع", d.getDisputeNumber());
        appendLine(sb, "العنوان", d.getTitle());
        appendLine(sb, "الخصم", d.getOpponentName());
        appendLine(sb, "النوع", arabicType(d.getDisputeType()));
        appendLine(sb, "الحالة", arabicStatus(d.getStatus()));
        appendLine(sb, "المرحلة الحالية", arabicStep(d.getCurrentStep()));
        if (d.getLegalPath() != null) {
            appendLine(sb, "المسار القانوني", d.getLegalPath().name());
        }
        if (d.getClaimAmount() != null) {
            appendLine(sb, "قيمة المطالبة", d.getClaimAmount().toPlainString());
        }
        appendLine(sb, "مستوى المخاطر", arabicRisk(d.getRiskLevel()));
        if (d.getSuccessProbability() != null) {
            appendLine(sb, "احتمال النجاح %", String.valueOf(d.getSuccessProbability()));
        }
        if (d.getDescription() != null && !d.getDescription().isBlank()) {
            sb.append("الوصف: ").append(truncate(d.getDescription(), 500)).append("\n");
        }

        sb.append("\n--- الأطراف ---\n");
        Person client = d.getClient();
        if (client != null) {
            appendLine(sb, "العميل", client.getFullName());
        }
        Person lawyer = d.getAssignedLawyer();
        appendLine(sb, "المحامي المسؤول",
                lawyer == null ? "لم يُعيّن" : lawyer.getFullName());

        if (d.getConvertedCase() != null) {
            sb.append("\n--- القضية المحوّلة ---\n");
            appendLine(sb, "رقم القضية", d.getConvertedCase().getCaseNumber());
            appendLine(sb, "عنوان القضية", d.getConvertedCase().getTitle());
            appendLine(sb, "معرّف القضية", d.getConvertedCase().getId().toString());
        }

        appendTimeline(sb, disputeId);

        String result = sb.toString();
        if (result.length() > MAX_CONTEXT_CHARS) {
            return result.substring(0, MAX_CONTEXT_CHARS) + "\n…[تم اقتطاع باقي السياق]";
        }
        return result;
    }

    public UUID resolveConvertedCaseId(UUID disputeId) {
        return disputeRepository.findScopedById(disputeId)
                .map(Dispute::getConvertedCase)
                .filter(cf -> cf != null)
                .map(cf -> cf.getId())
                .orElse(null);
    }

    private void appendTimeline(StringBuilder sb, UUID disputeId) {
        List<DisputeTimelineEvent> events =
                timelineRepository.findByDisputeIdForDetail(disputeId);
        int count = Math.min(events.size(), TIMELINE_LIMIT);
        sb.append("\n--- آخر ").append(count).append(" أحداث ---\n");
        if (events.isEmpty()) {
            sb.append("لا توجد أحداث مسجّلة.\n");
            return;
        }
        for (int i = 0; i < count; i++) {
            DisputeTimelineEvent e = events.get(i);
            sb.append("• ").append(e.getTitle() == null ? "—" : e.getTitle());
            if (e.getDescription() != null && !e.getDescription().isBlank()) {
                sb.append(" — ").append(truncate(e.getDescription(), 100));
            }
            sb.append("\n");
        }
    }

    private static void appendLine(StringBuilder sb, String label, String value) {
        if (value == null || value.isBlank()) return;
        sb.append(label).append(": ").append(value).append("\n");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String arabicStatus(DomainEnums.DisputeStatus status) {
        if (status == null) return "غير محددة";
        return switch (status) {
            case NEW -> "جديد";
            case UNDER_REVIEW -> "قيد المراجعة";
            case WAITING_DOCUMENTS -> "بانتظار مستندات";
            case FRIENDLY_SETTLEMENT -> "تسوية ودية";
            case NEGOTIATION -> "تفاوض";
            case MEDIATION -> "وساطة";
            case READY_TO_CONVERT -> "جاهز للتحويل";
            case CONVERTED_TO_CASE -> "حُوّل لقضية";
            case CLOSED -> "مغلق";
            case REJECTED -> "مرفوض";
        };
    }

    private static String arabicType(DomainEnums.DisputeType type) {
        if (type == null) return "غير محدد";
        return switch (type) {
            case CIVIL -> "مدني";
            case COMMERCIAL -> "تجاري";
            case LABOR -> "عمالي";
            case ADMINISTRATIVE -> "إداري";
            case CRIMINAL -> "جنائي";
            case FAMILY -> "أحوال شخصية";
            case REAL_ESTATE -> "عقاري";
            case OTHER -> "أخرى";
        };
    }

    private static String arabicStep(DomainEnums.DisputeWorkflowStep step) {
        if (step == null) return "—";
        return switch (step) {
            case CREATED -> "إنشاء";
            case INITIAL_REVIEW -> "مراجعة أولية";
            case DOCUMENT_COLLECTION -> "جمع مستندات";
            case NEGOTIATION -> "تفاوض";
            case MEDIATION -> "وساطة";
            case LEGAL_NOTICE -> "إنذار قانوني";
            case COURT_FILING -> "رفع دعوى";
            case HEARINGS -> "جلسات";
            case JUDGMENT -> "حكم";
            case CLOSED -> "إغلاق";
        };
    }

    private static String arabicRisk(DomainEnums.RiskLevel risk) {
        if (risk == null) return "—";
        return switch (risk) {
            case LOW -> "منخفض";
            case MEDIUM -> "متوسط";
            case HIGH -> "مرتفع";
            case CRITICAL -> "حرج";
        };
    }
}
