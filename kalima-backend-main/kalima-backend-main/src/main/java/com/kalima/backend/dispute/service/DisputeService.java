package com.kalima.backend.dispute.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casefile.dto.CaseCreateRequest;
import com.kalima.backend.casefile.dto.CaseResponse;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseService;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.dispute.dto.AddDisputeTimelineNoteRequest;
import com.kalima.backend.dispute.dto.CreateDisputeAdministrativeUpdateRequest;
import com.kalima.backend.dispute.dto.CreateDisputeStageRequest;
import com.kalima.backend.dispute.dto.DisputeAdministrativeUpdateResponse;
import com.kalima.backend.dispute.dto.DisputeCaseLinkResponse;
import com.kalima.backend.dispute.dto.DisputeCreateRequest;
import com.kalima.backend.dispute.dto.DisputeListItemResponse;
import com.kalima.backend.dispute.dto.DisputeResponse;
import com.kalima.backend.dispute.dto.DisputeStageResponse;
import com.kalima.backend.dispute.dto.DisputeTimelineEventResponse;
import com.kalima.backend.dispute.dto.DisputeUpdateRequest;
import com.kalima.backend.dispute.dto.LinkDisputeCaseRequest;
import com.kalima.backend.dispute.dto.UpdateDisputeStageRequest;
import com.kalima.backend.dispute.entity.Dispute;
import com.kalima.backend.dispute.entity.DisputeAdministrativeUpdate;
import com.kalima.backend.dispute.entity.DisputeCaseLink;
import com.kalima.backend.dispute.entity.DisputeStage;
import com.kalima.backend.dispute.entity.DisputeTimelineEvent;
import com.kalima.backend.dispute.repository.DisputeAdministrativeUpdateRepository;
import com.kalima.backend.dispute.repository.DisputeCaseLinkRepository;
import com.kalima.backend.dispute.repository.DisputeNumberSequenceRepository;
import com.kalima.backend.dispute.repository.DisputeRepository;
import com.kalima.backend.dispute.repository.DisputeStageRepository;
import com.kalima.backend.dispute.repository.DisputeTimelineEventRepository;
import com.kalima.backend.notification.service.NotificationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DisputeService {

    private static final Set<DomainEnums.SystemRole> LAWYER_ROLES = EnumSet.of(
            DomainEnums.SystemRole.LAWYER,
            DomainEnums.SystemRole.SENIOR_LAWYER);

    private static final String CASE_LINK_ROLE_CONVERTED = "CONVERTED";
    private static final String ADMIN_UPDATE_TYPE_CONVERTED = "CONVERTED_TO_CASE";

    private final DisputeRepository disputeRepository;
    private final DisputeTimelineEventRepository timelineEventRepository;
    private final DisputeStageRepository disputeStageRepository;
    private final DisputeCaseLinkRepository disputeCaseLinkRepository;
    private final DisputeAdministrativeUpdateRepository disputeAdministrativeUpdateRepository;
    private final PersonRepository personRepository;
    private final CompanyRepository companyRepository;
    private final CaseFileRepository caseFileRepository;
    private final CurrentUserService currentUserService;
    private final CaseService caseService;
    private final DisputeNumberSequenceRepository disputeNumberSequenceRepository;
    private final DisputeTeamService disputeTeamService;
    private final NotificationService notificationService;

    public DisputeService(DisputeRepository disputeRepository,
                          DisputeTimelineEventRepository timelineEventRepository,
                          DisputeStageRepository disputeStageRepository,
                          DisputeCaseLinkRepository disputeCaseLinkRepository,
                          DisputeAdministrativeUpdateRepository disputeAdministrativeUpdateRepository,
                          PersonRepository personRepository,
                          CompanyRepository companyRepository,
                          CaseFileRepository caseFileRepository,
                          CurrentUserService currentUserService,
                          CaseService caseService,
                          DisputeNumberSequenceRepository disputeNumberSequenceRepository,
                          DisputeTeamService disputeTeamService,
                          NotificationService notificationService) {
        this.disputeRepository = disputeRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.disputeStageRepository = disputeStageRepository;
        this.disputeCaseLinkRepository = disputeCaseLinkRepository;
        this.disputeAdministrativeUpdateRepository = disputeAdministrativeUpdateRepository;
        this.personRepository = personRepository;
        this.companyRepository = companyRepository;
        this.caseFileRepository = caseFileRepository;
        this.currentUserService = currentUserService;
        this.caseService = caseService;
        this.disputeNumberSequenceRepository = disputeNumberSequenceRepository;
        this.disputeTeamService = disputeTeamService;
        this.notificationService = notificationService;
    }

    public DisputeResponse create(DisputeCreateRequest request) {
        Person actor = currentUserService.requireCurrentPerson();
        Person client = requireClient(request.clientId());
        Person lawyer = resolveLawyer(request.assignedLawyerId());
        UUID tenantId = TenantContext.getRequired();
        int year = LocalDate.now(ZoneId.systemDefault()).getYear();
        int seq = disputeNumberSequenceRepository.allocateNextSequence(tenantId, year);
        String disputeNumber = "DIS-" + year + "-" + String.format(Locale.ROOT, "%04d", seq);

        Dispute d = new Dispute();
        d.setCompany(currentTenantReference());
        d.setDisputeNumber(disputeNumber);
        d.setClient(client);
        d.setClientRole(request.clientRole());
        d.setAssignedLawyer(lawyer);
        d.setOpponentName(requireText(request.opponentName(), "opponentName"));
        d.setOpponentRole(request.opponentRole());
        d.setTitle(requireText(request.title(), "title"));
        d.setDescription(trimToNull(request.description()));
        d.setDisputeType(request.disputeType());
        d.setStatus(DomainEnums.DisputeStatus.NEW);
        d.setLegalPath(request.legalPath());
        d.setCurrentStep(DomainEnums.DisputeWorkflowStep.CREATED);
        d.setClaimAmount(request.claimAmount());
        d.setRiskLevel(request.riskLevel());
        d.setSuccessProbability(request.successProbability());

        Dispute saved = disputeRepository.save(d);
        disputeTeamService.setMemberOnCreate(saved, DomainEnums.DisputeTeamRole.ADMINISTRATIVE_OFFICER,
                request.administrativeOfficerId());
        if (lawyer != null) {
            disputeTeamService.setMemberOnCreate(saved, DomainEnums.DisputeTeamRole.ASSIGNED_LAWYER,
                    lawyer.getId());
        }
        saved = disputeRepository.save(saved);
        appendTimeline(
                saved,
                DomainEnums.DisputeTimelineEventType.CREATED,
                "تم إنشاء النزاع",
                null,
                actor);

        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DisputeListItemResponse> search(
            DomainEnums.DisputeStatus status,
            DomainEnums.DisputeType type,
            UUID assignedLawyerId,
            DomainEnums.DisputeWorkflowStep currentStep,
            String search,
            Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank()
                ? null
                : search.trim().toLowerCase(Locale.ROOT);
        // Fixed sort is in repository JPQL (createdAt DESC). Strip any client sort to avoid
        // duplicate ORDER BY / invalid sort paths on mapped-superclass fields.
        Pageable paged = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return disputeRepository.search(status, type, assignedLawyerId, currentStep, normalizedSearch, paged)
                .map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public DisputeResponse getById(UUID id) {
        Dispute d = loadDispute(id);
        return toDetailResponse(d);
    }

    @Transactional(readOnly = true)
    public List<DisputeStageResponse> listStages(UUID disputeId) {
        loadDispute(disputeId);
        return disputeStageRepository.findByDispute_IdOrderByCreatedAtAsc(disputeId).stream()
                .map(this::toStageResponse)
                .toList();
    }

    public DisputeStageResponse addStage(UUID disputeId, CreateDisputeStageRequest request) {
        Dispute d = loadDispute(disputeId);
        DisputeStage s = new DisputeStage();
        s.setCompany(d.getCompany());
        s.setDispute(d);
        s.setStageType(requireText(request.stageType(), "stageType"));
        String rawStatus = trimToNull(request.status());
        s.setStatus(rawStatus != null
                ? DomainEnums.DisputeStageStatus.valueOf(rawStatus.toUpperCase(Locale.ROOT))
                : DomainEnums.DisputeStageStatus.ACTIVE);
        s.setTitle(trimToNull(request.title()));
        s.setStartedAt(request.startedAt());
        s.setClosedAt(request.closedAt());
        return toStageResponse(disputeStageRepository.save(s));
    }

    public DisputeStageResponse updateStage(UUID disputeId, UUID stageId, UpdateDisputeStageRequest request) {
        Dispute d = loadDispute(disputeId);
        DisputeStage stage = disputeStageRepository.findByIdAndDispute_Id(stageId, disputeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Stage not found: " + stageId));
        boolean wasClosed = stageIsClosed(stage);

        if (request.title() != null) {
            stage.setTitle(trimToNull(request.title()));
        }
        if (request.status() != null) {
            stage.setStatus(request.status());
        }
        if (request.startedAt() != null) {
            stage.setStartedAt(request.startedAt());
        }
        if (request.closedAt() != null) {
            stage.setClosedAt(request.closedAt());
        }

        boolean becomesClosed = stageIsClosed(stage);
        String closingNoteToPersist = null;
        if (becomesClosed && !wasClosed) {
            closingNoteToPersist = requireText(request.closingNote(), "closingNote");
            if (request.outcome() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome is required");
            }
            stage.setOutcome(request.outcome());
            if (request.outcomeNote() != null) {
                stage.setOutcomeNote(trimToNull(request.outcomeNote()));
            }
        } else {
            if (request.outcome() != null) {
                stage.setOutcome(request.outcome());
            }
            if (request.outcomeNote() != null) {
                stage.setOutcomeNote(trimToNull(request.outcomeNote()));
            }
        }

        DisputeStage saved = disputeStageRepository.save(stage);

        if (closingNoteToPersist != null) {
            Person actor = currentUserService.requireCurrentPerson();
            appendStageClosedAdministrativeUpdate(d, saved, closingNoteToPersist, actor);
        }

        return toStageResponse(saved);
    }

    private void appendStageClosedAdministrativeUpdate(Dispute dispute, DisputeStage stage, String closingNote, Person actor) {
        DisputeAdministrativeUpdate u = new DisputeAdministrativeUpdate();
        u.setCompany(dispute.getCompany());
        u.setDispute(dispute);
        u.setStage(stage);
        u.setUpdateType("STAGE_CLOSED");
        u.setTitle("تم إغلاق المرحلة");
        String outcomeAr = stage.getOutcome() != null ? stage.getOutcome().arabicLabel() : "";
        StringBuilder desc = new StringBuilder();
        desc.append("نتيجة المرحلة: ").append(outcomeAr);
        desc.append("\n\n").append(closingNote.trim());
        String extra = trimToNull(stage.getOutcomeNote());
        if (extra != null) {
            desc.append("\n\nملاحظات إضافية: ").append(extra);
        }
        u.setDescription(desc.toString());
        u.setOccurredAt(LocalDateTime.now());
        u.setCreatedBy(actor);
        disputeAdministrativeUpdateRepository.save(u);
    }

    private static boolean stageIsClosed(DisputeStage stage) {
        if (stage.getClosedAt() != null) {
            return true;
        }
        return stage.getStatus() == DomainEnums.DisputeStageStatus.CLOSED;
    }

    @Transactional(readOnly = true)
    public List<DisputeCaseLinkResponse> listLinkedCases(UUID disputeId) {
        loadDispute(disputeId);
        return disputeCaseLinkRepository.findByDispute_IdOrderByCreatedAtAsc(disputeId).stream()
                .map(this::toCaseLinkResponse)
                .toList();
    }

    public DisputeCaseLinkResponse linkCase(UUID disputeId, LinkDisputeCaseRequest request) {
        Dispute d = loadDispute(disputeId);
        UUID caseId = request.caseId();
        if (disputeCaseLinkRepository.existsByDispute_IdAndCaseFile_Id(disputeId, caseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Case is already linked to this dispute");
        }
        CaseFile caseFile = caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Case not found: " + caseId));
        DisputeCaseLink link = new DisputeCaseLink();
        link.setCompany(d.getCompany());
        link.setDispute(d);
        link.setCaseFile(caseFile);
        String role = trimToNull(request.linkRole());
        link.setLinkRole(role != null ? role : "LINKED");
        try {
            return toCaseLinkResponse(disputeCaseLinkRepository.save(link));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Case is already linked to this dispute", ex);
        }
    }

    public void unlinkCase(UUID disputeId, UUID caseId) {
        loadDispute(disputeId);
        if (!disputeCaseLinkRepository.existsByDispute_IdAndCaseFile_Id(disputeId, caseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No link between dispute and case");
        }
        disputeCaseLinkRepository.deleteByDispute_IdAndCaseFile_Id(disputeId, caseId);
    }

    @Transactional(readOnly = true)
    public List<DisputeAdministrativeUpdateResponse> listAdministrativeUpdates(UUID disputeId) {
        loadDispute(disputeId);
        return disputeAdministrativeUpdateRepository.findByDispute_IdOrderByCreatedAtDesc(disputeId).stream()
                .map(this::toAdministrativeUpdateResponse)
                .toList();
    }

    public DisputeAdministrativeUpdateResponse addAdministrativeUpdate(
            UUID disputeId,
            CreateDisputeAdministrativeUpdateRequest request) {
        Dispute d = loadDispute(disputeId);
        DisputeStage stage = null;
        if (request.stageId() != null) {
            stage = disputeStageRepository.findByIdAndDispute_Id(request.stageId(), disputeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Stage not found: " + request.stageId()));
        }
        Person actor = currentUserService.requireCurrentPerson();
        DisputeAdministrativeUpdate u = new DisputeAdministrativeUpdate();
        u.setCompany(d.getCompany());
        u.setDispute(d);
        u.setStage(stage);
        u.setUpdateType(requireText(request.updateType(), "updateType"));
        u.setTitle(requireText(request.title(), "title"));
        u.setDescription(trimToNull(request.description()));
        u.setOccurredAt(request.occurredAt());
        u.setCreatedBy(actor);
        return toAdministrativeUpdateResponse(disputeAdministrativeUpdateRepository.save(u));
    }

    public DisputeResponse update(UUID id, DisputeUpdateRequest request) {
        Dispute d = loadDispute(id);
        ensureMutable(d);
        Person actor = currentUserService.requireCurrentPerson();

        if (request.title() != null) {
            d.setTitle(requireText(request.title(), "title"));
        }
        if (request.opponentName() != null) {
            d.setOpponentName(requireText(request.opponentName(), "opponentName"));
        }
        if (request.description() != null) {
            d.setDescription(trimToNull(request.description()));
        }
        if (request.disputeType() != null) {
            d.setDisputeType(request.disputeType());
        }
        if (request.claimAmount() != null) {
            d.setClaimAmount(request.claimAmount());
        }
        if (request.riskLevel() != null) {
            d.setRiskLevel(request.riskLevel());
        }
        if (request.successProbability() != null) {
            d.setSuccessProbability(request.successProbability());
        }
        if (request.clientId() != null) {
            d.setClient(requireClient(request.clientId()));
        }
        if (request.clientRole() != null) {
            d.setClientRole(request.clientRole());
        }
        if (request.opponentRole() != null) {
            d.setOpponentRole(request.opponentRole());
        }

        if (request.status() != null && !Objects.equals(d.getStatus(), request.status())) {
            DomainEnums.DisputeStatus prev = d.getStatus();
            d.setStatus(request.status());
            appendTimeline(
                    d,
                    DomainEnums.DisputeTimelineEventType.STATUS_CHANGED,
                    "تغيير الحالة",
                    "من " + prev + " إلى " + d.getStatus(),
                    actor);
        }

        if (request.legalPath() != null && !Objects.equals(d.getLegalPath(), request.legalPath())) {
            DomainEnums.LegalPath prev = d.getLegalPath();
            d.setLegalPath(request.legalPath());
            String prevText = prev == null ? "بدون مسار" : prev.name();
            appendTimeline(
                    d,
                    DomainEnums.DisputeTimelineEventType.LEGAL_PATH_CHANGED,
                    "تغيير المسار القانوني",
                    "من " + prevText + " إلى " + d.getLegalPath(),
                    actor);
        }

        if (request.assignedLawyerId() != null) {
            UUID prevId = d.getAssignedLawyer() == null ? null : d.getAssignedLawyer().getId();
            if (!Objects.equals(prevId, request.assignedLawyerId())) {
                Person previousLawyer = d.getAssignedLawyer();
                Person nextLawyer = resolveLawyer(request.assignedLawyerId());
                d.setAssignedLawyer(nextLawyer);
                if (nextLawyer != null) {
                    disputeTeamService.setMemberOnCreate(d, DomainEnums.DisputeTeamRole.ASSIGNED_LAWYER,
                            nextLawyer.getId());
                } else {
                    disputeTeamService.clearMember(d, DomainEnums.DisputeTeamRole.ASSIGNED_LAWYER);
                }
                String label = nextLawyer == null ? "إزالة المحامي المسؤول"
                        : "تعيين المحامي: " + displayName(nextLawyer);
                appendTimeline(
                        d,
                        DomainEnums.DisputeTimelineEventType.LAWYER_ASSIGNED,
                        label,
                        null,
                        actor);
                Person adminOfficer = disputeTeamService.findMemberPerson(
                        d.getId(), DomainEnums.DisputeTeamRole.ADMINISTRATIVE_OFFICER);
                notificationService.notifyDisputeLawyerChanged(
                        d, previousLawyer, nextLawyer, adminOfficer, actor);
            }
        }

        if (request.currentStep() != null && !Objects.equals(d.getCurrentStep(), request.currentStep())) {
            DomainEnums.DisputeWorkflowStep prev = d.getCurrentStep();
            d.setCurrentStep(request.currentStep());
            appendTimeline(
                    d,
                    DomainEnums.DisputeTimelineEventType.WORKFLOW_STEP_CHANGED,
                    "تقدم مسار العمل",
                    "من " + prev + " إلى " + d.getCurrentStep(),
                    actor);
        }

        Dispute saved = disputeRepository.save(d);
        return toDetailResponse(saved);
    }

    public DisputeResponse addTimelineNote(UUID id, AddDisputeTimelineNoteRequest request) {
        Dispute d = loadDispute(id);
        ensureMutable(d);
        Person actor = currentUserService.requireCurrentPerson();
        appendTimeline(
                d,
                DomainEnums.DisputeTimelineEventType.NOTE_ADDED,
                requireText(request.title(), "title"),
                trimToNull(request.description()),
                actor);
        return toDetailResponse(d);
    }

    public DisputeResponse convertToCase(UUID id) {
        Dispute d = loadDispute(id);
        if (d.getConvertedCase() != null) {
            syncConversionArtifacts(d, requireCurrentActorForConversion());
            return toDetailResponse(d);
        }
        if (d.getStatus() == DomainEnums.DisputeStatus.CONVERTED_TO_CASE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dispute is marked converted but no case is linked");
        }
        if (d.getClient() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "client is required to convert dispute to case");
        }

        Person actor = requireCurrentActorForConversion();
        String caseNumber = uniqueDisputeCaseNumber(d.getId());

        CaseCreateRequest caseRequest = new CaseCreateRequest(
                caseNumber,
                null,
                d.getTitle(),
                d.getDescription(),
                toCaseTypeLabel(d.getDisputeType()),
                null,
                null,
                null,
                DomainEnums.CaseStatus.NEW,
                null,
                d.getClient().getId(),
                d.getAssignedLawyer() == null ? null : d.getAssignedLawyer().getId(),
                toCaseDisputePath(d.getLegalPath()),
                null,
                null);

        CaseResponse created = caseService.create(caseRequest);
        CaseFile caseRef = caseFileRepository.getReferenceById(created.id());

        d.setConvertedCase(caseRef);
        d.setStatus(DomainEnums.DisputeStatus.CONVERTED_TO_CASE);
        disputeRepository.save(d);

        appendTimeline(
                d,
                DomainEnums.DisputeTimelineEventType.CONVERTED_TO_CASE,
                "تحويل إلى قضية رسمية",
                "رقم الملف: " + created.caseNumber(),
                actor);

        syncConversionArtifacts(d, caseRef, created.caseNumber(), actor);

        return toDetailResponse(d);
    }

    private Person requireCurrentActorForConversion() {
        return currentUserService.requireCurrentPerson();
    }

    /**
     * Ensures {@link DisputeCaseLink}, optional court stage, and administrative update exist
     * for a converted dispute. Safe to call repeatedly (idempotent).
     */
    private void syncConversionArtifacts(Dispute dispute, Person actor) {
        CaseFile caseFile = dispute.getConvertedCase();
        syncConversionArtifacts(dispute, caseFile, caseFile.getCaseNumber(), actor);
    }

    private void syncConversionArtifacts(
            Dispute dispute,
            CaseFile caseFile,
            String caseNumberForDescription,
            Person actor) {
        ensureConvertedCaseLink(dispute, caseFile);
        ensureCourtConversionStageIfAbsent(dispute);
        ensureConversionAdministrativeUpdateIfAbsent(
                dispute, caseFile, caseNumberForDescription, actor);
    }

    private void ensureConvertedCaseLink(Dispute dispute, CaseFile caseFile) {
        UUID disputeId = dispute.getId();
        UUID caseId = caseFile.getId();
        if (disputeCaseLinkRepository.existsByDispute_IdAndCaseFile_Id(disputeId, caseId)) {
            return;
        }
        DisputeCaseLink link = new DisputeCaseLink();
        link.setCompany(dispute.getCompany());
        link.setDispute(dispute);
        link.setCaseFile(caseFile);
        link.setLinkRole(CASE_LINK_ROLE_CONVERTED);
        try {
            disputeCaseLinkRepository.save(link);
        } catch (DataIntegrityViolationException ex) {
            if (disputeCaseLinkRepository.existsByDispute_IdAndCaseFile_Id(disputeId, caseId)) {
                return;
            }
            throw ex;
        }
    }

    private void ensureCourtConversionStageIfAbsent(Dispute dispute) {
        List<DisputeStage> stages = disputeStageRepository.findByDispute_IdOrderByCreatedAtAsc(dispute.getId());
        boolean hasActiveCourt = stages.stream().anyMatch(this::isActiveCourtStyleStage);
        if (hasActiveCourt) {
            return;
        }
        DisputeStage s = new DisputeStage();
        s.setCompany(dispute.getCompany());
        s.setDispute(dispute);
        s.setStageType("COURT_CASE");
        s.setStatus(DomainEnums.DisputeStageStatus.ACTIVE);
        s.setTitle("تحويل إلى قضية");
        s.setStartedAt(LocalDateTime.now());
        disputeStageRepository.save(s);
    }

    private boolean isActiveCourtStyleStage(DisputeStage s) {
        if (!isCourtStyleStageType(s.getStageType())) {
            return false;
        }
        if (s.getClosedAt() != null) {
            return false;
        }
        DomainEnums.DisputeStageStatus st = s.getStatus();
        return st != DomainEnums.DisputeStageStatus.CLOSED
                && st != DomainEnums.DisputeStageStatus.DONE
                && st != DomainEnums.DisputeStageStatus.CANCELLED;
    }

    private static boolean isCourtStyleStageType(String stageType) {
        if (stageType == null) {
            return false;
        }
        String u = stageType.toUpperCase(Locale.ROOT);
        return "COURT_CASE".equals(u) || "COURT_FILING".equals(u);
    }

    private void ensureConversionAdministrativeUpdateIfAbsent(
            Dispute dispute,
            CaseFile caseFile,
            String caseNumberForDescription,
            Person actor) {
        if (disputeAdministrativeUpdateRepository
                .existsByDispute_IdAndUpdateType(dispute.getId(), ADMIN_UPDATE_TYPE_CONVERTED)) {
            return;
        }
        DisputeAdministrativeUpdate u = new DisputeAdministrativeUpdate();
        u.setCompany(dispute.getCompany());
        u.setDispute(dispute);
        u.setStage(null);
        u.setUpdateType(ADMIN_UPDATE_TYPE_CONVERTED);
        u.setTitle("تم تحويل النزاع إلى قضية");
        u.setDescription(trimToNull("معرّف القضية: " + caseFile.getId()
                + " — رقم الملف: " + caseNumberForDescription));
        u.setOccurredAt(LocalDateTime.now());
        u.setCreatedBy(actor);
        disputeAdministrativeUpdateRepository.save(u);
    }

    private void ensureMutable(Dispute d) {
        if (d.getConvertedCase() != null || d.getStatus() == DomainEnums.DisputeStatus.CONVERTED_TO_CASE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot modify a dispute that has been converted to a case");
        }
    }

    private String uniqueDisputeCaseNumber(UUID disputeId) {
        String base = "DSP-" + disputeId.toString().replace("-", "");
        if (!caseFileRepository.existsByCaseNumberScoped(base)) {
            return base;
        }
        for (int i = 1; i < 1000; i++) {
            String candidate = base + "-" + i;
            if (!caseFileRepository.existsByCaseNumberScoped(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate case number for dispute " + disputeId);
    }

    private static DomainEnums.DisputePathType toCaseDisputePath(DomainEnums.LegalPath path) {
        if (path == null) {
            return DomainEnums.DisputePathType.CUSTOM;
        }
        return switch (path) {
            case COURT_CASE -> DomainEnums.DisputePathType.CIVIL;
            case CRIMINAL_REPORT -> DomainEnums.DisputePathType.CRIMINAL;
            case ARBITRATION -> DomainEnums.DisputePathType.ARBITRATION;
            case ADMINISTRATIVE_ACTION -> DomainEnums.DisputePathType.ADMINISTRATIVE;
            case LEGAL_NOTICE, NEGOTIATION, MEDIATION, FRIENDLY_SETTLEMENT -> DomainEnums.DisputePathType.CUSTOM;
        };
    }

    private static String toCaseTypeLabel(DomainEnums.DisputeType t) {
        return switch (t) {
            case CIVIL -> "مدني";
            case COMMERCIAL -> "تجاري";
            case LABOR -> "عمالي";
            case ADMINISTRATIVE -> "إداري";
            case CRIMINAL -> "جنائي";
            case FAMILY -> "أحوال شخصية";
            case REAL_ESTATE -> "عقاري";
            case OTHER -> "أخرى";
        };
    }

    private Dispute loadDispute(UUID id) {
        return disputeRepository.findScopedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dispute not found: " + id));
    }

    private void appendTimeline(
            Dispute dispute,
            DomainEnums.DisputeTimelineEventType type,
            String title,
            String description,
            Person actor) {
        DisputeTimelineEvent e = new DisputeTimelineEvent();
        e.setCompany(dispute.getCompany());
        e.setDispute(dispute);
        e.setEventType(type);
        e.setTitle(title);
        e.setDescription(trimToNull(description));
        e.setCreatedBy(actor);
        timelineEventRepository.save(e);
    }

    private DisputeResponse toDetailResponse(Dispute d) {
        List<DisputeTimelineEventResponse> timeline = timelineEventRepository
                .findByDisputeIdForDetail(d.getId())
                .stream()
                .map(this::toTimelineRow)
                .toList();

        return new DisputeResponse(
                d.getId(),
                d.getDisputeNumber(),
                d.getOpponentName(),
                d.getOpponentRole(),
                d.getTitle(),
                d.getDescription(),
                d.getDisputeType(),
                d.getStatus(),
                d.getLegalPath(),
                d.getCurrentStep(),
                d.getClaimAmount(),
                d.getRiskLevel(),
                d.getSuccessProbability(),
                toParty(d.getClient()),
                d.getClientRole(),
                toParty(d.getAssignedLawyer()),
                d.getConvertedCase() == null ? null : d.getConvertedCase().getId(),
                d.getCreatedAt(),
                d.getUpdatedAt(),
                timeline,
                disputeTeamService.listForDisputeDetail(d.getId()));
    }

    private DisputeListItemResponse toListItem(Dispute d) {
        return new DisputeListItemResponse(
                d.getId(),
                d.getDisputeNumber(),
                d.getTitle(),
                d.getOpponentName(),
                d.getDisputeType(),
                d.getStatus(),
                d.getLegalPath(),
                d.getCurrentStep(),
                d.getRiskLevel(),
                d.getClaimAmount(),
                toParty(d.getClient()),
                toParty(d.getAssignedLawyer()),
                d.getConvertedCase() == null ? null : d.getConvertedCase().getId(),
                d.getCreatedAt());
    }

    private DisputeTimelineEventResponse toTimelineRow(DisputeTimelineEvent e) {
        return new DisputeTimelineEventResponse(
                e.getId(),
                e.getEventType(),
                e.getTitle(),
                e.getDescription(),
                e.getCreatedAt(),
                toParty(e.getCreatedBy()));
    }

    private DisputeStageResponse toStageResponse(DisputeStage s) {
        return new DisputeStageResponse(
                s.getId(),
                s.getDispute().getId(),
                s.getStageType(),
                s.getStatus(),
                s.getTitle(),
                s.getStartedAt(),
                s.getClosedAt(),
                s.getOutcome(),
                s.getOutcomeNote(),
                s.getCreatedAt(),
                s.getUpdatedAt());
    }

    private DisputeCaseLinkResponse toCaseLinkResponse(DisputeCaseLink link) {
        return new DisputeCaseLinkResponse(
                link.getId(),
                link.getDispute().getId(),
                link.getCaseFile().getId(),
                link.getLinkRole(),
                link.getCreatedAt());
    }

    private DisputeAdministrativeUpdateResponse toAdministrativeUpdateResponse(DisputeAdministrativeUpdate u) {
        return new DisputeAdministrativeUpdateResponse(
                u.getId(),
                u.getDispute().getId(),
                u.getStage() == null ? null : u.getStage().getId(),
                u.getUpdateType(),
                u.getTitle(),
                u.getDescription(),
                u.getOccurredAt(),
                toParty(u.getCreatedBy()),
                u.getCreatedAt());
    }

    private PartySummary toParty(Person person) {
        if (person == null) {
            return null;
        }
        return new PartySummary(person.getId(), displayName(person), person.getSystemRole());
    }

    private static String displayName(Person person) {
        String n = person.getFullName();
        if (n != null && !n.isBlank()) {
            return n.trim();
        }
        if (person.getCompanyName() != null && !person.getCompanyName().isBlank()) {
            return person.getCompanyName().trim();
        }
        return person.getEmail() != null ? person.getEmail() : person.getId().toString();
    }

    private Person requireClient(UUID clientId) {
        Person client = personRepository.findScopedById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "client not found in this company: " + clientId));
        if (client.getSystemRole() != DomainEnums.SystemRole.CLIENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Referenced person is not a CLIENT: " + clientId);
        }
        if (!client.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Client is inactive: " + clientId);
        }
        return client;
    }

    private Person resolveLawyer(UUID lawyerId) {
        if (lawyerId == null) {
            return null;
        }
        Person lawyer = personRepository.findScopedById(lawyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "assignedLawyer not found in this company: " + lawyerId));
        if (!LAWYER_ROLES.contains(lawyer.getSystemRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "assignedLawyer must have role LAWYER or SENIOR_LAWYER: " + lawyerId);
        }
        if (!lawyer.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "assignedLawyer is inactive: " + lawyerId);
        }
        return lawyer;
    }

    private Company currentTenantReference() {
        UUID tenantId = TenantContext.getRequired();
        return companyRepository.getReferenceById(tenantId);
    }

    private static String requireText(String value, String field) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " is required");
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
