package com.kalima.backend.task.controller;

import com.kalima.backend.task.dto.TaskCreateRequest;
import com.kalima.backend.task.dto.TaskResponse;
import com.kalima.backend.task.dto.TaskUpdateRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<TaskResponse> create(
            @PathVariable UUID caseId,
            @Valid @RequestBody TaskCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        TaskResponse created = taskService.create(caseId, request);
        URI loc = uriBuilder
                .path("/api/cases/{caseId}/tasks/{taskId}")
                .buildAndExpand(caseId, created.id())
                .toUri();
        return ResponseEntity.created(loc).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public Page<TaskResponse> list(
            @PathVariable UUID caseId,
            @PageableDefault(size = 20) Pageable pageable) {
        return taskService.list(caseId, pageable);
    }

    @GetMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public TaskResponse get(
            @PathVariable UUID caseId,
            @PathVariable UUID taskId) {
        return taskService.findById(caseId, taskId);
    }

    @PutMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    @ResponseStatus(HttpStatus.OK)
    public TaskResponse update(
            @PathVariable UUID caseId,
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(caseId, taskId, request);
    }

    @DeleteMapping("/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID caseId,
            @PathVariable UUID taskId) {
        taskService.deleteInCase(caseId, taskId);
    }
}
