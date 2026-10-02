package com.kalima.backend.dashboard.service;

import com.kalima.backend.dashboard.dto.DashboardSummaryResponse;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.hearing.repository.HearingRepository;
import com.kalima.backend.task.repository.TaskItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<DomainEnums.CaseStatus> ACTIVE_CASE_STATUSES = List.of(
            DomainEnums.CaseStatus.NEW,
            DomainEnums.CaseStatus.IN_PROGRESS,
            DomainEnums.CaseStatus.ON_HOLD);

    private final CaseFileRepository caseFileRepository;
    private final HearingRepository hearingRepository;
    private final TaskItemRepository taskItemRepository;

    public DashboardService(
            CaseFileRepository caseFileRepository,
            HearingRepository hearingRepository,
            TaskItemRepository taskItemRepository) {
        this.caseFileRepository = caseFileRepository;
        this.hearingRepository = hearingRepository;
        this.taskItemRepository = taskItemRepository;
    }

    public DashboardSummaryResponse summary() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        long active = caseFileRepository.countByArchivedAtIsNullAndStatusIn(ACTIVE_CASE_STATUSES);
        long todayH = hearingRepository.countHearingsForActiveCasesBetween(start, end);
        long pending = taskItemRepository.countPendingOnActiveCases(
                DomainEnums.TaskStatus.TODO, DomainEnums.TaskStatus.IN_PROGRESS);

        return new DashboardSummaryResponse(
                active,
                todayH,
                pending,
                null);
    }
}
