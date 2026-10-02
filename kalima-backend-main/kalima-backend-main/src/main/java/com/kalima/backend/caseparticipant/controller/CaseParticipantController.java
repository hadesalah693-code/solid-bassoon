package com.kalima.backend.caseparticipant.controller;

import com.kalima.backend.caseparticipant.dto.ParticipantAddRequest;
import com.kalima.backend.caseparticipant.dto.ParticipantResponse;
import com.kalima.backend.caseparticipant.service.CaseParticipantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cases/{caseId}/participants")
public class CaseParticipantController {

    private final CaseParticipantService service;

    public CaseParticipantController(CaseParticipantService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<ParticipantResponse> add(@PathVariable UUID caseId,
                                                   @Valid @RequestBody ParticipantAddRequest request,
                                                   UriComponentsBuilder uriBuilder) {
        ParticipantResponse created = service.add(caseId, request);
        URI location = uriBuilder
                .path("/api/cases/{caseId}/participants/{id}")
                .buildAndExpand(caseId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public List<ParticipantResponse> list(@PathVariable UUID caseId) {
        return service.list(caseId);
    }

    @DeleteMapping("/{participantId}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID caseId, @PathVariable UUID participantId) {
        service.remove(caseId, participantId);
    }
}
