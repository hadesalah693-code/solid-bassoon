package com.kalima.backend.delegation.intake.service;

import com.kalima.backend.casefile.dto.PartySummary;
import com.kalima.backend.delegation.dto.DelegationIntakeResponse;
import com.kalima.backend.delegation.settings.dto.DelegationAttorneyMatchResponse;
import com.kalima.backend.delegation.settings.service.DelegationOfficeAttorneyService;
import com.kalima.backend.delegation.repository.DelegationRepository;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.entity.Person;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.repository.PersonRepository;
import com.kalima.backend.ocr.entity.OcrRecord;
import com.kalima.backend.ocr.entity.OcrRelation;
import com.kalima.backend.ocr.repository.OcrRecordRepository;
import com.kalima.backend.ocr.repository.OcrRelationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw OCR text from a scanned power-of-attorney into suggested
 * delegation field values. Heuristic only — the user always confirms.
 */
@Service
@Transactional(readOnly = true)
public class DelegationIntakeService {

    private static final Pattern NOTARY_NUMBER = Pattern.compile(
            "(?:رقم\\s*(?:ال)?(?:توثيق|وثيقة|تسجيل)?\\s*[:：]?\\s*)?(\\d{4}\\s*/\\s*\\d{3,8})",
            Pattern.UNICODE_CASE);
    private static final Pattern DATE_DMY = Pattern.compile("(\\d{1,2})[./\\-](\\d{1,2})[./\\-](\\d{4})");
    private static final Pattern DATE_YMD = Pattern.compile("(\\d{4})[./\\-](\\d{1,2})[./\\-](\\d{1,2})");

    private static final Map<String, DomainEnums.DelegationType> TYPE_KEYWORDS = Map.ofEntries(
            Map.entry("توكيل عام", DomainEnums.DelegationType.GENERAL),
            Map.entry("عام", DomainEnums.DelegationType.GENERAL),
            Map.entry("توكيل خاص", DomainEnums.DelegationType.SPECIAL),
            Map.entry("خاص", DomainEnums.DelegationType.SPECIAL),
            Map.entry("قضائي", DomainEnums.DelegationType.JUDICIAL),
            Map.entry("تجاري", DomainEnums.DelegationType.COMMERCIAL),
            Map.entry("إداري", DomainEnums.DelegationType.ADMINISTRATIVE),
            Map.entry("اداري", DomainEnums.DelegationType.ADMINISTRATIVE),
            Map.entry("رسمي", DomainEnums.DelegationType.OFFICIAL));

    private final OcrRecordRepository ocrRecordRepository;
    private final PersonRepository personRepository;
    private final DelegationOfficeAttorneyService officeAttorneyService;
    private final DelegationRepository delegationRepository;
    private final OcrRelationRepository ocrRelationRepository;
    private final CompanyRepository companyRepository;

    public DelegationIntakeService(OcrRecordRepository ocrRecordRepository,
                                   PersonRepository personRepository,
                                   DelegationOfficeAttorneyService officeAttorneyService,
                                   DelegationRepository delegationRepository,
                                   OcrRelationRepository ocrRelationRepository,
                                   CompanyRepository companyRepository) {
        this.ocrRecordRepository = ocrRecordRepository;
        this.personRepository = personRepository;
        this.officeAttorneyService = officeAttorneyService;
        this.delegationRepository = delegationRepository;
        this.ocrRelationRepository = ocrRelationRepository;
        this.companyRepository = companyRepository;
    }

    public DelegationIntakeResponse parseFromOcrRecord(UUID ocrRecordId) {
        OcrRecord record = ocrRecordRepository.findScopedById(ocrRecordId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "OCR record not found: " + ocrRecordId));
        if (record.getStatus() != DomainEnums.OcrStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "OCR record is not ready for intake (status=" + record.getStatus() + ")");
        }

        String text = effectiveText(record);
        if (text.isBlank()) {
            return emptyResponse(ocrRecordId);
        }

        String notaryNumber = extractNotaryNumber(text);
        LocalDate issuedAt = extractLabeledDate(text, List.of("تاريخ الإصدار", "تاريخ اصدار", "صدر في"));
        LocalDate expiresAt = extractLabeledDate(text, List.of(
                "تاريخ انتهاء", "تاريخ الانتهاء", "ينتهي في", "سريان"));
        if (expiresAt == null) {
            expiresAt = extractLastDateAfterKeyword(text, List.of("انتهاء", "سريان"));
        }
        DomainEnums.DelegationType type = extractDelegationType(text);
        String clientName = extractLabeledName(text, List.of("الموكل", "موكل", "المُوَكِّل", "اسم الموكل"));
        String agentName = extractLabeledName(text, List.of("الوكيل", "وكيل", "اسم الوكيل", "الوكيل عن"));

        PartySummary suggestedClient = matchClient(clientName);
        DelegationAttorneyMatchResponse agentMatch = agentName != null && !agentName.isBlank()
                ? officeAttorneyService.matchByName(agentName)
                : DelegationAttorneyMatchResponse.none();

        String excerpt = text.length() > 500 ? text.substring(0, 500) + "…" : text;

        return new DelegationIntakeResponse(
                ocrRecordId,
                clientName,
                suggestedClient,
                agentName,
                agentMatch,
                notaryNumber,
                issuedAt,
                expiresAt,
                type,
                "QA",
                excerpt);
    }

    /**
     * Links a completed OCR intake record to the saved delegation for audit/search.
     */
    @Transactional
    public void linkOcrRecord(UUID delegationId, UUID ocrRecordId) {
        delegationRepository.findScopedById(delegationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "delegation not found: " + delegationId));
        OcrRecord record = ocrRecordRepository.findScopedById(ocrRecordId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "OCR record not found: " + ocrRecordId));
        boolean exists = record.getRelations().stream()
                .anyMatch(r -> r.getRelationType() == DomainEnums.OcrRelationType.DELEGATION
                        && r.getRelationId().equals(delegationId));
        if (exists) {
            return;
        }
        OcrRelation relation = new OcrRelation();
        relation.setCompany(companyRepository.getReferenceById(record.getCompany().getId()));
        relation.setOcrRecord(record);
        relation.setRelationType(DomainEnums.OcrRelationType.DELEGATION);
        relation.setRelationId(delegationId);
        ocrRelationRepository.save(relation);
    }

    private static DelegationIntakeResponse emptyResponse(UUID ocrRecordId) {
        return new DelegationIntakeResponse(
                ocrRecordId, null, null, null,
                DelegationAttorneyMatchResponse.none(),
                null, null, null, null, "QA", null);
    }

    private static String effectiveText(OcrRecord record) {
        if (record.getEditedText() != null && !record.getEditedText().isBlank()) {
            return record.getEditedText().trim();
        }
        return record.getExtractedText() != null ? record.getExtractedText().trim() : "";
    }

    private static String extractNotaryNumber(String text) {
        Matcher m = NOTARY_NUMBER.matcher(text);
        if (m.find()) {
            return m.group(1).replaceAll("\\s+", "");
        }
        return null;
    }

    private static LocalDate extractLabeledDate(String text, List<String> labels) {
        for (String label : labels) {
            int idx = indexOfIgnoreCase(text, label);
            if (idx < 0) {
                continue;
            }
            String slice = text.substring(idx, Math.min(text.length(), idx + 80));
            LocalDate parsed = parseFirstDate(slice);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private static LocalDate extractLastDateAfterKeyword(String text, List<String> keywords) {
        LocalDate found = null;
        for (String keyword : keywords) {
            int idx = indexOfIgnoreCase(text, keyword);
            while (idx >= 0) {
                String slice = text.substring(idx, Math.min(text.length(), idx + 80));
                LocalDate parsed = parseFirstDate(slice);
                if (parsed != null) {
                    found = parsed;
                }
                idx = indexOfIgnoreCase(text, keyword, idx + keyword.length());
            }
        }
        return found;
    }

    private static LocalDate parseFirstDate(String slice) {
        Matcher ymd = DATE_YMD.matcher(slice);
        if (ymd.find()) {
            return safeDate(Integer.parseInt(ymd.group(1)),
                    Integer.parseInt(ymd.group(2)),
                    Integer.parseInt(ymd.group(3)));
        }
        Matcher dmy = DATE_DMY.matcher(slice);
        if (dmy.find()) {
            return safeDate(Integer.parseInt(dmy.group(3)),
                    Integer.parseInt(dmy.group(2)),
                    Integer.parseInt(dmy.group(1)));
        }
        return null;
    }

    private static LocalDate safeDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static DomainEnums.DelegationType extractDelegationType(String text) {
        String normalized = normalizeForMatch(text);
        DomainEnums.DelegationType best = null;
        int bestLen = 0;
        for (Map.Entry<String, DomainEnums.DelegationType> entry : TYPE_KEYWORDS.entrySet()) {
            String key = normalizeForMatch(entry.getKey());
            if (normalized.contains(key) && key.length() > bestLen) {
                best = entry.getValue();
                bestLen = key.length();
            }
        }
        return best;
    }

    private static String extractLabeledName(String text, List<String> labels) {
        for (String label : labels) {
            int idx = indexOfIgnoreCase(text, label);
            if (idx < 0) {
                continue;
            }
            String after = text.substring(idx + label.length()).trim();
            after = after.replaceFirst("^[:：/\\-]+\\s*", "");
            if (after.isEmpty()) {
                continue;
            }
            // Take until newline or a common next-field label.
            int end = after.length();
            for (String stop : new String[] {"\n", "الوكيل", "الموكل", "تاريخ", "رقم", "نوع"}) {
                int stopIdx = indexOfIgnoreCase(after, stop);
                if (stopIdx > 0 && stopIdx < end) {
                    end = stopIdx;
                }
            }
            String name = after.substring(0, end).trim();
            name = name.replaceAll("\\d{4}/\\d+.*$", "").trim();
            if (name.length() >= 3) {
                return name;
            }
        }
        return null;
    }

    private PartySummary matchClient(String clientName) {
        if (clientName == null || clientName.isBlank()) {
            return null;
        }
        String needle = normalizeForMatch(clientName);
        var page = personRepository.searchActive(
                DomainEnums.SystemRole.CLIENT,
                clientName.trim(),
                PageRequest.of(0, 10));
        PartySummary exact = null;
        PartySummary partial = null;
        for (Person person : page.getContent()) {
            String candidate = normalizeForMatch(displayName(person));
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.equals(needle)) {
                exact = toParty(person);
                break;
            }
            if (partial == null && (candidate.contains(needle) || needle.contains(candidate))) {
                partial = toParty(person);
            }
        }
        return exact != null ? exact : partial;
    }

    private static PartySummary toParty(Person person) {
        return new PartySummary(person.getId(), displayName(person), person.getSystemRole());
    }

    private static String displayName(Person person) {
        if (person.getFullName() != null && !person.getFullName().isBlank()) {
            return person.getFullName().trim();
        }
        if (person.getCompanyName() != null && !person.getCompanyName().isBlank()) {
            return person.getCompanyName().trim();
        }
        return person.getEmail();
    }

    private static int indexOfIgnoreCase(String haystack, String needle) {
        return indexOfIgnoreCase(haystack, needle, 0);
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int fromIndex) {
        return haystack.toLowerCase(Locale.ROOT)
                .indexOf(needle.toLowerCase(Locale.ROOT), fromIndex);
    }

    private static String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
