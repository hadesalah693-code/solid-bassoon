package com.kalima.backend.person.controller;

import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.person.dto.PersonRequest;
import com.kalima.backend.person.dto.PersonResponse;
import com.kalima.backend.person.service.PersonService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/persons")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public ResponseEntity<PersonResponse> create(@Valid @RequestBody PersonRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        PersonResponse created = personService.create(request);
        URI location = uriBuilder.path("/api/persons/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','SENIOR_LAWYER')")
    public Page<PersonResponse> list(
            @RequestParam(name = "role", required = false) DomainEnums.SystemRole role,
            @RequestParam(name = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return personService.findAll(role, search, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','LAWYER','SENIOR_LAWYER')")
    public PersonResponse getById(@PathVariable UUID id) {
        return personService.findById(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public PersonResponse update(@PathVariable UUID id,
                                 @Valid @RequestBody PersonRequest request) {
        return personService.update(id, request);
    }

    /**
     * Soft-archive: sets {@code archivedAt}. Returns 409 if the person is
     * still linked from cases or case participants. Hard delete is not exposed.
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public PersonResponse archive(@PathVariable UUID id) {
        return personService.archive(id);
    }
}
