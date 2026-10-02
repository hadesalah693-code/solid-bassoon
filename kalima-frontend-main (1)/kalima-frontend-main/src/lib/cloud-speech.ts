import { agentSpeechApi, type SpeechConfig } from "@/lib/api/agent-speech";
import { speakAsync, stopSpeaking, getStoredVoiceName, type SpeakOptions } from "@/lib/tts";

let cachedConfig: SpeechConfig | null = null;
let configPromise: Promise<SpeechConfig> | null = null;
let playingAudio: HTMLAudioElement | null = null;

type SpeechRecognitionResultLike = {
  readonly length: number;
  item(index: number): { transcript: string };
  [index: number]: { transcript: string };
};

type SpeechRecognitionEventLike = Event & {
  readonly resultIndex: number;
  readonly results: {
    readonly length: number;
    item(index: number): SpeechRecognitionResultLike;
    [index: number]: SpeechRecognitionResultLike;
  };
};

type SpeechRecognitionErrorEventLike = Event & {
  readonly error: string;
};

type SpeechRecognitionLike = {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  maxAlternatives: number;
  onresult: ((event: SpeechRecognitionEventLike) => void) | null;
  onerror: ((event: SpeechRecognitionErrorEventLike) => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
  abort: () => void;
};

type SpeechRecognitionConstructor = new () => SpeechRecognitionLike;

export async function loadSpeechConfig(): Promise<SpeechConfig> {
  if (cachedConfig) return cachedConfig;
  if (!configPromise) {
    configPromise = agentSpeechApi.config().then((cfg) => {
      cachedConfig = cfg;
      return cfg;
    });
  }
  return configPromise;
}

export function getSpeechConfig(): SpeechConfig | null {
  return cachedConfig;
}

export function isNeuralTtsAvailable(): boolean {
  return cachedConfig?.ttsProvider === "AZURE";
}

export function isCloudSttAvailable(): boolean {
  return cachedConfig?.sttProvider === "GROQ_WHISPER";
}

export function stopCloudSpeech(): void {
  if (playingAudio) {
    playingAudio.pause();
    playingAudio.src = "";
    playingAudio = null;
  }
  stopSpeaking();
}

/** Azure neural TTS when configured; otherwise browser Web Speech API. */
export async function speakAssistant(
  text: string,
  options?: SpeakOptions,
): Promise<void> {
  const trimmed = text?.trim();
  if (!trimmed) return;

  stopCloudSpeech();

  const cfg = cachedConfig ?? (await loadSpeechConfig());
  if (cfg.ttsProvider === "AZURE") {
    const voice =
      options?.voiceName ?? getStoredVoiceName() ?? cfg.voice ?? undefined;
    const res = await agentSpeechApi.tts(trimmed, voice);
    playingAudio = new Audio(
      `data:${res.contentType};base64,${res.audioBase64}`,
    );
    await new Promise<void>((resolve, reject) => {
      if (!playingAudio) {
        resolve();
        return;
      }
      playingAudio.onended = () => {
        playingAudio = null;
        options?.onEnd?.();
        resolve();
      };
      playingAudio.onerror = () => {
        playingAudio = null;
        options?.onEnd?.();
        reject(new Error("Audio playback failed"));
      };
      void playingAudio.play().catch(reject);
    });
    return;
  }

  await speakAsync(trimmed, options);
}

function extractTranscript(event: SpeechRecognitionEventLike): string {
  const parts: string[] = [];
  for (let i = event.resultIndex; i < event.results.length; i++) {
    const result = event.results[i] ?? event.results.item(i);
    const first = result[0] ?? result.item(0);
    if (first?.transcript) parts.push(first.transcript);
  }
  return parts.join(" ").trim();
}

/** One utterance: Groq Whisper when configured, else browser STT. */
export async function listenOnce(
  getRecognition: () => SpeechRecognitionConstructor | null,
): Promise<string | null> {
  const Recognition = getRecognition();
  if (!Recognition) return null;

  const cfg = cachedConfig ?? (await loadSpeechConfig());

  if (cfg.sttProvider !== "GROQ_WHISPER") {
    return new Promise((resolve) => {
      const recognition = new Recognition();
      recognition.lang = "ar-EG";
      recognition.continuous = false;
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      let settled = false;
      recognition.onresult = (event) => {
        settled = true;
        resolve(extractTranscript(event) || null);
      };
      recognition.onerror = () => {
        if (!settled) resolve(null);
      };
      recognition.onend = () => {
        if (!settled) resolve(null);
      };
      recognition.start();
    });
  }

  const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
  const recorder = new MediaRecorder(stream);
  const chunks: BlobPart[] = [];
  recorder.ondataavailable = (event) => {
    if (event.data.size > 0) chunks.push(event.data);
  };

  return new Promise((resolve) => {
    let settled = false;
    const recognition = new Recognition();
    recognition.lang = "ar-EG";
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    const finish = (text: string | null) => {
      if (settled) return;
      settled = true;
      window.clearTimeout(timeout);
      try {
        recognition.stop();
      } catch {
        /* ignore */
      }
      stream.getTracks().forEach((t) => t.stop());
      resolve(text);
    };

    recognition.onend = () => {
      if (recorder.state !== "inactive") {
        recorder.stop();
      }
    };

    recognition.onerror = () => {
      if (recorder.state !== "inactive") {
        recorder.stop();
      } else if (!settled) {
        finish(null);
      }
    };

    const timeout = window.setTimeout(() => {
      if (recorder.state !== "inactive") {
        recorder.stop();
      }
    }, 15_000);

    recorder.onstop = async () => {
      window.clearTimeout(timeout);
      try {
        if (chunks.length === 0) {
          finish(null);
          return;
        }
        const blob = new Blob(chunks, {
          type: recorder.mimeType || "audio/webm",
        });
        if (blob.size < 800) {
          finish(null);
          return;
        }
        const res = await agentSpeechApi.stt(blob);
        finish(res.text?.trim() || null);
      } catch {
        finish(null);
      }
    };

    recorder.onerror = () => finish(null);

    recorder.start();
    recognition.start();
  });
}
