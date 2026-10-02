package com.kalima.backend.chatbot.service;

import com.kalima.backend.auth.support.CurrentUserService;
import com.kalima.backend.casedocument.repository.CaseDocumentRepository;
import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.casefile.entity.CaseFile;
import com.kalima.backend.casefile.repository.CaseFileRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.chatbot.dto.ChatbotAskRequest;
import com.kalima.backend.chatbot.dto.ChatbotAskResponse;
import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Message;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.MessageRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.hearing.entity.Hearing;
import com.kalima.backend.hearing.repository.HearingRepository;
import com.kalima.backend.message.dto.MessageResponse;
import com.kalima.backend.realtime.service.MessageBroadcaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight Arabic-keyword-based case chatbot. Detects intent from the
 * question text, then answers using existing repositories. No external LLM.
 *
 * <p>Bot replies are persisted as {@link Message} rows with
 * {@code senderType = BOT} so they appear inline in the case conversation
 * thread for the asker. The DB CHECK constraint
 * {@code chk_messages_sender_user_match_type} guarantees that BOT rows have
 * no Person sender.
 *
 * <p>Access control mirrors {@link com.kalima.backend.message.service.MessageService}
 * — CLIENTs only see their own case; staff role gating matches existing rules.
 */
@Service
@Transactional
public class CaseChatbotService {

    /** Internal label sent back so the frontend can style/debug the answer. */
    public enum Intent {
        EXPLICIT_LAWYER_REQUEST,
        LAWYER_HANDOFF,
        NEXT_HEARING,
        CASE_STATUS,
        DOCUMENTS,
        ASSIGNED_LAWYER,
        LAST_UPDATE,
        POSTPONEMENT,
        UNKNOWN
    }

    /**
     * Arabic / English keywords for each intent. Order matters: the first
     * matching intent wins. Compared case-insensitively against the
     * normalised question text.
     *
     * <p>Order is significant — POSTPONEMENT is checked before NEXT_HEARING so
     * that "هل اتأجلت الجلسة؟" matches POSTPONEMENT instead of NEXT_HEARING.
     * ASSIGNED_LAWYER is checked before CASE_STATUS for similar reasons.
     */
    private static final Map<Intent, List<String>> INTENT_KEYWORDS;

    static {
        Map<Intent, List<String>> m = new LinkedHashMap<>();
        m.put(Intent.EXPLICIT_LAWYER_REQUEST, List.of(
                "تحدث مع المحامي", "التحدث مع المحامي", "اكلم المحامي",
                "أكلم المحامي", "اتكلم مع المحامي", "كلم المحامي",
                "تواصل مع المحامي", "عايز محامي", "عاوز محامي",
                "محامي", "تواصل", "كلم", "اكلم", "أكلم", "اتكلم"));
        m.put(Intent.LAWYER_HANDOFF, List.of(
                "رأي قانوني", "راي قانوني", "رأيك", "رايك", "رأي", "راي",
                "استشارة", "نصيحة", "تنصح",
                "اعمل ايه", "أعمل ايه", "أعمل إيه", "ماذا أفعل",
                "قرار", "توصية", "استراتيجية", "خطة دفاع", "ارفع",
                "أرفع", "أرد", "ارد", "اكسب",
                "أكسب", "اخسر", "أخسر", "مضمون", "احتمال", "مسؤولية",
                "legal advice", "recommend", "opinion", "strategy"));
        m.put(Intent.POSTPONEMENT, List.of(
                "اتأجل", "اتأجلت", "تأجلت", "تأجيل", "في تأجيل",
                "مؤجل", "مؤجلة", "تم التأجيل",
                "postpon", "adjourn"));
        m.put(Intent.LAST_UPDATE, List.of(
                "آخر تحديث", "اخر تحديث", "ايه الجديد", "إيه الجديد",
                "ايه اخبار", "آخر شيء", "آخر أخبار", "اخر اخبار",
                "last update", "what's new", "whats new", "latest"));
        m.put(Intent.NEXT_HEARING, List.of(
                "جلسة", "الجلسة", "جلسات", "موعد", "ميعاد",
                "next hearing", "hearing"));
        m.put(Intent.CASE_STATUS, List.of(
                "حالة", "الحالة", "وضع", "الوضع", "ستاتس",
                "status"));
        m.put(Intent.DOCUMENTS, List.of(
                "مستند", "مستندات", "وثائق", "ملف", "ملفات",
                "documents", "files"));
        m.put(Intent.ASSIGNED_LAWYER, List.of(
                "مين ماسك القضية", "المحامي المسؤول عن القضية",
                "assigned lawyer"));
        INTENT_KEYWORDS = m;
    }

    /** Words that mark a hearing result as a postponement / adjournment. */
    private static final List<String> POSTPONEMENT_RESULT_KEYWORDS = List.of(
            "تأجل", "اتأجل", "تأجيل", "مؤجل", "مؤجلة",
            "postpon", "adjourn");

    private static final DateTimeFormatter HEARING_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CaseFileRepository caseFileRepository;
    private final HearingRepository hearingRepository;
    private final CaseDocumentRepository caseDocumentRepository;
    private final MessageRepository messageRepository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final MessageBroadcaster broadcaster;
    private final CaseAccessService caseAccessService;

    public CaseChatbotService(CaseFileRepository caseFileRepository,
                              HearingRepository hearingRepository,
                              CaseDocumentRepository caseDocumentRepository,
                              MessageRepository messageRepository,
                              CompanyRepository companyRepository,
                              CurrentUserService currentUserService,
                              MessageBroadcaster broadcaster,
                              CaseAccessService caseAccessService) {
        this.caseFileRepository = caseFileRepository;
        this.hearingRepository = hearingRepository;
        this.caseDocumentRepository = caseDocumentRepository;
        this.messageRepository = messageRepository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.broadcaster = broadcaster;
        this.caseAccessService = caseAccessService;
    }

    public ChatbotAskResponse ask(UUID caseId, ChatbotAskRequest request) {
        CaseFile caseFile = loadCase(caseId);
        Person actor = currentUserService.requireCurrentPerson();
        caseAccessService.requireReadableCase(caseFile, actor);

        String question = request.question() == null
                ? ""
                : request.question().trim();
        if (question.isEmpty()) {
            return persistAndReturn(caseFile, actor, fallback());
        }

        Intent intent = detectIntent(question);
        ChatbotAskResponse answer = switch (intent) {
            case EXPLICIT_LAWYER_REQUEST -> explicitLawyerRequest();
            case LAWYER_HANDOFF -> lawyerHandoff();
            case NEXT_HEARING -> answerNextHearing(caseFile);
            case CASE_STATUS -> answerCaseStatus(caseFile);
            case DOCUMENTS -> answerDocuments(caseFile);
            case ASSIGNED_LAWYER -> answerAssignedLawyer(caseFile);
            case LAST_UPDATE -> answerLastUpdate(caseFile);
            case POSTPONEMENT -> answerPostponement(caseFile);
            case UNKNOWN -> lawyerHandoff();
        };
        return persistAndReturn(caseFile, actor, answer);
    }

    /* ─── intent detection ─────────────────────────────────────── */

    static Intent detectIntent(String question) {
        String haystack = question.toLowerCase(Locale.ROOT);
        for (Map.Entry<Intent, List<String>> e : INTENT_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (haystack.contains(kw.toLowerCase(Locale.ROOT))) {
                    return e.getKey();
                }
            }
        }
        return Intent.UNKNOWN;
    }

    /* ─── answer builders ──────────────────────────────────────── */

    private ChatbotAskResponse answerNextHearing(CaseFile caseFile) {
        // Pull a small window of hearings for this case ordered by date asc and
        // pick the soonest upcoming one. Using the existing list query keeps
        // the tenant filter active.
        Page<Hearing> page = hearingRepository.findByCaseIdForList(
                caseFile.getId(),
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "hearingDate")));
        LocalDateTime now = LocalDateTime.now();
        Hearing upcoming = page.getContent().stream()
                .filter(h -> h.getHearingDate() != null
                        && !h.getHearingDate().isBefore(now))
                .findFirst()
                .orElse(null);

        if (upcoming == null) {
            return new ChatbotAskResponse(
                    "لا توجد جلسات قادمة مسجّلة على هذه القضية حالياً.",
                    Intent.NEXT_HEARING.name());
        }

        String when = upcoming.getHearingDate().format(HEARING_FORMAT);
        String court = upcoming.getCourtName() == null
                ? "—"
                : upcoming.getCourtName();
        String chamber = upcoming.getChamber();
        String tail = (chamber == null || chamber.isBlank())
                ? ""
                : " — الدائرة: " + chamber;
        return new ChatbotAskResponse(
                "أقرب جلسة قادمة بتاريخ " + when + " في " + court + tail + ".",
                Intent.NEXT_HEARING.name());
    }

    private ChatbotAskResponse answerCaseStatus(CaseFile caseFile) {
        DomainEnums.CaseStatus status = caseFile.getStatus();
        boolean archived = caseFile.getArchivedAt() != null;
        String statusAr = arabicStatus(status);
        String archiveTail = archived
                ? " (مؤرشفة)"
                : "";
        return new ChatbotAskResponse(
                "حالة القضية الحالية: " + statusAr + archiveTail + ".",
                Intent.CASE_STATUS.name());
    }

    private ChatbotAskResponse answerDocuments(CaseFile caseFile) {
        List<?> docs = caseDocumentRepository.findByCaseId(
                caseFile.getId(), null, Sort.by(Sort.Direction.DESC, "createdAt"));
        int count = docs.size();
        String body;
        if (count == 0) {
            body = "لا توجد مستندات مرفقة بهذه القضية حتى الآن.";
        } else if (count == 1) {
            body = "يوجد مستند واحد مرفق بهذه القضية.";
        } else if (count == 2) {
            body = "يوجد مستندان مرفقان بهذه القضية.";
        } else {
            body = "يوجد " + count + " مستندات مرفقة بهذه القضية.";
        }
        return new ChatbotAskResponse(body, Intent.DOCUMENTS.name());
    }

    private ChatbotAskResponse answerAssignedLawyer(CaseFile caseFile) {
        Person lawyer = caseFile.getAssignedLawyer();
        if (lawyer == null) {
            return new ChatbotAskResponse(
                    "لم يتم تعيين محامي مسؤول على هذه القضية بعد.",
                    Intent.ASSIGNED_LAWYER.name());
        }
        String name = lawyer.getFullName() == null || lawyer.getFullName().isBlank()
                ? "—"
                : lawyer.getFullName();
        return new ChatbotAskResponse(
                "المحامي المسؤول عن هذه القضية: " + name + ".",
                Intent.ASSIGNED_LAWYER.name());
    }

    private ChatbotAskResponse answerLastUpdate(CaseFile caseFile) {
        // Compare three signals and report the freshest one:
        //   - latest hearing on this case (by hearingDate desc)
        //   - latest message on this case (by createdAt desc)
        //   - the case row's own updatedAt
        Page<Hearing> hearingsPage = hearingRepository.findByCaseIdForList(
                caseFile.getId(),
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "hearingDate")));
        Hearing latestHearing = hearingsPage.getContent().isEmpty()
                ? null
                : hearingsPage.getContent().get(0);

        List<Message> messages = messageRepository.findConversationForStaff(caseFile.getId());
        Message latestMessage = messages.isEmpty()
                ? null
                : messages.get(messages.size() - 1);

        LocalDateTime hearingTs = latestHearing == null
                ? null
                : latestHearing.getHearingDate();
        LocalDateTime messageTs = latestMessage == null
                ? null
                : latestMessage.getCreatedAt();
        LocalDateTime caseUpdatedTs = caseFile.getUpdatedAt();

        LocalDateTime best = null;
        String bestLabel = null;
        if (hearingTs != null) {
            best = hearingTs;
            bestLabel = "آخر جلسة بتاريخ " + hearingTs.format(HEARING_FORMAT);
        }
        if (messageTs != null && (best == null || messageTs.isAfter(best))) {
            best = messageTs;
            bestLabel = "آخر رسالة في المحادثة بتاريخ "
                    + messageTs.format(HEARING_FORMAT);
        }
        if (caseUpdatedTs != null && (best == null || caseUpdatedTs.isAfter(best))) {
            best = caseUpdatedTs;
            bestLabel = "آخر تحديث على ملف القضية بتاريخ "
                    + caseUpdatedTs.format(HEARING_FORMAT);
        }

        if (bestLabel == null) {
            return new ChatbotAskResponse(
                    "لا يوجد أي نشاط مسجّل على هذه القضية حتى الآن.",
                    Intent.LAST_UPDATE.name());
        }
        return new ChatbotAskResponse(bestLabel + ".", Intent.LAST_UPDATE.name());
    }

    private ChatbotAskResponse answerPostponement(CaseFile caseFile) {
        Page<Hearing> page = hearingRepository.findByCaseIdForList(
                caseFile.getId(),
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "hearingDate")));
        if (page.getContent().isEmpty()) {
            return new ChatbotAskResponse(
                    "لا توجد جلسات مسجّلة على هذه القضية حتى الآن.",
                    Intent.POSTPONEMENT.name());
        }
        Hearing latest = page.getContent().get(0);
        boolean postponed = isPostponed(latest);
        if (!postponed) {
            return new ChatbotAskResponse(
                    "لا يوجد تأجيل مسجّل على آخر جلسة بهذه القضية.",
                    Intent.POSTPONEMENT.name());
        }
        StringBuilder sb = new StringBuilder("تم تأجيل آخر جلسة (")
                .append(latest.getHearingDate().format(HEARING_FORMAT))
                .append(")");
        if (latest.getNextHearingDate() != null) {
            sb.append(" إلى تاريخ ")
                    .append(latest.getNextHearingDate().format(HEARING_FORMAT));
        }
        sb.append(".");
        return new ChatbotAskResponse(sb.toString(), Intent.POSTPONEMENT.name());
    }

    private static boolean isPostponed(Hearing hearing) {
        if (hearing.getNextHearingDate() != null) {
            return true;
        }
        String result = hearing.getResult();
        if (result == null || result.isBlank()) {
            return false;
        }
        String lower = result.toLowerCase(Locale.ROOT);
        for (String kw : POSTPONEMENT_RESULT_KEYWORDS) {
            if (lower.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private ChatbotAskResponse fallback() {
        return lawyerHandoff();
    }

    private ChatbotAskResponse lawyerHandoff() {
        return new ChatbotAskResponse(
                "السؤال ده يحتاج مراجعة من المحامي المختص. تم تحويل رسالتك للمحامي وسيقوم بالرد عليك.",
                Intent.LAWYER_HANDOFF.name());
    }

    private ChatbotAskResponse explicitLawyerRequest() {
        return new ChatbotAskResponse(
                "تم تحويل طلبك للمحامي المختص. يمكنك كتابة رسالتك هنا وسيقوم بالرد عليك.",
                Intent.EXPLICIT_LAWYER_REQUEST.name());
    }

    private static String arabicStatus(DomainEnums.CaseStatus status) {
        if (status == null) {
            return "غير محددة";
        }
        return switch (status) {
            case NEW -> "جديدة";
            case IN_PROGRESS -> "قيد التنفيذ";
            case ON_HOLD -> "معلّقة";
            case CLOSED -> "مغلقة";
            case ARCHIVED -> "مؤرشفة";
        };
    }

    /* ─── persistence ──────────────────────────────────────────── */

    /**
     * Persist the chatbot reply as a Message row so it shows up inline in the
     * case conversation for the asker, then return the original response
     * untouched. The receiver is the actor — that way:
     * <ul>
     *   <li>existing CLIENT participant filter
     *       ({@code findConversationForParticipant}) still surfaces it</li>
     *   <li>another participant in the same case won't see someone else's
     *       private bot question/answer</li>
     * </ul>
     */
    private ChatbotAskResponse persistAndReturn(
            CaseFile caseFile, Person actor, ChatbotAskResponse answer) {
        Message m = new Message();
        m.setCompany(currentTenantReference());
        m.setCaseFile(caseFile);
        m.setSenderUser(null);
        m.setReceiverUser(actor);
        m.setSenderType(DomainEnums.MessageSenderType.BOT);
        m.setContent(answer.answer());
        Message saved = messageRepository.save(m);
        broadcaster.broadcastToCase(caseFile.getId(), toResponse(saved));
        return answer;
    }

    private static MessageResponse toResponse(Message m) {
        Person rcv = m.getReceiverUser();
        PartySummary receiver = rcv == null
                ? null
                : new PartySummary(rcv.getId(), rcv.getFullName(), rcv.getSystemRole());
        return new MessageResponse(
                m.getId(),
                m.getCaseFile().getId(),
                null,
                receiver,
                m.getSenderType(),
                m.getContent(),
                m.getAttachmentUrl(),
                null,             // replyTo — chatbot never replies to a specific message
                m.isRead(),
                m.getReadAt(),
                m.getCreatedAt(),
                java.util.Map.of(),
                null);
    }

    private Company currentTenantReference() {
        return companyRepository.getReferenceById(TenantContext.getRequired());
    }

    private CaseFile loadCase(UUID caseId) {
        return caseFileRepository.findScopedById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Case not found: " + caseId));
    }
}
