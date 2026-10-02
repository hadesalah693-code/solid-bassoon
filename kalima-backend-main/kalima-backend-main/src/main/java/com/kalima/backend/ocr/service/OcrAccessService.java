package com.kalima.backend.ocr.service;

import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.ocr.entity.OcrRecord;
import com.kalima.backend.ocr.entity.OcrRelation;
import com.kalima.backend.ocr.repository.OcrRelationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OcrAccessService {

    private final CaseFileRepository caseFileRepository;
    private final CaseAccessService caseAccessService;
    private final OcrRelationRepository relationRepository;

    public OcrAccessService(CaseFileRepository caseFileRepository,
                            CaseAccessService caseAccessService,
                            OcrRelationRepository relationRepository) {
        this.caseFileRepository = caseFileRepository;
        this.caseAccessService = caseAccessService;
        this.relationRepository = relationRepository;
    }

    public void requireReadable(OcrRecord record, Person actor) {
        if (canRead(record, actor)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "OCR record not found: " + record.getId());
    }

    public void requireWritable(OcrRecord record, Person actor) {
        if (canWrite(record, actor)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "لا تملك صلاحية تعديل سجل OCR هذا.");
    }

    public boolean canRead(OcrRecord record, Person actor) {
        if (isPrivileged(actor.getSystemRole())) {
            return true;
        }
        if (record.getUploadedBy().getId().equals(actor.getId())
                && isLawyerRole(actor.getSystemRole())) {
            return true;
        }
        return hasReadableCaseRelation(record.getId(), actor);
    }

    public boolean canWrite(OcrRecord record, Person actor) {
        if (!isLawyerRole(actor.getSystemRole()) && !isPrivileged(actor.getSystemRole())) {
            return false;
        }
        if (isPrivileged(actor.getSystemRole())) {
            return true;
        }
        if (record.getUploadedBy().getId().equals(actor.getId())) {
            return true;
        }
        return hasReadableCaseRelation(record.getId(), actor);
    }

    public boolean isPrivileged(DomainEnums.SystemRole role) {
        return role == DomainEnums.SystemRole.ADMIN
                || role == DomainEnums.SystemRole.SENIOR_LAWYER;
    }

    private boolean isLawyerRole(DomainEnums.SystemRole role) {
        return role == DomainEnums.SystemRole.LAWYER
                || role == DomainEnums.SystemRole.SENIOR_LAWYER
                || role == DomainEnums.SystemRole.ADMIN;
    }

    private boolean hasReadableCaseRelation(UUID ocrRecordId, Person actor) {
        List<OcrRelation> relations = relationRepository.findByOcrRecordId(ocrRecordId);
        for (OcrRelation relation : relations) {
            if (relation.getRelationType() != DomainEnums.OcrRelationType.CASE) {
                continue;
            }
            CaseFile caseFile = caseFileRepository.findScopedById(relation.getRelationId())
                    .orElse(null);
            if (caseFile == null) {
                continue;
            }
            try {
                caseAccessService.requireReadableCase(caseFile, actor);
                return true;
            } catch (ResponseStatusException ignored) {
                /* try next relation */
            }
        }
        return false;
    }
}
