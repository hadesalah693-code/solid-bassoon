package com.kalima.backend.message.controller;

import com.kalima.backend.message.service.MessageAttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages/attachments")
public class MessageAttachmentController {

    private final MessageAttachmentService attachmentService;

    public MessageAttachmentController(MessageAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public Map<String, String> upload(@RequestParam UUID caseId,
                                      @RequestParam("file") MultipartFile file) {
        return Map.of("url", attachmentService.upload(caseId, file));
    }

    @GetMapping("/{filename}")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','CLIENT')")
    public ResponseEntity<Resource> download(@PathVariable String filename) {
        MessageAttachmentService.DownloadResult result = attachmentService.download(filename);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + result.originalFilename() + "\"")
                .contentType(MediaType.parseMediaType(result.contentType()))
                .body(result.resource());
    }
}
