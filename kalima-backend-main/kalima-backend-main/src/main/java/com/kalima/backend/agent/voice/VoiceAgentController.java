package com.kalima.backend.agent.voice;

import com.kalima.backend.agent.voice.dto.VoiceAgentRequest;
import com.kalima.backend.agent.voice.dto.VoiceAgentResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Voice Legal Agent — accepts transcribed speech (STT on client) and executes
 * real backend actions via the tool registry. Does not replace the case
 * chatbot or lawyer assistant modules.
 */
@RestController
@RequestMapping("/api/agent/voice")
public class VoiceAgentController {

    private final VoiceAgentService voiceAgentService;

    public VoiceAgentController(VoiceAgentService voiceAgentService) {
        this.voiceAgentService = voiceAgentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SENIOR_LAWYER','LAWYER','SECRETARY','ACCOUNTANT')")
    public VoiceAgentResponse voice(@Valid @RequestBody VoiceAgentRequest request) {
        return voiceAgentService.handle(request);
    }
}
