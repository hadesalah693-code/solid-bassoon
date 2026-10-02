package com.kalima.backend.hearing.controller;

import com.kalima.backend.hearing.dto.HearingOfficeItemResponse;
import com.kalima.backend.hearing.entity.HearingStatus;
import com.kalima.backend.hearing.entity.HearingType;
import com.kalima.backend.hearing.service.HearingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Tenant-scoped list of hearings across cases (e.g. calendar / “today at the firm”).
 * Kept separate from {@link HearingController} so {@code /api/cases/.../hearings} URLs stay stable.
 */
@RestController
@RequestMapping("/api/hearings")
public class HearingListController {

    private final HearingService hearingService;

    public HearingListController(HearingService hearingService) {
        this.hearingService = hearingService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<HearingOfficeItemResponse> list(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) HearingStatus status,
            @RequestParam(required = false) HearingType type,
            @RequestParam(required = false) UUID lawyerId,
            @PageableDefault(size = 20, sort = "hearingDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return hearingService.listForOfficeOnDate(date, from, to, status, type, lawyerId, pageable);
    }
}
