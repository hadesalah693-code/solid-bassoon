package com.kalima.backend.task.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.task.dto.AdminTaskCreateRequest;
import com.kalima.backend.task.dto.AdminTaskUpdateRequest;
import com.kalima.backend.task.dto.OpenTaskCountResponse;
import com.kalima.backend.task.dto.TaskResponse;
import com.kalima.backend.task.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class AdminTaskController {

    private final TaskService taskService;

    public AdminTaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<TaskResponse> list(
            @RequestParam(name = "status", required = false) DomainEnums.TaskStatus status,
            @RequestParam(name = "assignedToId", required = false) UUID assignedToId,
            @RequestParam(name = "caseId", required = false) UUID caseId,
            @RequestParam(name = "disputeId", required = false) UUID disputeId,
            @RequestParam(name = "unassignedOnly", required = false) Boolean unassignedOnly,
            @RequestParam(name = "adminTaskType", required = false) DomainEnums.AdminTaskType adminTaskType,
            @PageableDefault(size = 20) Pageable pageable) {
        return taskService.listAll(
                status, assignedToId, caseId, disputeId, unassignedOnly, adminTaskType, pageable);
    }

    @GetMapping("/my-open-count")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public OpenTaskCountResponse myOpenCount() {
        return taskService.myOpenCount();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody AdminTaskCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        TaskResponse created = taskService.createAdmin(request);
        URI loc = uriBuilder.path("/api/tasks/{taskId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(loc).body(created);
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public TaskResponse get(@PathVariable UUID taskId) {
        return taskService.findById(taskId);
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public TaskResponse update(
            @PathVariable UUID taskId,
            @Valid @RequestBody AdminTaskUpdateRequest request) {
        return taskService.updateAdmin(taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID taskId) {
        taskService.delete(taskId);
    }
}
