"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";

import { agentApi } from "@/lib/api/agent-voice";
import { lawyerAssistantApi } from "@/lib/api/lawyer-assistant";
import { casesApi } from "@/lib/api/cases";
import { delegationsApi } from "@/lib/api/delegations";
import { disputesApi } from "@/lib/api/disputes";
import { ApiRequestError } from "@/lib/api/client";
import type { SpeechConfig } from "@/lib/api/agent-speech";
import {
  listenOnce,
  loadSpeechConfig,
  speakAssistant,
  stopCloudSpeech,
  isNeuralTtsAvailable,
} from "@/lib/cloud-speech";
import { shouldRouteToAgent } from "@/lib/assistant/route-request";
import type { PageAssistantContext } from "@/lib/assistant/types";
import {
  generateMessageId,
  readAutoSpeakPreference,
  revealMessageText,
  toServerHistory,
  writeAutoSpeakPreference,
} from "@/lib/assistant/utils";
import { NO_CASE, type AssistantChatMessage } from "@/lib/assistant/types";
import {
  getSpeechRecognitionConstructor,
  type SpeechRecognitionLike,
} from "@/lib/assistant/speech-types";
import {
  getStoredVoiceName,
  isSpeechSupported,
  listArabicVoices,
  preloadVoices,
  setStoredVoiceName,
  type ArabicVoiceOption,
} from "@/lib/tts";
import type { CaseResponseDto } from "@/types/api";

export type VoiceOption = {
  id: string;
  label: string;
  lang: string;
};

export type UseLawyerAssistantOptions = {
  pageContext?: PageAssistantContext;
  /** When true, sync selected case from page URL context */
  syncPageCase?: boolean;
};

export function useLawyerAssistant(options: UseLawyerAssistantOptions = {}) {
  const { pageContext, syncPageCase = false } = options;

  const [messages, setMessages] = useState<AssistantChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [thinking, setThinking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [speakingId, setSpeakingId] = useState<string | null>(null);
  const [listening, setListening] = useState(false);
  const [voiceError, setVoiceError] = useState<string | null>(null);
  const [autoSpeak, setAutoSpeak] = useState(true);
  const [voiceConversation, setVoiceConversation] = useState(false);
  const [preferredVoice, setPreferredVoice] = useState<string | null>(null);
  const [arabicVoices, setArabicVoices] = useState<ArabicVoiceOption[]>([]);
  const [speechConfig, setSpeechConfig] = useState<SpeechConfig | null>(null);

  const [cases, setCases] = useState<CaseResponseDto[]>([]);
  const [casesLoading, setCasesLoading] = useState(false);
  const [casesError, setCasesError] = useState<string | null>(null);
  const [selectedCaseId, setSelectedCaseId] = useState<string>(NO_CASE);
  const [pendingActionId, setPendingActionId] = useState<string | null>(null);
  const [pendingSummary, setPendingSummary] = useState<string | null>(null);

  const listRef = useRef<HTMLDivElement | null>(null);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const voiceConversationRef = useRef(false);
  const thinkingRef = useRef(false);
  const pendingActionIdRef = useRef<string | null>(null);
  const listenCycleInFlightRef = useRef(false);
  const resumeTimeoutRef = useRef<number | null>(null);
  const startListeningRef = useRef<(() => void) | null>(null);
  const messagesRef = useRef(messages);
  const speakingIdRef = useRef<string | null>(null);
  const lastVoiceSendRef = useRef<{ text: string; at: number }>({
    text: "",
    at: 0,
  });

  messagesRef.current = messages;
  speakingIdRef.current = speakingId;
  voiceConversationRef.current = voiceConversation;
  thinkingRef.current = thinking;
  pendingActionIdRef.current = pendingActionId;

  const supportsTts = useMemo(() => isSpeechSupported(), []);

  const voiceOptions = useMemo((): VoiceOption[] => {
    if (
      speechConfig?.ttsProvider === "AZURE" &&
      (speechConfig.availableVoices?.length ?? 0) > 0
    ) {
      return speechConfig.availableVoices ?? [];
    }
    return arabicVoices.map((v) => ({
      id: v.name,
      label: v.name,
      lang: v.lang,
    }));
  }, [speechConfig, arabicVoices]);
  const canSpeak = useMemo(
    () =>
      supportsTts ||
      speechConfig?.ttsProvider === "AZURE" ||
      isNeuralTtsAvailable(),
    [supportsTts, speechConfig],
  );
  const shouldAttemptSpeak = canSpeak || speechConfig === null;
  const supportsSpeechInput = useMemo(
    () => getSpeechRecognitionConstructor() !== null,
    [],
  );

  const selectedCase = useMemo(
    () =>
      selectedCaseId === NO_CASE
        ? null
        : cases.find((c) => c.id === selectedCaseId) ?? null,
    [selectedCaseId, cases],
  );

  const effectiveCaseId =
    selectedCaseId !== NO_CASE
      ? selectedCaseId
      : syncPageCase && pageContext?.caseId
        ? pageContext.caseId
        : undefined;

  useEffect(() => {
    if (!syncPageCase || !pageContext?.caseId) return;
    setSelectedCaseId((prev) =>
      prev === NO_CASE ? pageContext.caseId! : prev,
    );
  }, [syncPageCase, pageContext?.caseId]);

  useEffect(() => {
    if (!syncPageCase || !pageContext?.delegationId) return;
    let cancelled = false;
    void delegationsApi
      .listCases(pageContext.delegationId)
      .then((links) => {
        if (cancelled || links.length === 0) return;
        setSelectedCaseId((prev) =>
          prev === NO_CASE ? links[0]!.caseId : prev,
        );
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [syncPageCase, pageContext?.delegationId]);

  useEffect(() => {
    if (!syncPageCase || !pageContext?.disputeId) return;
    let cancelled = false;
    void disputesApi
      .get(pageContext.disputeId)
      .then((dispute) => {
        if (cancelled || !dispute.convertedCaseId) return;
        setSelectedCaseId((prev) =>
          prev === NO_CASE ? dispute.convertedCaseId! : prev,
        );
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [syncPageCase, pageContext?.disputeId]);

  useEffect(() => {
    listRef.current?.scrollTo({
      top: listRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [messages.length, thinking]);

  useEffect(() => {
    setAutoSpeak(readAutoSpeakPreference());
    setPreferredVoice(getStoredVoiceName());
    void preloadVoices().then(() => setArabicVoices(listArabicVoices()));
    void loadSpeechConfig()
      .then((cfg) => {
        setSpeechConfig(cfg);
        const stored = getStoredVoiceName();
        const azureIds =
          cfg.ttsProvider === "AZURE"
            ? new Set((cfg.availableVoices ?? []).map((v) => v.id))
            : null;
        if (stored && (!azureIds || azureIds.has(stored))) {
          setPreferredVoice(stored);
        } else if (cfg.voice && cfg.ttsProvider === "AZURE") {
          setPreferredVoice(cfg.voice);
        }
      })
      .catch(() => setSpeechConfig(null));
    return () => {
      stopCloudSpeech();
      recognitionRef.current?.abort();
      if (resumeTimeoutRef.current != null) {
        window.clearTimeout(resumeTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setCasesLoading(true);
      setCasesError(null);
      try {
        const res = await casesApi.list({
          page: 0,
          size: 100,
          includeArchived: false,
          sort: "updatedAt,desc",
        });
        if (!cancelled) setCases(res.content);
      } catch (e) {
        if (!cancelled) {
          setCasesError(
            e instanceof ApiRequestError
              ? e.details?.message ?? e.message
              : "تعذر تحميل قائمة القضايا.",
          );
        }
      } finally {
        if (!cancelled) setCasesLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const cancelResumeListening = useCallback(() => {
    if (resumeTimeoutRef.current != null) {
      window.clearTimeout(resumeTimeoutRef.current);
      resumeTimeoutRef.current = null;
    }
  }, []);

  const scheduleResumeListening = useCallback(() => {
    if (!voiceConversationRef.current) return;
    if (thinkingRef.current || pendingActionIdRef.current) return;
    if (listenCycleInFlightRef.current) return;
    cancelResumeListening();
    resumeTimeoutRef.current = window.setTimeout(() => {
      resumeTimeoutRef.current = null;
      if (
        voiceConversationRef.current &&
        !thinkingRef.current &&
        !pendingActionIdRef.current &&
        !listenCycleInFlightRef.current &&
        !speakingIdRef.current
      ) {
        startListeningRef.current?.();
      }
    }, 1500);
  }, [cancelResumeListening]);

  const finalizeAssistantSpeech = useCallback(
    (
      id: string,
      content: string,
      options?: { resumeConversation?: boolean },
    ) => {
      const resume =
        options?.resumeConversation ?? voiceConversationRef.current;
      if (autoSpeak || resume) {
        stopCloudSpeech();
        setSpeakingId(id);
        void speakAssistant(content, {
          voiceName: preferredVoice,
          onEnd: () => {
            setSpeakingId((c) => (c === id ? null : c));
            if (resume) scheduleResumeListening();
          },
        }).catch(() => {
          setSpeakingId((c) => (c === id ? null : c));
          if (resume) scheduleResumeListening();
        });
      } else if (resume) {
        scheduleResumeListening();
      }
    },
    [autoSpeak, preferredVoice, scheduleResumeListening],
  );

  const appendStreamingAssistantMessage = useCallback(
    async (
      partial: Omit<
        AssistantChatMessage,
        "id" | "createdAt" | "streaming" | "content"
      >,
      streamBody: Parameters<typeof lawyerAssistantApi.askStream>[0],
      options?: { resumeConversation?: boolean },
    ) => {
      const id = generateMessageId();
      setMessages((prev) => [
        ...prev,
        {
          ...partial,
          id,
          createdAt: Date.now(),
          streaming: true,
          content: "",
        },
      ]);

      const res = await lawyerAssistantApi.askStream(streamBody, {
        onDelta: (text) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === id ? { ...m, content: m.content + text } : m,
            ),
          );
        },
      });

      setMessages((prev) =>
        prev.map((m) =>
          m.id === id
            ? {
                ...m,
                streaming: false,
                content: m.content || res.reply,
                contextUsed: res.contextUsed,
                documentsUsed: res.documentsUsed,
                provider: res.provider,
                model: res.model,
              }
            : m,
        ),
      );

      finalizeAssistantSpeech(id, res.reply || "", options);
      return res;
    },
    [finalizeAssistantSpeech],
  );

  const appendAssistantMessage = useCallback(
    async (
      partial: Omit<AssistantChatMessage, "id" | "createdAt" | "streaming">,
      options?: { stream?: boolean; resumeConversation?: boolean },
    ) => {
      const id = generateMessageId();
      const msg: AssistantChatMessage = {
        ...partial,
        id,
        createdAt: Date.now(),
        streaming: Boolean(options?.stream),
        content: options?.stream ? "" : partial.content,
      };
      setMessages((prev) => [...prev, msg]);

      if (options?.stream && partial.content.length > 0) {
        await revealMessageText(partial.content, (partialText) => {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === id ? { ...m, content: partialText } : m,
            ),
          );
        });
        setMessages((prev) =>
          prev.map((m) =>
            m.id === id ? { ...m, streaming: false, content: partial.content } : m,
          ),
        );
      }

      const finalMsg: AssistantChatMessage = {
        ...msg,
        content: partial.content,
        streaming: false,
      };

      const resume =
        options?.resumeConversation ?? voiceConversationRef.current;
      finalizeAssistantSpeech(id, partial.content, { resumeConversation: resume });

      return finalMsg;
    },
    [finalizeAssistantSpeech],
  );

  const speakAssistantMessage = useCallback(
    (msg: AssistantChatMessage, options?: { resumeConversation?: boolean }) => {
      if (!shouldAttemptSpeak) {
        if (options?.resumeConversation) scheduleResumeListening();
        return;
      }
      stopCloudSpeech();
      setSpeakingId(msg.id);
      void speakAssistant(msg.content, {
        voiceName: preferredVoice,
        onEnd: () => {
          setSpeakingId((current) => (current === msg.id ? null : current));
          if (options?.resumeConversation ?? voiceConversationRef.current) {
            scheduleResumeListening();
          }
        },
      }).catch(() => {
        setSpeakingId((current) => (current === msg.id ? null : current));
        if (options?.resumeConversation ?? voiceConversationRef.current) {
          scheduleResumeListening();
        }
      });
    },
    [shouldAttemptSpeak, preferredVoice, scheduleResumeListening],
  );

  const handleSpeak = useCallback(
    (msg: AssistantChatMessage) => {
      if (!shouldAttemptSpeak) return;
      if (speakingId === msg.id) {
        stopCloudSpeech();
        setSpeakingId(null);
        return;
      }
      speakAssistantMessage(msg);
    },
    [speakingId, shouldAttemptSpeak, speakAssistantMessage],
  );

  const sendText = useCallback(
    async (
      rawText: string,
      opts?: { confirmPendingId?: string; displayAsUser?: string },
    ) => {
      const text = rawText.trim();
      const isConfirm = Boolean(opts?.confirmPendingId);
      if ((!text && !isConfirm) || thinking) return;

      if (
        !isConfirm &&
        voiceConversationRef.current &&
        text === lastVoiceSendRef.current.text &&
        Date.now() - lastVoiceSendRef.current.at < 3_000
      ) {
        return;
      }
      if (!isConfirm && voiceConversationRef.current) {
        lastVoiceSendRef.current = { text, at: Date.now() };
      }

      const priorHistory = toServerHistory(messagesRef.current);

      const userDisplay =
        opts?.displayAsUser ??
        (isConfirm ? `✓ تأكيد: ${pendingSummary ?? "الإجراء"}` : text);

      setMessages((prev) => [
        ...prev,
        {
          id: generateMessageId(),
          role: "user",
          content: userDisplay,
          createdAt: Date.now(),
        },
      ]);
      setInput("");
      thinkingRef.current = true;
      setThinking(true);
      setError(null);

      const useAgent = shouldRouteToAgent(text, { isConfirm });
      const caseForAgent =
        selectedCase ??
        (effectiveCaseId
          ? cases.find((c) => c.id === effectiveCaseId) ?? null
          : null);

      try {
        if (useAgent) {
          const agentText =
            !isConfirm && caseForAgent
              ? `بخصوص القضية ${caseForAgent.caseNumber}: ${text}`
              : text || "تأكيد";
          const res = await agentApi.voice({
            text: agentText,
            pendingActionId: opts?.confirmPendingId,
            confirm: isConfirm ? true : undefined,
            caseId: effectiveCaseId,
            delegationId: pageContext?.delegationId,
            disputeId: pageContext?.disputeId,
          });

          if (res.needsConfirmation && res.pendingActionId) {
            setPendingActionId(res.pendingActionId);
            setPendingSummary(res.pendingSummary ?? res.reply);
          } else {
            setPendingActionId(null);
            setPendingSummary(null);
          }
          if (res.activeCaseId) {
            setSelectedCaseId(res.activeCaseId);
          }

          const pauseForConfirm = Boolean(
            res.needsConfirmation && res.pendingActionId,
          );
          const resume = voiceConversationRef.current && !pauseForConfirm;
          await appendAssistantMessage(
            {
              role: "assistant",
              content: res.reply,
              isAgent: true,
              toolsUsed: res.toolsUsed,
              provider: "AGENT",
            },
            { stream: false, resumeConversation: resume },
          );
          if (pauseForConfirm && voiceConversationRef.current) {
            /* wait for confirm */
          }
        } else {
          const streamBody = {
            caseId: effectiveCaseId,
            delegationId: pageContext?.delegationId,
            disputeId: pageContext?.disputeId,
            history: priorHistory,
            question: text,
          };
          const resume = voiceConversationRef.current;
          await appendStreamingAssistantMessage(
            { role: "assistant" },
            streamBody,
            { resumeConversation: resume },
          );
        }
      } catch (e) {
        const msg =
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : useAgent
              ? "تعذر تنفيذ الأمر. حاول مرة أخرى."
              : "تعذر الحصول على رد من المساعد. حاول مرة أخرى.";
        setError(msg);
        if (voiceConversationRef.current) scheduleResumeListening();
      } finally {
        thinkingRef.current = false;
        setThinking(false);
      }
    },
    [
      thinking,
      pendingSummary,
      selectedCase,
      effectiveCaseId,
      cases,
      appendAssistantMessage,
      appendStreamingAssistantMessage,
      scheduleResumeListening,
      pageContext,
    ],
  );

  const sendTextRef = useRef(sendText);
  sendTextRef.current = sendText;

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const text = input.trim();
      if (!text || thinking) return;
      void sendText(text);
    },
    [input, thinking, sendText],
  );

  const handleConfirmPending = useCallback(() => {
    if (!pendingActionId || thinking) return;
    void sendText("تأكيد", {
      confirmPendingId: pendingActionId,
      displayAsUser: `✓ تأكيد: ${pendingSummary ?? "الإجراء"}`,
    });
  }, [pendingActionId, pendingSummary, thinking, sendText]);

  const handleCancelPending = useCallback(() => {
    setPendingActionId(null);
    setPendingSummary(null);
    if (voiceConversationRef.current) scheduleResumeListening();
  }, [scheduleResumeListening]);

  const toggleAutoSpeak = useCallback(() => {
    setAutoSpeak((prev) => {
      const next = !prev;
      writeAutoSpeakPreference(next);
      return next;
    });
  }, []);

  const handleVoiceChange = useCallback((name: string) => {
    const value = name === "" ? null : name;
    setPreferredVoice(value);
    setStoredVoiceName(value);
  }, []);

  const stopListening = useCallback(() => {
    recognitionRef.current?.stop();
    setListening(false);
  }, []);

  const runListenCycle = useCallback(async () => {
    if (!voiceConversationRef.current || thinkingRef.current) return;
    if (pendingActionIdRef.current) return;
    if (listenCycleInFlightRef.current) return;
    if (speakingIdRef.current) return;

    listenCycleInFlightRef.current = true;
    cancelResumeListening();
    stopCloudSpeech();
    setListening(true);
    setVoiceError(null);
    try {
      const transcript = await listenOnce(getSpeechRecognitionConstructor);
      if (!voiceConversationRef.current) return;
      if (transcript) {
        await sendTextRef.current(transcript);
      } else {
        scheduleResumeListening();
      }
    } catch {
      setVoiceError("تعذر التقاط الصوت. حاول مرة أخرى.");
      scheduleResumeListening();
    } finally {
      listenCycleInFlightRef.current = false;
      setListening(false);
    }
  }, [cancelResumeListening, scheduleResumeListening]);

  const startListeningManual = useCallback(() => {
    if (thinkingRef.current || speakingIdRef.current) return;
    const Recognition = getSpeechRecognitionConstructor();
    if (!Recognition) {
      setVoiceError("المتصفح لا يدعم الإدخال الصوتي. جرّب Chrome أو Edge.");
      return;
    }
    setVoiceError(null);
    const recognition = new Recognition();
    recognition.lang = "ar-EG";
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.maxAlternatives = 1;

    recognition.onresult = (event) => {
      const parts: string[] = [];
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const result = event.results[i] ?? event.results.item(i);
        const first = result[0] ?? result.item(0);
        if (first?.transcript) parts.push(first.transcript);
      }
      const transcript = parts.join(" ").trim();
      if (!transcript) return;
      if (voiceConversationRef.current) {
        void sendTextRef.current(transcript);
      } else {
        setInput(transcript);
      }
    };

    recognition.onerror = (event) => {
      const message =
        event.error === "not-allowed"
          ? "تم رفض إذن الميكروفون. فعّل الميكروفون من إعدادات المتصفح."
          : "تعذر التقاط الصوت. حاول مرة أخرى.";
      setVoiceError(message);
      setListening(false);
      if (event.error === "not-allowed") {
        voiceConversationRef.current = false;
        setVoiceConversation(false);
      } else if (voiceConversationRef.current) {
        scheduleResumeListening();
      }
    };

    recognition.onend = () => setListening(false);

    recognitionRef.current = recognition;
    setListening(true);
    recognition.start();
  }, [scheduleResumeListening]);

  startListeningRef.current = () => {
    if (voiceConversationRef.current) {
      void runListenCycle();
    } else {
      startListeningManual();
    }
  };

  const toggleListening = useCallback(() => {
    if (listening) {
      stopListening();
      return;
    }
    if (voiceConversationRef.current) {
      void runListenCycle();
      return;
    }
    startListeningManual();
  }, [listening, runListenCycle, startListeningManual, stopListening]);

  const toggleVoiceConversation = useCallback(() => {
    if (voiceConversationRef.current) {
      voiceConversationRef.current = false;
      setVoiceConversation(false);
      cancelResumeListening();
      stopListening();
      stopCloudSpeech();
      setSpeakingId(null);
      return;
    }
    if (!supportsSpeechInput) {
      setVoiceError("المتصفح لا يدعم المحادثة الصوتية. جرّب Chrome أو Edge.");
      return;
    }
    setAutoSpeak(true);
    writeAutoSpeakPreference(true);
    setVoiceError(null);
    voiceConversationRef.current = true;
    setVoiceConversation(true);
    void runListenCycle();
  }, [supportsSpeechInput, runListenCycle, stopListening, cancelResumeListening]);

  const handleClear = useCallback(() => {
    voiceConversationRef.current = false;
    setVoiceConversation(false);
    cancelResumeListening();
    stopCloudSpeech();
    recognitionRef.current?.abort();
    setMessages([]);
    setError(null);
    setSpeakingId(null);
    setListening(false);
    setVoiceError(null);
    setPendingActionId(null);
    setPendingSummary(null);
  }, [cancelResumeListening]);

  const statusHint = useMemo(() => {
    if (voiceConversation) {
      if (listening) return "محادثة صوتية — تكلّم الآن...";
      if (speakingId) return "المساعد يرد عليك صوتياً...";
      return "محادثة صوتية مفعّلة — انتظر أو تكلّم.";
    }
    if (canSpeak) {
      if (supportsSpeechInput) {
        return autoSpeak
          ? "رد صوتي تلقائي — أو «محادثة صوتية» للتحدث باستمرار."
          : "يدعم الإدخال والردود الصوتية.";
      }
      return autoSpeak ? "الردود تُقرأ صوتياً تلقائياً." : "قراءة صوتية متاحة.";
    }
    return speechConfig === null ? "جاري تهيئة الصوت..." : "الصوت غير متاح.";
  }, [
    voiceConversation,
    listening,
    speakingId,
    canSpeak,
    supportsSpeechInput,
    autoSpeak,
    speechConfig,
  ]);

  return {
    messages,
    input,
    setInput,
    thinking,
    error,
    speakingId,
    listening,
    voiceError,
    autoSpeak,
    voiceConversation,
    preferredVoice,
    voiceOptions,
    speechConfig,
    cases,
    casesLoading,
    casesError,
    selectedCaseId,
    setSelectedCaseId,
    selectedCase,
    effectiveCaseId,
    pendingActionId,
    pendingSummary,
    pageContext,
    listRef,
    shouldAttemptSpeak,
    supportsSpeechInput,
    statusHint,
    handleSubmit,
    handleSpeak,
    handleConfirmPending,
    handleCancelPending,
    toggleAutoSpeak,
    handleVoiceChange,
    toggleListening,
    toggleVoiceConversation,
    handleClear,
    sendText,
  };
}

export type LawyerAssistantState = ReturnType<typeof useLawyerAssistant>;
