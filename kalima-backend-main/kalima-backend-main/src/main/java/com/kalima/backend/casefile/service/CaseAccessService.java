package com.kalima.backend.casefile.service;



import com.kalima.backend.casefile.entity.CaseFile;

import com.kalima.backend.casefile.repository.CaseFileRepository;

import com.kalima.backend.caseparticipant.repository.CaseParticipantRepository;

import com.kalima.backend.domain.entity.DomainEnums;

import com.kalima.backend.domain.entity.Person;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;



import java.util.UUID;



@Service

@Transactional(readOnly = true)

public class CaseAccessService {



    private final CaseFileRepository caseFileRepository;

    private final CaseParticipantRepository caseParticipantRepository;



    public CaseAccessService(CaseFileRepository caseFileRepository,

                             CaseParticipantRepository caseParticipantRepository) {

        this.caseFileRepository = caseFileRepository;

        this.caseParticipantRepository = caseParticipantRepository;

    }



    public CaseFile requireReadableCase(UUID caseId, UUID personId, DomainEnums.SystemRole role) {

        CaseFile caseFile = caseFileRepository.findScopedById(caseId)

                .orElseThrow(() -> new ResponseStatusException(

                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));

        ensureCanRead(caseFile, personId, role);

        return caseFile;

    }



    public CaseFile requireReadableCase(UUID caseId, Person actor) {

        return requireReadableCase(caseId, actor.getId(), actor.getSystemRole());

    }



    public void requireReadableCase(CaseFile caseFile, Person actor) {

        ensureCanRead(caseFile, actor.getId(), actor.getSystemRole());

    }



    public CaseFile requireSendableCase(UUID caseId, Person actor) {

        CaseFile caseFile = requireReadableCase(caseId, actor);

        blockNonSendingRoles(caseFile, actor.getSystemRole());

        return caseFile;

    }



    public void requireSendableCase(CaseFile caseFile, Person actor) {

        requireReadableCase(caseFile, actor);

        blockNonSendingRoles(caseFile, actor.getSystemRole());

    }



    private void blockNonSendingRoles(CaseFile caseFile, DomainEnums.SystemRole role) {

        if (role == DomainEnums.SystemRole.SECRETARY

                || role == DomainEnums.SystemRole.ACCOUNTANT) {

            throw new ResponseStatusException(

                    HttpStatus.NOT_FOUND, "Case not found: " + caseFile.getId());

        }

    }



    /**

     * Access matrix aligned with {@link CaseFileRepository#findAllCasesAccessibleForInbox}

     * and chat-scope queries: privileged roles see all tenant cases; others need a

     * direct relationship (client, assigned lawyer, or case_participants row).

     */

    private void ensureCanRead(CaseFile caseFile, UUID personId, DomainEnums.SystemRole role) {

        if (isPrivileged(role)) {

            return;

        }



        if (role == DomainEnums.SystemRole.CLIENT) {

            if (caseFile.getClient() != null

                    && caseFile.getClient().getId().equals(personId)) {

                return;

            }

            if (isParticipant(caseFile.getId(), personId)) {

                return;

            }

            throw new ResponseStatusException(

                    HttpStatus.NOT_FOUND, "Case not found: " + caseFile.getId());

        }



        if (caseFile.getAssignedLawyer() != null

                && caseFile.getAssignedLawyer().getId().equals(personId)) {

            return;

        }



        if (isParticipant(caseFile.getId(), personId)) {

            return;

        }



        throw new ResponseStatusException(

                HttpStatus.NOT_FOUND, "Case not found: " + caseFile.getId());

    }



    private static boolean isPrivileged(DomainEnums.SystemRole role) {

        return role == DomainEnums.SystemRole.ADMIN

                || role == DomainEnums.SystemRole.SENIOR_LAWYER;

    }



    private boolean isParticipant(UUID caseId, UUID personId) {

        return caseParticipantRepository.existsByCaseIdAndPersonId(caseId, personId);

    }

    /** Matches {@code CaseController#changeStatus} — lawyers and above only. */
    public CaseFile requireCaseStatusChange(UUID caseId, Person actor) {
        CaseFile caseFile = requireReadableCase(caseId, actor);
        DomainEnums.SystemRole role = actor.getSystemRole();
        if (role == DomainEnums.SystemRole.ADMIN
                || role == DomainEnums.SystemRole.SENIOR_LAWYER
                || role == DomainEnums.SystemRole.LAWYER) {
            return caseFile;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "لا تملك صلاحية تغيير حالة القضية.");
    }

    /** Matches {@code CaseController#update} — excludes ACCOUNTANT. */
    public CaseFile requireCaseUpdate(UUID caseId, Person actor) {
        CaseFile caseFile = requireReadableCase(caseId, actor);
        DomainEnums.SystemRole role = actor.getSystemRole();
        if (role == DomainEnums.SystemRole.ADMIN
                || role == DomainEnums.SystemRole.SENIOR_LAWYER
                || role == DomainEnums.SystemRole.LAWYER
                || role == DomainEnums.SystemRole.SECRETARY) {
            return caseFile;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "لا تملك صلاحية تعديل بيانات القضية.");
    }

    /** Matches {@code TaskController#create}. */
    public CaseFile requireTaskCreate(UUID caseId, Person actor) {
        CaseFile caseFile = requireReadableCase(caseId, actor);
        DomainEnums.SystemRole role = actor.getSystemRole();
        if (role == DomainEnums.SystemRole.ADMIN
                || role == DomainEnums.SystemRole.SENIOR_LAWYER
                || role == DomainEnums.SystemRole.LAWYER
                || role == DomainEnums.SystemRole.SECRETARY) {
            return caseFile;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "لا تملك صلاحية إنشاء مهام على القضية.");
    }

}


