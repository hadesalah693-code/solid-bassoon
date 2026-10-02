/**
 * Browser Text-to-Speech helper using the Web Speech API.
 * Picks the clearest available Arabic voice on the device.
 */

const STORAGE_VOICE_KEY = "kalima-tts-voice";
const DEFAULT_RATE = 0.88;

export type ArabicVoiceOption = {
  name: string;
  lang: string;
  local: boolean;
};

export type SpeakOptions = {
  /** Exact SpeechSynthesisVoice.name; falls back to best Arabic voice. */
  voiceName?: string | null;
  rate?: number;
  onEnd?: () => void;
};

function synth(): SpeechSynthesis | null {
  if (typeof window === "undefined") return null;
  return "speechSynthesis" in window ? window.speechSynthesis : null;
}

function getVoices(): SpeechSynthesisVoice[] {
  return synth()?.getVoices() ?? [];
}

/** Warm up voice list (Chrome loads voices asynchronously). */
export function preloadVoices(): Promise<void> {
  const engine = synth();
  if (!engine) return Promise.resolve();
  if (getVoices().length > 0) return Promise.resolve();

  return new Promise((resolve) => {
    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      engine.removeEventListener("voiceschanged", finish);
      resolve();
    };
    engine.addEventListener("voiceschanged", finish);
    engine.getVoices();
    window.setTimeout(finish, 600);
  });
}

export function getStoredVoiceName(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(STORAGE_VOICE_KEY);
  } catch {
    return null;
  }
}

export function setStoredVoiceName(name: string | null): void {
  if (typeof window === "undefined") return;
  try {
    if (name) localStorage.setItem(STORAGE_VOICE_KEY, name);
    else localStorage.removeItem(STORAGE_VOICE_KEY);
  } catch {
    /* ignore quota / private mode */
  }
}

export function listArabicVoices(): ArabicVoiceOption[] {
  return getVoices()
    .filter((v) => v.lang.toLowerCase().startsWith("ar"))
    .map((v) => ({
      name: v.name,
      lang: v.lang,
      local: v.localService,
    }))
    .sort((a, b) => {
      const score = (v: ArabicVoiceOption) =>
        (v.lang === "ar-EG" ? 4 : v.lang === "ar-SA" ? 3 : v.lang.startsWith("ar") ? 2 : 0) +
        (v.local ? 1 : 0);
      return score(b) - score(a);
    });
}

function pickArabicVoice(preferredName?: string | null): SpeechSynthesisVoice | undefined {
  const voices = getVoices();
  if (preferredName) {
    const exact = voices.find((v) => v.name === preferredName);
    if (exact) return exact;
  }

  const arabic = voices.filter((v) => v.lang.toLowerCase().startsWith("ar"));
  const preferLocal = (list: SpeechSynthesisVoice[]) =>
    list.find((v) => v.localService) ?? list[0];

  const byLang = (lang: string) => preferLocal(arabic.filter((v) => v.lang === lang));
  const byPrefix = (prefix: string) =>
    preferLocal(arabic.filter((v) => v.lang.toLowerCase().startsWith(prefix)));

  return (
    byLang("ar-EG") ??
    byLang("ar-SA") ??
    byPrefix("ar-eg") ??
    byPrefix("ar-sa") ??
    arabic.find((v) => /hoda|naayf|salma|tarik|amir/i.test(v.name)) ??
    preferLocal(arabic)
  );
}

export function speak(text: string, options?: SpeakOptions): void {
  const engine = synth();
  if (!engine) return;
  const trimmed = text?.trim();
  if (!trimmed) return;

  const utterance = new SpeechSynthesisUtterance(trimmed);
  const voice = pickArabicVoice(options?.voiceName ?? getStoredVoiceName());
  if (voice) {
    utterance.voice = voice;
    utterance.lang = voice.lang;
  } else {
    utterance.lang = "ar-EG";
  }
  utterance.rate = options?.rate ?? DEFAULT_RATE;
  utterance.pitch = 1;
  utterance.volume = 1;

  utterance.onend = () => options?.onEnd?.();
  utterance.onerror = () => options?.onEnd?.();

  engine.cancel();
  engine.speak(utterance);
}

/** Stop any ongoing speech immediately. */
export function stopSpeaking(): void {
  synth()?.cancel();
}

/** Returns true if the current browser supports the Web Speech API. */
export function isSpeechSupported(): boolean {
  return synth() !== null;
}

export function speakAsync(text: string, options?: SpeakOptions): Promise<void> {
  if (!synth()) {
    options?.onEnd?.();
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    speak(text, {
      ...options,
      onEnd: () => {
        options?.onEnd?.();
        resolve();
      },
    });
  });
}
