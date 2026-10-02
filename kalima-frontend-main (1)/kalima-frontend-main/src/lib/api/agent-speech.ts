import { request } from "@/lib/api/client";

export type SpeechVoiceOption = {
  id: string;
  label: string;
  lang: string;
};

export type SpeechConfig = {
  ttsProvider: "AZURE" | "BROWSER";
  sttProvider: "GROQ_WHISPER" | "BROWSER";
  voice: string | null;
  voiceConversationReady: boolean;
  availableVoices?: SpeechVoiceOption[];
};

export type TtsResponse = {
  audioBase64: string;
  contentType: string;
  provider: string;
  voice: string;
};

export type SttResponse = {
  text: string;
  provider: string;
};

export const agentSpeechApi = {
  config: () => request<SpeechConfig>("/agent/speech/config"),

  tts: (text: string, voice?: string | null) =>
    request<TtsResponse>("/agent/speech/tts", {
      method: "POST",
      body: { text, voice: voice ?? undefined },
    }),

  stt: (blob: Blob, filename = "speech.webm") => {
    const form = new FormData();
    form.append("file", blob, filename);
    return request<SttResponse>("/agent/speech/stt", {
      method: "POST",
      body: form,
    });
  },
};
