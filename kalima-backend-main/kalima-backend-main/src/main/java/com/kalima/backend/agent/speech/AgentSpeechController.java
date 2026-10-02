package com.kalima.backend.agent.speech;

import com.kalima.backend.agent.speech.dto.SpeechConfigResponse;
import com.kalima.backend.agent.speech.dto.SttResponse;
import com.kalima.backend.agent.speech.dto.TtsRequest;
import com.kalima.backend.agent.speech.dto.TtsResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cloud speech for the legal agent — Azure neural TTS + Groq Whisper STT.
 * Enables ChatGPT Voice–style quality when keys are configured.
 */
@RestController
@RequestMapping("/api/agent/speech")
public class AgentSpeechController {

    private final AgentSpeechService speechService;

    public AgentSpeechController(AgentSpeechService speechService) {
        this.speechService = speechService;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public SpeechConfigResponse config() {
        return speechService.config();
    }

    @PostMapping("/tts")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public TtsResponse tts(@Valid @RequestBody TtsRequest request) {
        return speechService.synthesize(request.text(), request.voice());
    }

    @PostMapping("/stt")
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public SttResponse stt(@RequestPart("file") MultipartFile file) {
        return speechService.transcribe(file);
    }
}
