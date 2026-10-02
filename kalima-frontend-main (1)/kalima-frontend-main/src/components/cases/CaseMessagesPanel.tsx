"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  Check,
  CheckCheck,
  CornerUpLeft,
  Loader2,
  MessageCircle,
  MessageSquare,
  Mic,
  Paperclip,
  Send,
  Sparkles,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { chatbotApi } from "@/lib/api/chatbot";
import { ApiRequestError } from "@/lib/api/client";
import { resolveBackendUrl } from "@/lib/api/resolve-backend-url";
import { prependUniqueMessages } from "@/lib/messages/message-list-utils";
import { messagesApi } from "@/lib/api/messages";
import { getStoredUser } from "@/lib/auth/session";
import { useAuthenticatedMediaUrl } from "@/lib/hooks/use-authenticated-media-url";
import { notifyApiError, notifyError } from "@/lib/feedback";
import {
  openCaseMessageStream,
  type StreamStatus,
} from "@/lib/realtime/case-message-stream";
import { cn } from "@/lib/utils/cn";
import { formatDateTime, formatTime } from "@/lib/utils/format-date";
import type { MessageResponseDto } from "@/types/api";

type Props = {
  caseId: string;
  disabled?: boolean;
  embedded?: boolean;
  /** When true the thread fills all available vertical space (no max-height cap). */
  fullHeight?: boolean;
  onMessagesChanged?: () => void;
};

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "مدير",
  SENIOR_LAWYER: "محامي أول",
  LAWYER: "محامي",
  SECRETARY: "سكرتير",
  ACCOUNTANT: "محاسب",
  CLIENT: "عميل",
};

const BOT_WELCOME_MESSAGE =
  "مرحبًا 👋 أنا خدمة المحادثة الذكية في كلمة. أقدر أساعدك في الأسئلة العامة عن قضيتك، مواعيد الجلسات، المستندات، وحالة الطلبات. لو سؤالك يحتاج رأي قانوني أو قرار من محامي، سأحوّلك للمحامي المختص.";

const QUICK_QUESTIONS = [
  "حالة القضية",
  "موعد الجلسة القادمة",
  "المستندات المطلوبة",
  "آخر تحديث",
  "التحدث مع المحامي",
];

type AssistantClassification = "safe" | "handoff" | "explicit-lawyer";

const HANDOFF_STORAGE_PREFIX = "kalima.chat.handoff.";

function classifyAssistantQuestion(question: string): AssistantClassification {
  const q = question.trim();
  if (
    q.includes("محامي") ||
    q.includes("تواصل") ||
    q.includes("كلم") ||
    q.includes("اكلم") ||
    q.includes("أكلم") ||
    q.includes("اتكلم")
  ) {
    return "explicit-lawyer";
  }
  if (
    q.includes("حالة") ||
    q.includes("القضية") ||
    q.includes("جلسة") ||
    q.includes("موعد") ||
    q.includes("مستند") ||
    q.includes("اوراق") ||
    q.includes("أوراق") ||
    q.includes("تحديث") ||
    q.includes("آخر")
  ) {
    return "safe";
  }
  return "handoff";
}

function initials(name: string | null, fallback: string) {
  const source = (name && name.trim()) || fallback;
  return source
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();
}

function isSameDay(a: string, b: string) {
  return a.slice(0, 10) === b.slice(0, 10);
}

export function CaseMessagesPanel({
  caseId,
  disabled,
  embedded,
  fullHeight,
  onMessagesChanged,
}: Props) {
  const scrollAreaRef = useRef<HTMLDivElement | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const messageIdsRef = useRef<Set<string>>(new Set());
  const hasLoadedMessagesRef = useRef(false);
  const [items, setItems] = useState<MessageResponseDto[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [assistantBusy, setAssistantBusy] = useState(false);
  const [handoffActive, setHandoffActive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [me, setMe] = useState<ReturnType<typeof getStoredUser>>(() =>
    getStoredUser(),
  );
  const [streamStatus, setStreamStatus] = useState<StreamStatus>("connecting");
  const [typingPersonName, setTypingPersonName] = useState<string | null>(null);
  const typingClearRef  = useRef<ReturnType<typeof setTimeout> | null>(null);
  const typingSendRef   = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [attachmentFile, setAttachmentFile] = useState<File | null>(null);
  const [attachmentUrl,  setAttachmentUrl]  = useState<string | null>(null);
  const [uploading,      setUploading]      = useState(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [replyTo, setReplyTo] = useState<MessageResponseDto | null>(null);

  // Voice recording
  const [isRecording, setIsRecording]   = useState(false);
  const [recordSeconds, setRecordSeconds] = useState(0);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef   = useRef<Blob[]>([]);
  const recordTimerRef   = useRef<ReturnType<typeof setInterval> | null>(null);
  const shouldScrollToBottomRef = useRef(true);
  const isClientUser = me?.systemRole === "CLIENT";

  useEffect(() => {
    setMe(getStoredUser());
  }, []);

  useEffect(() => {
    if (typeof window === "undefined") return;
    messageIdsRef.current = new Set();
    hasLoadedMessagesRef.current = false;
    shouldScrollToBottomRef.current = true;
    setHasMore(false);
    setNextCursor(null);
    setLoadingOlder(false);
    setHandoffActive(
      window.localStorage.getItem(`${HANDOFF_STORAGE_PREFIX}${caseId}`) === "1",
    );
  }, [caseId]);

  const refresh = useCallback(() => setReloadKey((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const page = await messagesApi.list(caseId);
        if (cancelled) return;
        const messages = page.items;
        const nextIds = new Set(messages.map((m) => m.id));
        const hasNewMessages = messages.some(
          (m) => !messageIdsRef.current.has(m.id),
        );
        if (hasLoadedMessagesRef.current && hasNewMessages) {
          onMessagesChanged?.();
        }
        messageIdsRef.current = nextIds;
        hasLoadedMessagesRef.current = true;
        setHasMore(page.hasMore);
        setNextCursor(page.nextCursor);
        setItems(messages);
        shouldScrollToBottomRef.current = true;
      } catch (e) {
        if (cancelled) return;
        setError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل المحادثة",
        );
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [caseId, onMessagesChanged, reloadKey]);

  useEffect(() => {
    if (loading || loadingOlder) return;
    if (!shouldScrollToBottomRef.current) return;
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
    shouldScrollToBottomRef.current = false;
  }, [items.length, loading, loadingOlder]);

  const loadOlderMessages = useCallback(async () => {
    if (!nextCursor || loadingOlder || loading) return;
    const scrollEl = scrollAreaRef.current;
    const prevScrollHeight = scrollEl?.scrollHeight ?? 0;
    const prevScrollTop = scrollEl?.scrollTop ?? 0;
    setLoadingOlder(true);
    try {
      const page = await messagesApi.list(caseId, { before: nextCursor });
      setHasMore(page.hasMore);
      setNextCursor(page.nextCursor);
      for (const message of page.items) {
        messageIdsRef.current.add(message.id);
      }
      setItems((prev) => prependUniqueMessages(prev, page.items));
      requestAnimationFrame(() => {
        if (!scrollEl) return;
        scrollEl.scrollTop = scrollEl.scrollHeight - prevScrollHeight + prevScrollTop;
      });
    } catch (e) {
      notifyApiError(e, "تعذر تحميل الرسائل الأقدم");
    } finally {
      setLoadingOlder(false);
    }
  }, [caseId, loading, loadingOlder, nextCursor]);

  const appendMessage = useCallback((message: MessageResponseDto) => {
    if (messageIdsRef.current.has(message.id)) {
      return;
    }
    messageIdsRef.current.add(message.id);
    shouldScrollToBottomRef.current = true;
    setItems((prev) => {
      return [...prev, message];
    });
    onMessagesChanged?.();
  }, [onMessagesChanged]);

  const activateHandoff = useCallback(() => {
    setHandoffActive(true);
    if (typeof window !== "undefined") {
      window.localStorage.setItem(`${HANDOFF_STORAGE_PREFIX}${caseId}`, "1");
    }
  }, [caseId]);

  const clearHandoff = useCallback(() => {
    setHandoffActive(false);
    if (typeof window !== "undefined") {
      window.localStorage.removeItem(`${HANDOFF_STORAGE_PREFIX}${caseId}`);
      window.localStorage.removeItem(`handoff:${caseId}`);
    }
  }, [caseId]);

  // Real-time stream: subscribe to messages + typing topics for this case.
  useEffect(() => {
    const dispose = openCaseMessageStream(caseId, {
      onStatusChange: setStreamStatus,
      onMessage: (incoming) => {
        appendMessage(incoming);
      },
      onTyping: (evt) => {
        // Ignore own typing events.
        if (me?.id && evt.personId === me.id) return;
        if (typingClearRef.current) clearTimeout(typingClearRef.current);
        setTypingPersonName(evt.personName);
        typingClearRef.current = setTimeout(() => {
          setTypingPersonName(null);
          typingClearRef.current = null;
        }, 3500);
      },
    });
    return () => {
      dispose();
      if (typingClearRef.current) clearTimeout(typingClearRef.current);
      if (typingSendRef.current)  clearTimeout(typingSendRef.current);
    };
  }, [appendMessage, caseId, me?.id]);

  async function handleAttachmentPick(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setAttachmentFile(file);
    setUploading(true);
    try {
      const url = await messagesApi.uploadAttachment(caseId, file);
      setAttachmentUrl(url);
    } catch (err) {
      notifyApiError(err, "تعذر رفع الملف");
      setAttachmentFile(null);
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  function clearAttachment() {
    setAttachmentFile(null);
    setAttachmentUrl(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  async function startRecording() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      audioChunksRef.current = [];

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunksRef.current.push(e.data);
      };

      recorder.onstop = async () => {
        stream.getTracks().forEach((t) => t.stop());
        const blob = new Blob(audioChunksRef.current, { type: "audio/webm" });
        const file = new File([blob], `voice-${Date.now()}.webm`, { type: "audio/webm" });
        setAttachmentFile(file);
        setUploading(true);
        try {
          const url = await messagesApi.uploadAttachment(caseId, file);
          setAttachmentUrl(url);
        } catch (err) {
          notifyApiError(err, "تعذر رفع الرسالة الصوتية");
          setAttachmentFile(null);
        } finally {
          setUploading(false);
        }
        if (recordTimerRef.current) clearInterval(recordTimerRef.current);
        setRecordSeconds(0);
        setIsRecording(false);
      };

      recorder.start();
      mediaRecorderRef.current = recorder;
      setIsRecording(true);
      setRecordSeconds(0);
      recordTimerRef.current = setInterval(() => setRecordSeconds((s) => s + 1), 1000);
    } catch {
      notifyError("لا يمكن الوصول إلى الميكروفون");
    }
  }

  function stopRecording() {
    mediaRecorderRef.current?.stop();
    if (recordTimerRef.current) {
      clearInterval(recordTimerRef.current);
      recordTimerRef.current = null;
    }
  }

  function cancelRecording() {
    if (mediaRecorderRef.current?.state === "recording") {
      // Prevent onstop from uploading by clearing chunks first
      audioChunksRef.current = [];
      mediaRecorderRef.current.stop();
    }
    if (recordTimerRef.current) {
      clearInterval(recordTimerRef.current);
      recordTimerRef.current = null;
    }
    setIsRecording(false);
    setRecordSeconds(0);
  }

  function formatRecordTime(secs: number) {
    const m = Math.floor(secs / 60).toString().padStart(2, "0");
    const s = (secs % 60).toString().padStart(2, "0");
    return `${m}:${s}`;
  }

  async function send() {
    const text = content.trim();
    if (!text && !attachmentUrl) {
      notifyError("اكتب الرسالة أو أرفق ملفاً أولاً");
      return;
    }
    setSaving(true);
    try {
      const created = await messagesApi.sendEnhanced(caseId, {
        content: text || undefined,
        attachmentUrl: attachmentUrl ?? undefined,
        replyToMessageId: replyTo?.id,
      });
      appendMessage(created);
      setContent("");
      clearAttachment();
      setReplyTo(null);
      if (isClientUser) {
        await requestAssistantReply(text || "(مرفق)");
      } else {
        clearHandoff();
      }
    } catch (e) {
      notifyApiError(e, "تعذر إرسال الرسالة");
    } finally {
      setSaving(false);
    }
  }

  async function requestAssistantReply(
    question: string,
    options: { force?: boolean } = {},
  ) {
    const classification = classifyAssistantQuestion(question);
    if (!options.force && handoffActive) {
      return;
    }
    setAssistantBusy(true);
    try {
      await chatbotApi.ask(caseId, { question });
      if (classification === "handoff" || classification === "explicit-lawyer") {
        activateHandoff();
      }
      refresh();
    } catch (e) {
      notifyApiError(e, "تعذر حفظ رد المساعد");
    } finally {
      setAssistantBusy(false);
    }
  }

  async function sendQuickQuestion(question: string) {
    const text = question.trim();
    if (!text) return;
    setSaving(true);
    try {
      const created = await messagesApi.send(caseId, text);
      appendMessage(created);
      await requestAssistantReply(text, { force: true });
    } catch (e) {
      notifyApiError(e, "تعذر إرسال الرسالة");
    } finally {
      setSaving(false);
    }
  }

  // Build a time-ordered timeline. Regular USER messages are grouped by
  // consecutive sender on the same day; BOT messages always render as their
  // own standalone bubble, never grouped with anyone.
  const timeline = useMemo(() => {
    type MsgGroup = {
      kind: "group";
      senderId: string;
      mine: boolean;
      sender: MessageResponseDto["sender"];
      day: string;
      items: MessageResponseDto[];
    };
    type BotBubble = {
      kind: "bot";
      day: string;
      data: MessageResponseDto;
    };

    const sorted = [...items].sort((a, b) =>
      a.createdAt.localeCompare(b.createdAt),
    );

    const out: (MsgGroup | BotBubble)[] = [];
    for (const m of sorted) {
      // BOT messages have no sender (sender = null); always standalone.
      if (m.senderType === "BOT") {
        out.push({
          kind: "bot",
          day: m.createdAt.slice(0, 10),
          data: m,
        });
        continue;
      }
      const senderId = m.sender?.id ?? "";
      const mine = Boolean(me?.id && senderId === me.id);
      const last = out[out.length - 1];
      if (
        last &&
        last.kind === "group" &&
        last.senderId === senderId &&
        isSameDay(last.items[last.items.length - 1].createdAt, m.createdAt)
      ) {
        last.items.push(m);
        continue;
      }
      out.push({
        kind: "group",
        senderId,
        mine,
        sender: m.sender,
        day: m.createdAt.slice(0, 10),
        items: [m],
      });
    }
    return out;
  }, [items, me?.id]);

  const messageArea = (
    <>
      {hasMore ? (
        <div className="flex justify-center pb-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={loadingOlder}
            onClick={() => void loadOlderMessages()}
            className="text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
          >
            {loadingOlder ? (
              <>
                <Loader2 className="size-3.5 animate-spin" />
                جاري التحميل...
              </>
            ) : (
              "تحميل رسائل أقدم"
            )}
          </Button>
        </div>
      ) : null}
      {/* Day separator + groups + bot bubbles */}
      {(() => {
        let lastDay: string | null = null;
        return timeline.map((entry, gi) => {
          const showDay = entry.day !== lastDay;
          lastDay = entry.day;
          const dayIso =
            entry.kind === "group"
              ? entry.items[0].createdAt
              : entry.data.createdAt;
          return (
            <div key={gi} className="space-y-1.5">
              {showDay ? <DaySeparator dateIso={dayIso} /> : null}
              {entry.kind === "group" ? (
                <MessageGroup
                  group={entry}
                  meFullName={me?.fullName ?? null}
                  onReply={setReplyTo}
                  onReact={async (msg, emoji) => {
                    try {
                      const updated = await messagesApi.react(caseId, msg.id, emoji);
                      setItems((prev) =>
                        prev.map((it) =>
                          it.id === msg.id
                            ? { ...it, reactions: updated.reactions, myReaction: updated.myReaction }
                            : it,
                        ),
                      );
                    } catch {
                      // Ignore reaction errors silently.
                    }
                  }}
                />
              ) : (
                <BotBubble message={entry.data} />
              )}
            </div>
          );
        });
      })()}
      {/* Typing indicator */}
      {typingPersonName ? (
        <TypingIndicator personName={typingPersonName} />
      ) : null}

      <div ref={bottomRef} />
    </>
  );

  const body = (
    <div className="flex h-full min-h-0 flex-col gap-3">
      {/* Scrollable thread */}
      <div
        ref={scrollAreaRef}
        className={cn(
          "relative flex-1 overflow-y-auto rounded-2xl border border-[var(--color-border)]",
          "bg-gradient-to-b from-[var(--color-surface-muted)]/40 to-[var(--color-surface)]",
          fullHeight
            ? "min-h-0 flex-1"
            : embedded
              ? "min-h-[18rem] max-h-[24rem]"
              : "min-h-[20rem] max-h-[32rem]",
        )}
      >
        {error ? (
          <ChatStateMessage
            kind="error"
            title="تعذر تحميل المحادثة"
            description={error}
            actionLabel="إعادة المحاولة"
            onAction={() => {
              setError(null);
              refresh();
            }}
          />
        ) : loading ? (
          <ChatStateMessage
            kind="loading"
            title="جاري تحميل المحادثة..."
          />
        ) : items.length === 0 && isClientUser ? (
          <AssistantWelcome
            onQuickQuestion={sendQuickQuestion}
            disabled={saving || assistantBusy || disabled}
          />
        ) : items.length === 0 ? (
          <ChatStateMessage
            kind="empty"
            title="لا توجد رسائل بعد"
            description="ابدأ المحادثة بكتابة أول رسالة."
          />
        ) : (
          <div className="space-y-3 p-4 sm:p-5">{messageArea}</div>
        )}
      </div>

      {/* Composer */}
      <div
        className={cn(
          "rounded-2xl border bg-[var(--color-surface)] p-2 transition-colors",
          "border-[var(--color-border)] focus-within:border-[var(--color-gold-400)]",
          "focus-within:shadow-[0_0_0_3px_rgba(212,175,55,0.12)]",
          (saving || assistantBusy || disabled) && "opacity-70",
        )}
      >
        {isClientUser && !loading && !error && items.length > 0 ? (
          <AssistantReengagement
            onQuickQuestion={sendQuickQuestion}
            disabled={saving || assistantBusy || disabled}
          />
        ) : null}
        <textarea
          ref={(el) => {
            if (el) {
              el.style.height = "auto";
              el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
            }
          }}
          rows={2}
          value={content}
          disabled={saving || disabled}
          placeholder={
            isClientUser
              ? "اكتب رسالتك للمحامي أو المكتب..."
              : "اكتب ردك للعميل..."
          }
          onChange={(e) => {
            setContent(e.target.value);
            // Auto-resize
            const el = e.currentTarget;
            el.style.height = "auto";
            el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
            // Typing indicator — debounced, fire at most every 2 s
            if (!typingSendRef.current) {
              void messagesApi.sendTyping(caseId).catch(() => {});
              typingSendRef.current = setTimeout(() => {
                typingSendRef.current = null;
              }, 2000);
            }
          }}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !e.ctrlKey && !e.metaKey) {
              e.preventDefault();
              void send();
            }
            // Shift+Enter → insert newline (default textarea behaviour, nothing needed)
          }}
          className={cn(
            "block w-full resize-none bg-transparent px-3 py-2 text-sm leading-6",
            "text-[var(--color-foreground)] placeholder:text-[var(--color-muted-foreground)]",
            "focus:outline-none disabled:cursor-not-allowed",
          )}
        />
        {/* Attachment preview strip */}
        {attachmentFile ? (
          <div className="mb-2 flex items-center gap-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface-muted)] px-3 py-2 text-sm">
            <span className="truncate flex-1 text-[var(--color-foreground)]">{attachmentFile.name}</span>
            {uploading ? (
              <Loader2 className="size-4 animate-spin text-[var(--color-muted-foreground)]" />
            ) : (
              <button
                type="button"
                onClick={clearAttachment}
                className="rounded p-0.5 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
              >
                <X className="size-4" />
              </button>
            )}
          </div>
        ) : null}

        {/* Reply preview strip */}
        {replyTo ? (
          <div className="mb-2 flex items-start gap-2 rounded-xl border-s-4 border-[var(--color-gold-400)] bg-[var(--color-surface-muted)] px-3 py-2 text-sm">
            <div className="flex-1 min-w-0">
              <span className="block text-[10px] font-semibold text-[var(--color-gold-600)]">
                {replyTo.sender?.fullName ?? "مستخدم"}
              </span>
              <span className="block truncate text-[var(--color-muted-foreground)]">
                {replyTo.content || "(مرفق)"}
              </span>
            </div>
            <button
              type="button"
              onClick={() => setReplyTo(null)}
              className="mt-0.5 rounded p-0.5 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
            >
              <X className="size-4" />
            </button>
          </div>
        ) : null}

        {/* Hidden file input */}
        <input
          ref={fileInputRef}
          type="file"
          className="hidden"
          accept="image/*,application/pdf,.doc,.docx,.xls,.xlsx,.txt"
          onChange={handleAttachmentPick}
        />

        <div className="flex flex-wrap items-center justify-between gap-2 px-2 pb-1 pt-1">
          <p className="text-xs text-[var(--color-muted-foreground)]">
            <kbd className="rounded border border-[var(--color-border)] bg-[var(--color-surface-muted)] px-1.5 py-0.5 text-[10px] font-mono">
              Enter
            </kbd>
            <span className="ms-2">إرسال</span>
            <span className="mx-2 opacity-40">·</span>
            <kbd className="rounded border border-[var(--color-border)] bg-[var(--color-surface-muted)] px-1.5 py-0.5 text-[10px] font-mono">
              Shift+Enter
            </kbd>
            <span className="ms-2">سطر جديد</span>
          </p>
          <div className="flex shrink-0 items-center gap-2">
            {/* Recording indicator */}
            {isRecording ? (
              <div className="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-1.5 text-sm text-red-600">
                <span className="size-2 animate-pulse rounded-full bg-red-500" />
                <span className="font-mono text-xs">{formatRecordTime(recordSeconds)}</span>
                {/* Cancel */}
                <button
                  type="button"
                  title="إلغاء"
                  onClick={cancelRecording}
                  className="rounded p-0.5 hover:text-red-800"
                >
                  <X className="size-3.5" />
                </button>
                {/* Stop & send */}
                <button
                  type="button"
                  title="إيقاف وإرسال"
                  onClick={stopRecording}
                  className="rounded bg-red-500 p-1 text-white hover:bg-red-600"
                >
                  <Send className="size-3 rtl:rotate-180" />
                </button>
              </div>
            ) : null}

            {/* Mic button — only visible when not recording */}
            {!isRecording ? (
              <button
                type="button"
                title="رسالة صوتية"
                disabled={saving || disabled || uploading || !!attachmentFile}
                onClick={() => void startRecording()}
                className="rounded-lg p-1.5 text-[var(--color-muted-foreground)] transition-colors hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-foreground)] disabled:opacity-40"
              >
                <Mic className="size-4" />
              </button>
            ) : null}

            {/* Attachment button */}
            {!isRecording ? (
              <button
                type="button"
                title="إرفاق ملف"
                disabled={saving || disabled || uploading}
                onClick={() => fileInputRef.current?.click()}
                className="rounded-lg p-1.5 text-[var(--color-muted-foreground)] transition-colors hover:bg-[var(--color-surface-muted)] hover:text-[var(--color-foreground)] disabled:opacity-40"
              >
                <Paperclip className="size-4" />
              </button>
            ) : null}
            <Button
              type="button"
              variant="gold"
              size="sm"
              disabled={saving || assistantBusy || disabled || uploading || isRecording || (!content.trim() && !attachmentUrl)}
              onClick={() => void send()}
              className="shrink-0"
            >
              {saving || assistantBusy ? (
                <>
                  <Loader2 className="size-4 animate-spin" />
                  {assistantBusy ? "جاري رد المساعد..." : "جاري الإرسال..."}
                </>
              ) : (
                <>
                  <Send className="size-4 rtl:rotate-180" />
                  إرسال
                </>
              )}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );

  if (embedded) {
    return body;
  }

  return (
    <Card className="overflow-hidden">
      <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border)] bg-[var(--color-surface)] px-5 py-4">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
            <MessageSquare className="size-5" />
          </div>
          <div className="min-w-0">
            <p className="truncate text-base font-semibold text-[var(--color-foreground)]">
              المحادثة
            </p>
            <p className="truncate text-xs text-[var(--color-muted-foreground)]">
              رسائل مباشرة بين المكتب والعميل داخل ملف القضية.
            </p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <ConnectionBadge status={streamStatus} />
          {!loading && !error ? (
            <span className="rounded-full border border-[var(--color-border)] bg-[var(--color-surface-muted)] px-2.5 py-1 text-xs text-[var(--color-muted-foreground)] ltr-nums">
              {items.length} رسالة
            </span>
          ) : null}
        </div>
      </div>
      <div className="p-5">{body}</div>
    </Card>
  );
}

function ConnectionBadge({ status }: { status: StreamStatus }) {
  const isConnected = status === "connected";
  const isConnecting = status === "connecting";
  const Icon = isConnected ? Wifi : WifiOff;
  const label = isConnected
    ? "متصل مباشر"
    : isConnecting
      ? "جاري الاتصال..."
      : "غير متصل مباشر";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[11px]",
        isConnected
          ? "border-emerald-300 bg-emerald-50 text-emerald-700"
          : isConnecting
            ? "border-amber-300 bg-amber-50 text-amber-700"
            : "border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
      )}
      title={label}
    >
      <Icon
        className={cn(
          "size-3",
          isConnected && "animate-none",
          isConnecting && "animate-pulse",
        )}
      />
      <span className="font-medium">{label}</span>
    </span>
  );
}

/* ─── Sub components ────────────────────────────────────────────── */

function AssistantWelcome({
  onQuickQuestion,
  disabled,
}: {
  onQuickQuestion: (question: string) => Promise<void>;
  disabled?: boolean;
}) {
  return (
    <div className="flex h-full min-h-[14rem] flex-col justify-end p-4 sm:p-5">
      <div className="mx-auto w-full max-w-2xl space-y-4">
        <div className="flex gap-2.5">
          <div
            className={cn(
              "flex size-10 shrink-0 select-none items-center justify-center rounded-full ring-1",
              "bg-gradient-to-br from-[var(--color-gold-300)] to-[var(--color-gold-500)]",
              "text-[var(--color-navy-900)] ring-[var(--color-gold-300)] shadow-sm",
            )}
            aria-label="المساعد الذكي"
          >
            <Sparkles className="size-5" />
          </div>
          <div className="min-w-0 flex-1">
            <div className="mb-1 px-1 text-[11px] font-semibold text-[var(--color-foreground)]">
              🤖 المساعد الذكي
            </div>
            <div
              className={cn(
                "rounded-2xl rounded-es-md px-4 py-3 text-sm leading-7 shadow-[var(--shadow-soft)]",
                "border border-[var(--color-gold-300)]",
                "bg-gradient-to-bl from-[var(--color-gold-50)] to-[var(--color-surface)]",
                "text-[var(--color-foreground)]",
              )}
            >
              <p className="whitespace-pre-wrap break-words">{BOT_WELCOME_MESSAGE}</p>
            </div>
          </div>
        </div>

        <QuickQuestionPanel
          title="أسئلة سريعة"
          onQuickQuestion={onQuickQuestion}
          disabled={disabled}
        />
      </div>
    </div>
  );
}

function AssistantReengagement({
  onQuickQuestion,
  disabled,
}: {
  onQuickQuestion: (question: string) => Promise<void>;
  disabled?: boolean;
}) {
  return (
    <div className="mb-2 rounded-2xl border border-[var(--color-gold-200)] bg-[var(--color-gold-50)]/60 p-3">
      <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-[var(--color-foreground)]">
        <Sparkles className="size-4 text-[var(--color-gold-600)]" />
        مرحبًا بعودتك 👋 هل تريد مساعدة سريعة؟
      </div>
      <QuickQuestionPanel
        compact
        onQuickQuestion={onQuickQuestion}
        disabled={disabled}
      />
    </div>
  );
}

function QuickQuestionPanel({
  title,
  compact,
  onQuickQuestion,
  disabled,
}: {
  title?: string;
  compact?: boolean;
  onQuickQuestion: (question: string) => Promise<void>;
  disabled?: boolean;
}) {
  return (
    <div
      className={cn(
        !compact &&
          "rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] p-3 shadow-[var(--shadow-soft)]",
      )}
    >
      {title ? (
        <p className="mb-2 text-xs font-semibold text-[var(--color-muted-foreground)]">
          {title}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {QUICK_QUESTIONS.map((question) => (
          <Button
            key={question}
            type="button"
            variant="outline"
            size="sm"
            disabled={disabled}
            onClick={() => void onQuickQuestion(question)}
            className={cn(
              "rounded-full border-[var(--color-gold-300)] bg-[var(--color-gold-50)]",
              "text-[var(--color-gold-700)] hover:bg-[var(--color-gold-100)]",
              compact && "h-7 px-2.5 text-xs",
            )}
          >
            {question}
          </Button>
        ))}
      </div>
    </div>
  );
}

function AttachmentPreview({ url, mine }: { url: string; mine: boolean }) {
  const filename = url.split("/").pop()?.replace(/^[0-9a-f-]{36}_/, "") ?? "ملف";
  const isImage = /\.(jpe?g|png|gif|webp|svg)$/i.test(url);
  const isAudio = /\.(webm|mp3|ogg|m4a|wav)$/i.test(url);
  const { blobUrl, loading, failed } = useAuthenticatedMediaUrl(url);
  const mediaSrc = blobUrl ?? resolveBackendUrl(url);

  if (loading && (isImage || isAudio)) {
    return (
      <div className="mt-1 flex items-center gap-2 text-xs text-[var(--color-muted-foreground)]">
        <Loader2 className="size-3.5 animate-spin" />
        جاري تحميل المرفق...
      </div>
    );
  }

  if (failed && (isImage || isAudio)) {
    return (
      <p className="mt-1 text-xs text-[var(--color-muted-foreground)]">
        تعذر تحميل المرفق
      </p>
    );
  }

  if (isAudio) {
    return (
      <div className={cn("mt-1 rounded-xl px-1 py-1", mine ? "bg-white/10" : "bg-[var(--color-surface-muted)]")}>
        {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
        <audio controls src={mediaSrc} className="h-9 w-full" style={{ minWidth: 200 }} />
      </div>
    );
  }

  if (isImage) {
    return (
      <a
        href={mediaSrc}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-1 block overflow-hidden rounded-xl"
        title={filename}
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={mediaSrc} alt={filename} className="max-h-48 w-full object-cover" />
      </a>
    );
  }

  return (
    <AuthenticatedFileLink url={url} filename={filename} mine={mine} />
  );
}

function AuthenticatedFileLink({
  url,
  filename,
  mine,
}: {
  url: string;
  filename: string;
  mine: boolean;
}) {
  const { blobUrl, loading } = useAuthenticatedMediaUrl(url);

  return (
    <a
      href={blobUrl ?? resolveBackendUrl(url)}
      download={filename}
      target="_blank"
      rel="noopener noreferrer"
      aria-disabled={loading}
      className={cn(
        "mt-1 flex items-center gap-2 rounded-xl border px-3 py-2 text-xs font-medium transition-opacity hover:opacity-80",
        loading && "pointer-events-none opacity-60",
        mine
          ? "border-white/20 bg-white/10 text-white"
          : "border-[var(--color-border)] bg-[var(--color-surface-muted)] text-[var(--color-foreground)]",
      )}
    >
      {loading ? (
        <Loader2 className="size-3.5 shrink-0 animate-spin" />
      ) : (
        <Paperclip className="size-3.5 shrink-0" />
      )}
      <span className="truncate">{filename}</span>
    </a>
  );
}

function TypingIndicator({ personName }: { personName: string }) {
  return (
    <div className="flex items-end gap-2.5 py-1">
      <div
        className={cn(
          "flex size-9 shrink-0 select-none items-center justify-center rounded-full text-[11px] font-semibold ring-1",
          "bg-[var(--color-gold-50)] text-[var(--color-navy-800)] ring-[var(--color-gold-200)]",
        )}
      >
        {initials(personName, "?")}
      </div>
      <div className="flex flex-col gap-0.5">
        <span className="px-1 text-[11px] font-semibold text-[var(--color-foreground)]">
          {personName}
        </span>
        <div
          className={cn(
            "inline-flex items-center gap-1.5 rounded-2xl rounded-es-md px-4 py-3 shadow-[var(--shadow-soft)]",
            "border border-[var(--color-border)] bg-white",
          )}
        >
          <span className="size-2 animate-bounce rounded-full bg-[var(--color-muted-foreground)]" style={{ animationDelay: "0ms" }} />
          <span className="size-2 animate-bounce rounded-full bg-[var(--color-muted-foreground)]" style={{ animationDelay: "180ms" }} />
          <span className="size-2 animate-bounce rounded-full bg-[var(--color-muted-foreground)]" style={{ animationDelay: "360ms" }} />
        </div>
      </div>
    </div>
  );
}

function DaySeparator({ dateIso }: { dateIso: string }) {
  return (
    <div className="my-2 flex items-center gap-3">
      <div className="h-px flex-1 bg-[var(--color-border)]" />
      <span className="rounded-full border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-0.5 text-[11px] text-[var(--color-muted-foreground)] ltr-nums">
        {formatDateTime(dateIso).split(" · ")[0]}
      </span>
      <div className="h-px flex-1 bg-[var(--color-border)]" />
    </div>
  );
}

type GroupProps = {
  group: {
    senderId: string;
    mine: boolean;
    sender: MessageResponseDto["sender"];
    day: string;
    items: MessageResponseDto[];
  };
  meFullName: string | null;
  onReply: (m: MessageResponseDto) => void;
  onReact: (m: MessageResponseDto, emoji: string) => void;
};

const QUICK_EMOJIS = ["👍", "❤️", "😂", "😮", "👏", "🙏"];

function MessageGroup({ group, meFullName, onReply, onReact }: GroupProps) {
  const { mine, sender, items } = group;
  const senderRole = sender?.systemRole ?? "";
  const senderFullName = sender?.fullName ?? null;
  const senderName = mine
    ? meFullName ?? senderFullName ?? "أنا"
    : senderFullName ?? ROLE_LABELS[senderRole] ?? "مستخدم";
  const roleLabel = ROLE_LABELS[senderRole] ?? senderRole;

  return (
    <div className={cn("flex gap-2.5", mine ? "flex-row-reverse" : "flex-row")}>
      {/* Avatar */}
      <div
        className={cn(
          "flex size-9 shrink-0 select-none items-center justify-center rounded-full text-[11px] font-semibold ring-1",
          mine
            ? "bg-[var(--color-navy-800)] text-[var(--color-gold-300)] ring-[var(--color-navy-700)]"
            : "bg-[var(--color-gold-50)] text-[var(--color-navy-800)] ring-[var(--color-gold-200)]",
        )}
        title={senderName}
      >
        {initials(senderName, "?")}
      </div>

      {/* Bubble column */}
      <div
        className={cn(
          "flex min-w-0 max-w-[78%] flex-col gap-1 sm:max-w-[70%]",
          mine ? "items-end" : "items-start",
        )}
      >
        {/* Header — name + role */}
        <div
          className={cn(
            "flex items-baseline gap-2 px-1 text-[11px]",
            mine ? "flex-row-reverse" : "flex-row",
          )}
        >
          <span className="font-semibold text-[var(--color-foreground)]">
            {senderName}
          </span>
          <span className="text-[var(--color-muted-foreground)]">
            {roleLabel}
          </span>
        </div>

        {/* Bubbles */}
        {items.map((m, i) => {
          const isFirst = i === 0;
          const isLast = i === items.length - 1;
          return (
            <div key={m.id} className="group/msg-wrap relative">
              {/* Hover action bar */}
              <div
                className={cn(
                  "absolute -top-7 z-30 flex items-center gap-0.5 rounded-xl border border-[var(--color-border)] bg-white px-1.5 py-1 shadow-md opacity-0 transition-opacity duration-150 group-hover/msg-wrap:opacity-100",
                  mine ? "end-0" : "start-0",
                )}
              >
                {QUICK_EMOJIS.map((emoji) => (
                  <button
                    key={emoji}
                    type="button"
                    onClick={() => onReact(m, emoji)}
                    className={cn(
                      "rounded px-1 text-sm leading-none transition-transform hover:scale-125",
                      m.myReaction === emoji && "bg-[var(--color-gold-50)]",
                    )}
                  >
                    {emoji}
                  </button>
                ))}
                <button
                  type="button"
                  title="رد"
                  onClick={() => onReply(m)}
                  className="ms-1 rounded p-0.5 text-[var(--color-muted-foreground)] hover:text-[var(--color-foreground)]"
                >
                  <CornerUpLeft className="size-3.5" />
                </button>
              </div>

              {/* Bubble */}
              <div
                className={cn(
                  "relative px-4 py-2.5 text-sm leading-6",
                  mine
                    ? "bg-gradient-to-br from-[var(--color-navy-700)] to-[var(--color-navy-900)] text-white shadow-[0_3px_14px_rgba(30,45,78,0.4)]"
                    : "border border-[var(--color-border)] bg-white text-[var(--color-foreground)] shadow-sm",
                  "rounded-2xl",
                  mine ? (isFirst ? "rounded-ee-md" : "") : isFirst ? "rounded-es-md" : "",
                  mine ? (isLast ? "rounded-ee-sm" : "") : isLast ? "rounded-es-sm" : "",
                )}
              >
                {/* Hover datetime tooltip */}
                <div className="pointer-events-none absolute -top-6 inset-x-0 z-20 flex justify-center opacity-0 transition-opacity duration-150 group-hover/msg-wrap:opacity-100">
                  <span className="whitespace-nowrap rounded-md bg-gray-900/90 px-2 py-0.5 text-[10px] text-white shadow-md">
                    {formatDateTime(m.createdAt)}
                  </span>
                </div>

                {/* Reply preview */}
                {m.replyTo ? (
                  <div
                    className={cn(
                      "mb-2 rounded-lg border-s-4 px-2.5 py-1.5 text-[11px]",
                      mine
                        ? "border-white/40 bg-white/10 text-white/80"
                        : "border-[var(--color-gold-400)] bg-[var(--color-surface-muted)] text-[var(--color-muted-foreground)]",
                    )}
                  >
                    <span className={cn("block font-semibold", mine ? "text-white/90" : "text-[var(--color-foreground)]")}>
                      {m.replyTo.senderName ?? "مستخدم"}
                    </span>
                    <span className="line-clamp-1">{m.replyTo.content}</span>
                  </div>
                ) : null}

                {m.content ? (
                  <p className="whitespace-pre-wrap break-words">{m.content}</p>
                ) : null}

                {/* Attachment */}
                {m.attachmentUrl ? (
                  <AttachmentPreview url={m.attachmentUrl} mine={mine} />
                ) : null}

                {isLast ? (
                  <span
                    className={cn(
                      "mt-1 inline-flex items-center gap-1 text-[10px] ltr-nums",
                      mine
                        ? "text-white/55"
                        : "text-[var(--color-muted-foreground)]",
                    )}
                  >
                    {formatTime(m.createdAt)}
                    {mine && (
                      m.read
                        ? <CheckCheck className="size-3 text-[var(--color-gold-400)]" />
                        : <Check className="size-3 opacity-50" />
                    )}
                  </span>
                ) : null}
              </div>

              {/* Reaction bar below bubble */}
              {m.reactions && Object.keys(m.reactions).length > 0 ? (
                <div className={cn("mt-1 flex flex-wrap gap-1", mine ? "justify-end" : "justify-start")}>
                  {Object.entries(m.reactions).map(([emoji, count]) => (
                    <button
                      key={emoji}
                      type="button"
                      onClick={() => onReact(m, emoji)}
                      className={cn(
                        "flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] transition-colors",
                        m.myReaction === emoji
                          ? "border-[var(--color-gold-400)] bg-[var(--color-gold-50)] text-[var(--color-navy-800)]"
                          : "border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)] hover:border-[var(--color-gold-300)]",
                      )}
                    >
                      <span>{emoji}</span>
                      <span className="font-medium">{count}</span>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function BotBubble({ message }: { message: MessageResponseDto }) {
  return (
    <div className="flex flex-row gap-2.5">
      {/* Bot avatar */}
      <div
        className={cn(
          "flex size-9 shrink-0 select-none items-center justify-center rounded-full text-base ring-1",
          "bg-gradient-to-br from-[var(--color-gold-300)] to-[var(--color-gold-500)]",
          "text-[var(--color-navy-900)] ring-[var(--color-gold-300)] shadow-sm",
        )}
        title="المساعد"
        aria-label="المساعد"
      >
        <Sparkles className="size-4" />
      </div>

      <div className="flex min-w-0 max-w-[78%] flex-col gap-1 sm:max-w-[70%]">
        {/* Header */}
        <div className="flex items-baseline gap-2 px-1 text-[11px]">
          <span className="font-semibold text-[var(--color-foreground)]">
            🤖 المساعد
          </span>
        </div>

        {/* Bot answer bubble */}
        <div
          className={cn(
            "rounded-2xl rounded-es-md px-4 py-2.5 text-sm leading-6 shadow-[var(--shadow-soft)]",
            "border border-[var(--color-gold-300)]",
            "bg-gradient-to-bl from-[var(--color-gold-50)] to-[var(--color-surface)]",
            "text-[var(--color-foreground)]",
          )}
        >
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
          <span
            className="ms-2 mt-1 inline-block text-[10px] text-[var(--color-muted-foreground)] ltr-nums"
            title={formatDateTime(message.createdAt)}
          >
            {formatTime(message.createdAt)}
          </span>
        </div>
      </div>
    </div>
  );
}

type StateKind = "loading" | "empty" | "error";

function ChatStateMessage({
  kind,
  title,
  description,
  actionLabel,
  onAction,
}: {
  kind: StateKind;
  title: string;
  description?: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  const Icon =
    kind === "loading" ? Loader2 : kind === "error" ? AlertCircle : MessageSquare;
  return (
    <div className="flex h-full min-h-[14rem] flex-col items-center justify-center px-6 py-10 text-center">
      <div
        className={cn(
          "mb-3 flex size-12 items-center justify-center rounded-2xl",
          kind === "error"
            ? "bg-[var(--color-danger-bg)] text-[var(--color-danger)]"
            : "bg-[var(--color-navy-50)] text-[var(--color-navy-700)]",
        )}
      >
        <Icon className={cn("size-6", kind === "loading" && "animate-spin")} />
      </div>
      <p className="text-sm font-semibold text-[var(--color-foreground)]">
        {title}
      </p>
      {description ? (
        <p className="mt-1 max-w-sm text-xs text-[var(--color-muted-foreground)]">
          {description}
        </p>
      ) : null}
      {actionLabel && onAction ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-4"
          onClick={onAction}
        >
          {actionLabel}
        </Button>
      ) : null}
    </div>
  );
}

/* ─── Floating chat button ──────────────────────────────────────── */

type ChatButtonProps = {
  caseId: string;
  disabled?: boolean;
};

export function CaseChatButton({ caseId, disabled }: ChatButtonProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="fixed bottom-5 end-5 z-[9999]">
      {open ? (
        <Card className="mb-3 flex max-h-[calc(100vh-7rem)] w-[min(26rem,calc(100vw-2rem))] flex-col overflow-hidden rounded-2xl shadow-2xl">
          <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border)] bg-[var(--color-navy-900)] px-4 py-3 text-white">
            <div className="flex min-w-0 items-center gap-2.5">
              <div className="flex size-9 items-center justify-center rounded-xl bg-[var(--color-gold-400)] text-[var(--color-navy-900)]">
                <MessageSquare className="size-4" />
              </div>
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold">المحادثة</p>
                <p className="truncate text-xs text-[var(--color-navy-300)]">
                  المكتب والعميل
                </p>
              </div>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="text-white hover:bg-white/10 hover:text-white"
              onClick={() => setOpen(false)}
              aria-label="إغلاق"
            >
              <X className="size-4" />
            </Button>
          </div>
          <div className="bg-[var(--color-surface)] p-4">
            <CaseMessagesPanel caseId={caseId} disabled={disabled} embedded />
          </div>
        </Card>
      ) : null}
      <Button
        type="button"
        size="lg"
        className={cn(
          "h-14 rounded-full px-5 shadow-2xl",
          "bg-[var(--color-gold-400)] text-[var(--color-navy-900)] hover:bg-[var(--color-gold-300)]",
        )}
        onClick={() => setOpen((v) => !v)}
      >
        <MessageCircle className="size-5" />
        {open ? "إغلاق المحادثة" : "محادثة"}
      </Button>
    </div>
  );
}
