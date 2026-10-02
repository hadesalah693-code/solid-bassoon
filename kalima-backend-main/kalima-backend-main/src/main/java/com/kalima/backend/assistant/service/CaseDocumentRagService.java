package com.kalima.backend.assistant.service;

import com.kalima.backend.assistant.config.AssistantRagProperties;
import com.kalima.backend.casedocument.entity.CaseDocument;
import com.kalima.backend.casedocument.repository.CaseDocumentRepository;
import com.kalima.backend.casefile.service.CaseAccessService;
import com.kalima.backend.domain.entity.Person;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lightweight keyword RAG over case document text — no vector DB. Retrieves
 * top-scoring chunks and formats them for prompt injection.
 */
@Service
@Transactional(readOnly = true)
public class CaseDocumentRagService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[\\s\\p{Punct}]+");

    private final AssistantRagProperties props;
    private final CaseDocumentRepository documentRepository;
    private final CaseAccessService caseAccessService;
    private final CaseDocumentTextExtractor textExtractor;

    public CaseDocumentRagService(AssistantRagProperties props,
                                  CaseDocumentRepository documentRepository,
                                  CaseAccessService caseAccessService,
                                  CaseDocumentTextExtractor textExtractor) {
        this.props = props;
        this.documentRepository = documentRepository;
        this.caseAccessService = caseAccessService;
        this.textExtractor = textExtractor;
    }

    public record RagResult(String promptBlock, boolean documentsUsed) {
        public static RagResult empty() {
            return new RagResult("", false);
        }
    }

    public RagResult retrieve(UUID caseId, String question, Person actor) {
        if (!props.isEnabled() || caseId == null || question == null || question.isBlank()) {
            return RagResult.empty();
        }
        caseAccessService.requireReadableCase(caseId, actor);

        List<CaseDocument> docs = documentRepository.findByCaseId(
                caseId, null, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (docs.isEmpty()) {
            return RagResult.empty();
        }

        int docLimit = Math.min(props.getMaxDocuments(), docs.size());
        Set<String> queryTokens = tokenize(question);
        boolean documentIntent = wantsDocumentContext(question);
        List<ScoredChunk> scored = new ArrayList<>();

        for (int i = 0; i < docLimit; i++) {
            CaseDocument doc = docs.get(i);
            String text = textExtractor.extract(doc);
            if (text.isBlank()) {
                continue;
            }
            List<String> chunks = chunk(text, props.getChunkSize());
            for (String chunkText : chunks) {
                int score = scoreChunk(chunkText, queryTokens);
                if (score > 0) {
                    scored.add(new ScoredChunk(
                            score,
                            doc.getOriginalFilename(),
                            chunkText));
                }
            }
            if (scored.isEmpty() && documentIntent && i == 0) {
                for (int c = 0; c < Math.min(2, chunks.size()); c++) {
                    scored.add(new ScoredChunk(
                            1,
                            doc.getOriginalFilename(),
                            chunks.get(c)));
                }
            }
        }

        if (scored.isEmpty()) {
            return RagResult.empty();
        }

        scored.sort(Comparator.comparingInt(ScoredChunk::score).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n=== مقتطفات من مستندات القضية ===\n");
        sb.append("استخدم هذه المقتطفات عند السؤال عن محتوى المستندات. ")
                .append("لا تختلق نصوصاً غير موجودة هنا.\n\n");

        int chunksUsed = 0;
        int charsUsed = 0;
        for (ScoredChunk item : scored) {
            if (chunksUsed >= props.getMaxChunks()) {
                break;
            }
            String block = "[مستند: " + nullSafe(item.filename()) + "]\n" + item.chunk() + "\n\n";
            if (charsUsed + block.length() > props.getMaxChars()) {
                break;
            }
            sb.append(block);
            charsUsed += block.length();
            chunksUsed++;
        }

        if (chunksUsed == 0) {
            return RagResult.empty();
        }

        sb.append("=== نهاية مقتطفات المستندات ===\n");
        return new RagResult(sb.toString(), true);
    }

    private static List<String> chunk(String text, int size) {
        List<String> parts = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int end = Math.min(text.length(), i + size);
            String slice = text.substring(i, end).trim();
            if (!slice.isEmpty()) {
                parts.add(slice);
            }
            i = end;
        }
        return parts;
    }

    private static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String part : TOKEN_SPLIT.split(normalized)) {
            String t = part.trim();
            if (t.length() >= 2) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    private static int scoreChunk(String chunk, Set<String> queryTokens) {
        if (queryTokens.isEmpty()) {
            return 0;
        }
        String lower = chunk.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String token : queryTokens) {
            if (lower.contains(token)) {
                score++;
            }
        }
        return score;
    }

    private static boolean wantsDocumentContext(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        return q.contains("مستند")
                || q.contains("مستندات")
                || q.contains("وثيقة")
                || q.contains("وثائق")
                || q.contains("ملف")
                || q.contains("محتو")
                || q.contains("document")
                || q.contains("pdf");
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private record ScoredChunk(int score, String filename, String chunk) {
    }
}
