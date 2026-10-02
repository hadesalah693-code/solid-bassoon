package com.kalima.backend.memo.controller;

import com.kalima.backend.memo.dto.MemoResponse;
import com.kalima.backend.memo.dto.MemoWriteRequest;
import com.kalima.backend.memo.entity.MemoStatus;
import com.kalima.backend.memo.entity.MemoType;
import com.kalima.backend.memo.service.MemoService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/memos")
public class MemoController {

    private final MemoService memoService;

    public MemoController(MemoService memoService) {
        this.memoService = memoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public Page<MemoResponse> list(
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) MemoStatus status,
            @RequestParam(required = false) MemoType type,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return memoService.search(caseId, status, type, search, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public ResponseEntity<MemoResponse> create(
            @RequestParam UUID caseId, @Valid @RequestBody MemoWriteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memoService.create(caseId, request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public MemoResponse get(@PathVariable UUID id) {
        return memoService.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public MemoResponse update(@PathVariable UUID id, @Valid @RequestBody MemoWriteRequest request) {
        return memoService.update(id, request);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public MemoResponse submit(@PathVariable UUID id) { return memoService.submit(id); }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    public MemoResponse approve(@PathVariable UUID id) { return memoService.approve(id); }

    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY')")
    public MemoResponse send(@PathVariable UUID id) { return memoService.send(id); }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER')")
    @ResponseStatus(HttpStatus.OK)
    public MemoResponse archive(@PathVariable UUID id) { return memoService.archive(id); }
}