package com.kalima.backend.disputeevent.service;

import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.disputeevent.dto.DisputeWorkflowTemplateStep;
import com.kalima.backend.disputeevent.entity.DisputeEvent;
import com.kalima.backend.disputeevent.repository.DisputeEventRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Inserts preset {@link DisputeEvent} rows once per case after creation when a
 * non-null, non-{@linkplain DomainEnums.DisputePathType#CUSTOM} path is chosen.
 *
 * <p>Idempotent: skips if the case already has any dispute events.
 */
@Service
public class DisputeWorkflowInitialEventsService {

    private final DisputeWorkflowTemplateService templateService;
    private final DisputeEventRepository disputeEventRepository;

    public DisputeWorkflowInitialEventsService(
            DisputeWorkflowTemplateService templateService,
            DisputeEventRepository disputeEventRepository) {
        this.templateService = templateService;
        this.disputeEventRepository = disputeEventRepository;
    }

    public void seedIfApplicable(CaseFile caseFile, DomainEnums.DisputePathType disputePathType) {
        if (disputePathType == null || disputePathType == DomainEnums.DisputePathType.CUSTOM) {
            return;
        }
        if (disputeEventRepository.countByCaseFile_Id(caseFile.getId()) > 0) {
            return;
        }
        List<DisputeWorkflowTemplateStep> steps = templateService.stepsFor(disputePathType);
        if (steps.isEmpty()) {
            return;
        }
        List<DisputeEvent> batch = new ArrayList<>(steps.size());
        int order = 0;
        for (DisputeWorkflowTemplateStep step : steps) {
            DisputeEvent e = new DisputeEvent();
            e.setCompany(caseFile.getCompany());
            e.setCaseFile(caseFile);
            e.setType(step.type());
            e.setStatus(step.status());
            e.setTitle(step.title());
            e.setSummary(null);
            e.setEventDate(null);
            e.setSortOrder(order++);
            batch.add(e);
        }
        disputeEventRepository.saveAll(batch);
    }
}
