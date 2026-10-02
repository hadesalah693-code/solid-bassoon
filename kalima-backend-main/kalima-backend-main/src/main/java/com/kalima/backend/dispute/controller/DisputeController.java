package com.kalima.backend.dispute.controller;

import com.kalima.backend.dispute.dto.AddDisputeTimelineNoteRequest;
import com.kalima.backend.dispute.dto.CreateDisputeAdministrativeUpdateRequest;
import com.kalima.backend.dispute.dto.CreateDisputeStageRequest;
import com.kalima.backend.dispute.dto.CompleteDisputeStageActionRequest;
import com.kalima.backend.dispute.dto.CreateDisputeStageActionRequest;
import com.kalima.backend.dispute.dto.DisputeActionSummaryResponse;
import com.kalima.backend.dispute.dto.DisputeAdministrativeUpdateResponse;
import com.kalima.backend.dispute.dto.DisputeCaseLinkResponse;
import com.kalima.backend.dispute.dto.DisputeCreateRequest;
import com.kalima.backend.dispute.dto.DisputeListItemResponse;
import com.kalima.backend.dispute.dto.DisputeResponse;
import com.kalima.backend.dispute.dto.DisputeStageActionResponse;
import com.kalima.backend.dispute.dto.DisputeStageResponse;
import com.kalima.backend.dispute.dto.DisputeUpdateRequest;
import com.kalima.backend.dispute.dto.LinkDisputeCaseRequest;
import com.kalima.backend.dispute.dto.UpdateDisputeStageActionRequest;
import com.kalima.backend.dispute.dto.UpdateDisputeStageRequest;
import com.kalima.backend.dispute.service.DisputeService;
import com.kalima.backend.dispute.service.DisputeStageActionService;
import com.kalima.backend.domain.entity.DomainEnums;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/disputes")
public class DisputeController {

    private final DisputeService disputeService;
    private final DisputeStageActionService disputeStageActionService;

    public DisputeController(DisputeService disputeService,
                             DisputeStageActionService disputeStageActionService) {
        this.disputeService = disputeService;
        this.disputeStageActionService = disputeStageActionService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputeResponse> create(@Valid @RequestBody DisputeCreateRequest request,
                                                  UriComponentsBuilder uriBuilder) {
        DisputeResponse created = disputeService.create(request);
        URI location = uriBuilder.path("/api/disputes/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<DisputeListItemResponse> list(
            @RequestParam(name = "status", required = false) DomainEnums.DisputeStatus status,
            @RequestParam(name = "type", required = false) DomainEnums.DisputeType type,
            @RequestParam(name = "assignedLawyerId", required = false) UUID assignedLawyerId,
            @RequestParam(name = "currentStep", required = false) DomainEnums.DisputeWorkflowStep currentStep,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20)
            Pageable pageable) {
        return disputeService.search(status, type, assignedLawyerId, currentStep, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public DisputeResponse getById(@PathVariable UUID id) {
        return disputeService.getById(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputeResponse update(@PathVariable UUID id,
                                  @Valid @RequestBody DisputeUpdateRequest request) {
        return disputeService.update(id, request);
    }

    @PostMapping("/{id}/timeline")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputeResponse addTimelineNote(@PathVariable UUID id,
                                           @Valid @RequestBody AddDisputeTimelineNoteRequest request) {
        return disputeService.addTimelineNote(id, request);
    }

    @PostMapping("/{id}/convert-to-case")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputeResponse convertToCase(@PathVariable UUID id) {
        return disputeService.convertToCase(id);
    }

    @GetMapping("/{id}/stages")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputeStageResponse> listStages(@PathVariable UUID id) {
        return disputeService.listStages(id);
    }

    @PostMapping("/{id}/stages")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputeStageResponse> addStage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDisputeStageRequest request,
            UriComponentsBuilder uriBuilder) {
        DisputeStageResponse created = disputeService.addStage(id, request);
        URI location = uriBuilder
                .path("/api/disputes/{id}/stages/{stageId}")
                .buildAndExpand(id, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}/stages/{stageId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputeStageResponse updateStage(
            @PathVariable UUID id,
            @PathVariable UUID stageId,
            @Valid @RequestBody UpdateDisputeStageRequest request) {
        return disputeService.updateStage(id, stageId, request);
    }

    @GetMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputeCaseLinkResponse> listLinkedCases(@PathVariable UUID id) {
        return disputeService.listLinkedCases(id);
    }

    @PostMapping("/{id}/cases")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputeCaseLinkResponse> linkCase(
            @PathVariable UUID id,
            @Valid @RequestBody LinkDisputeCaseRequest request,
            UriComponentsBuilder uriBuilder) {
        DisputeCaseLinkResponse created = disputeService.linkCase(id, request);
        URI location = uriBuilder
                .path("/api/disputes/{id}/cases/{caseId}")
                .buildAndExpand(id, created.caseId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @DeleteMapping("/{id}/cases/{caseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public void unlinkCase(@PathVariable UUID id, @PathVariable UUID caseId) {
        disputeService.unlinkCase(id, caseId);
    }

    @GetMapping("/{id}/updates")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputeAdministrativeUpdateResponse> listAdministrativeUpdates(@PathVariable UUID id) {
        return disputeService.listAdministrativeUpdates(id);
    }

    @PostMapping("/{id}/updates")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputeAdministrativeUpdateResponse> addAdministrativeUpdate(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDisputeAdministrativeUpdateRequest request,
            UriComponentsBuilder uriBuilder) {
        DisputeAdministrativeUpdateResponse created = disputeService.addAdministrativeUpdate(id, request);
        URI location = uriBuilder
                .path("/api/disputes/{id}/updates/{updateId}")
                .buildAndExpand(id, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}/actions/summary")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public DisputeActionSummaryResponse actionSummary(@PathVariable UUID id) {
        return disputeStageActionService.getSummary(id);
    }

    @GetMapping("/{id}/actions")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputeStageActionResponse> listActions(
            @PathVariable UUID id,
            @RequestParam(name = "stageId", required = false) UUID stageId) {
        return disputeStageActionService.listActions(id, stageId);
    }

    @GetMapping("/{id}/stages/{stageId}/actions")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<DisputeStageActionResponse> listActionsForStage(
            @PathVariable UUID id,
            @PathVariable UUID stageId) {
        return disputeStageActionService.listActionsForStage(id, stageId);
    }

    @PostMapping("/{id}/actions")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<DisputeStageActionResponse> addAction(
            @PathVariable UUID id,
            @Valid @RequestBody CreateDisputeStageActionRequest request,
            UriComponentsBuilder uriBuilder) {
        DisputeStageActionResponse created = disputeStageActionService.addAction(id, request);
        URI location = uriBuilder
                .path("/api/disputes/{id}/actions/{actionId}")
                .buildAndExpand(id, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}/actions/{actionId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputeStageActionResponse updateAction(
            @PathVariable UUID id,
            @PathVariable UUID actionId,
            @Valid @RequestBody UpdateDisputeStageActionRequest request) {
        return disputeStageActionService.updateAction(id, actionId, request);
    }

    @PostMapping("/{id}/actions/{actionId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputeStageActionResponse completeAction(
            @PathVariable UUID id,
            @PathVariable UUID actionId,
            @Valid @RequestBody CompleteDisputeStageActionRequest request) {
        return disputeStageActionService.completeAction(id, actionId, request);
    }

    @PostMapping("/{id}/actions/{actionId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public DisputeStageActionResponse cancelAction(
            @PathVariable UUID id,
            @PathVariable UUID actionId) {
        return disputeStageActionService.cancelAction(id, actionId);
    }
}
