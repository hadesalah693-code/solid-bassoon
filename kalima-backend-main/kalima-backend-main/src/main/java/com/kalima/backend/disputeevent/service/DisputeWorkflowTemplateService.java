package com.kalima.backend.disputeevent.service;

import com.kalima.backend.disputeevent.dto.DisputeWorkflowTemplateStep;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventStatus.DONE;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventStatus.UPCOMING;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.APPEAL;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.CASE_OPENED;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.CLAIM_FILED;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.FIRST_HEARING;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.JUDGMENT;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.MEMO_EXCHANGE;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.OTHER;
import static com.kalima.backend.domain.entity.DomainEnums.DisputeEventType.WARNING_NOTICE;

/**
 * Default dispute-event presets per legal path. {@link DomainEnums.DisputePathType#CUSTOM}
 * yields an empty list (no auto rows).
 */
@Service
public class DisputeWorkflowTemplateService {

    public List<DisputeWorkflowTemplateStep> stepsFor(DomainEnums.DisputePathType pathType) {
        if (pathType == null || pathType == DomainEnums.DisputePathType.CUSTOM) {
            return Collections.emptyList();
        }
        return switch (pathType) {
            case CIVIL -> civil();
            case COMMERCIAL -> commercial();
            case LABOR -> labor();
            case CRIMINAL -> criminal();
            case ADMINISTRATIVE -> administrative();
            case ARBITRATION -> arbitration();
            case CUSTOM -> Collections.emptyList();
        };
    }

    private static List<DisputeWorkflowTemplateStep> civil() {
        return List.of(
                new DisputeWorkflowTemplateStep(CASE_OPENED, DONE, "فتح القضية"),
                new DisputeWorkflowTemplateStep(WARNING_NOTICE, UPCOMING, "إنذار / مطالبة"),
                new DisputeWorkflowTemplateStep(CLAIM_FILED, UPCOMING, "رفع الدعوى"),
                new DisputeWorkflowTemplateStep(FIRST_HEARING, UPCOMING, "الجلسة الأولى"),
                new DisputeWorkflowTemplateStep(MEMO_EXCHANGE, UPCOMING, "تبادل المذكرات"),
                new DisputeWorkflowTemplateStep(JUDGMENT, UPCOMING, "حكم ابتدائي"),
                new DisputeWorkflowTemplateStep(APPEAL, UPCOMING, "استئناف"),
                new DisputeWorkflowTemplateStep(OTHER, UPCOMING, "تنفيذ الحكم")
        );
    }

    private static List<DisputeWorkflowTemplateStep> commercial() {
        return List.of(
                new DisputeWorkflowTemplateStep(CASE_OPENED, DONE, "فتح الملف التجاري"),
                new DisputeWorkflowTemplateStep(WARNING_NOTICE, UPCOMING, "مطالبة تجارية"),
                new DisputeWorkflowTemplateStep(CLAIM_FILED, UPCOMING, "رفع الدعوى التجارية"),
                new DisputeWorkflowTemplateStep(FIRST_HEARING, UPCOMING, "الجلسة الأولى"),
                new DisputeWorkflowTemplateStep(MEMO_EXCHANGE, UPCOMING, "مستندات ومذكرات"),
                new DisputeWorkflowTemplateStep(JUDGMENT, UPCOMING, "حكم تجاري"),
                new DisputeWorkflowTemplateStep(APPEAL, UPCOMING, "استئناف تجاري")
        );
    }

    private static List<DisputeWorkflowTemplateStep> labor() {
        return List.of(
                new DisputeWorkflowTemplateStep(CASE_OPENED, DONE, "فتح النزاع العمالي"),
                new DisputeWorkflowTemplateStep(WARNING_NOTICE, UPCOMING, "شكوى أو مطالبة عمالية"),
                new DisputeWorkflowTemplateStep(CLAIM_FILED, UPCOMING, "رفع الدعوى العمالية"),
                new DisputeWorkflowTemplateStep(FIRST_HEARING, UPCOMING, "أول جلسة"),
                new DisputeWorkflowTemplateStep(MEMO_EXCHANGE, UPCOMING, "مستندات العمل والردود"),
                new DisputeWorkflowTemplateStep(JUDGMENT, UPCOMING, "الحكم"),
                new DisputeWorkflowTemplateStep(OTHER, UPCOMING, "التنفيذ أو التسوية")
        );
    }

    private static List<DisputeWorkflowTemplateStep> criminal() {
        return List.of(
                new DisputeWorkflowTemplateStep(CASE_OPENED, DONE, "فتح الملف الجنائي"),
                new DisputeWorkflowTemplateStep(OTHER, UPCOMING, "بلاغ / محضر"),
                new DisputeWorkflowTemplateStep(FIRST_HEARING, UPCOMING, "جلسة أولى"),
                new DisputeWorkflowTemplateStep(MEMO_EXCHANGE, UPCOMING, "دفاع ومذكرات"),
                new DisputeWorkflowTemplateStep(JUDGMENT, UPCOMING, "حكم"),
                new DisputeWorkflowTemplateStep(APPEAL, UPCOMING, "استئناف / معارضة")
        );
    }

    private static List<DisputeWorkflowTemplateStep> administrative() {
        return List.of(
                new DisputeWorkflowTemplateStep(CASE_OPENED, DONE, "فتح النزاع الإداري"),
                new DisputeWorkflowTemplateStep(WARNING_NOTICE, UPCOMING, "تظلم إداري"),
                new DisputeWorkflowTemplateStep(CLAIM_FILED, UPCOMING, "رفع الدعوى الإدارية"),
                new DisputeWorkflowTemplateStep(FIRST_HEARING, UPCOMING, "أول جلسة"),
                new DisputeWorkflowTemplateStep(MEMO_EXCHANGE, UPCOMING, "مذكرات ومستندات"),
                new DisputeWorkflowTemplateStep(JUDGMENT, UPCOMING, "حكم إداري"),
                new DisputeWorkflowTemplateStep(APPEAL, UPCOMING, "طعن / استئناف")
        );
    }

    private static List<DisputeWorkflowTemplateStep> arbitration() {
        return List.of(
                new DisputeWorkflowTemplateStep(CASE_OPENED, DONE, "فتح ملف التحكيم"),
                new DisputeWorkflowTemplateStep(WARNING_NOTICE, UPCOMING, "إخطار التحكيم"),
                new DisputeWorkflowTemplateStep(CLAIM_FILED, UPCOMING, "تسجيل طلب التحكيم"),
                new DisputeWorkflowTemplateStep(FIRST_HEARING, UPCOMING, "جلسة إجرائية أولى"),
                new DisputeWorkflowTemplateStep(MEMO_EXCHANGE, UPCOMING, "تبادل مذكرات"),
                new DisputeWorkflowTemplateStep(JUDGMENT, UPCOMING, "حكم التحكيم"),
                new DisputeWorkflowTemplateStep(OTHER, UPCOMING, "تنفيذ الحكم")
        );
    }
}
