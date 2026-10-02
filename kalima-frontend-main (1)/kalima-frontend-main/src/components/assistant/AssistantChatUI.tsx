"use client";

import {
  AlertTriangle,
  Brain,
  Briefcase,
  Mic,
  MicOff,
  Send,
  Sparkles,
  UserRound,
  Volume2,
  VolumeX,
  X,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Textarea } from "@/components/ui/textarea";
import { useAssistant } from "@/components/assistant/AssistantProvider";
import { NO_CASE, type AssistantChatMessage } from "@/lib/assistant/types";
import { describePageContext } from "@/lib/assistant/page-context";
import { cn } from "@/lib/utils/cn";

type AssistantChatUIProps = {
  variant?: "page" | "overlay";
  onClose?: () => void;
};

export function AssistantChatUI({
  variant = "page",
  onClose,
}: AssistantChatUIProps) {
  const {
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
  } = useAssistant();

  const isOverlay = variant === "overlay";

  return (
    <Card
      className={cn(
        "flex flex-col overflow-hidden border border-[var(--color-border)] bg-white shadow-[var(--shadow-soft)]",
        isOverlay
          ? "h-full min-h-0 rounded-none border-0 shadow-none"
          : "min-h-[34rem] rounded-2xl",
      )}
    >
      <div className="flex flex-col gap-3 border-b border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
        <div className="flex items-center gap-3">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-2xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
            <Brain className="size-5" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-bold text-[var(--color-foreground)]">
              المساعد القانوني
            </p>
            <p className="mt-0.5 text-xs text-[var(--color-muted-foreground)]">
              {statusHint}
            </p>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {shouldAttemptSpeak ? (
            <>
              <button
                type="button"
                onClick={toggleAutoSpeak}
                disabled={thinking}
                title={
                  autoSpeak
                    ? "إيقاف الرد الصوتي التلقائي"
                    : "تفعيل الرد الصوتي التلقائي"
                }
                className={cn(
                  "inline-flex items-center gap-1.5 rounded-xl border px-2.5 py-1.5 text-xs font-semibold transition-colors",
                  autoSpeak
                    ? "border-[var(--color-gold-300)] bg-[var(--color-gold-50)] text-[var(--color-gold-700)]"
                    : "border-[var(--color-border)] bg-white text-[var(--color-muted-foreground)]",
                )}
              >
                {autoSpeak ? (
                  <Volume2 className="size-3.5" />
                ) : (
                  <VolumeX className="size-3.5" />
                )}
                رد تلقائي
              </button>
              {voiceOptions.length > 0 ? (
                <select
                  value={preferredVoice ?? ""}
                  onChange={(e) => handleVoiceChange(e.target.value)}
                  disabled={thinking}
                  title="اختر صوت المساعد"
                  className={cn(
                    "h-8 max-w-[11rem] truncate rounded-xl border border-[var(--color-border)] bg-white px-2 text-xs text-[var(--color-foreground)]",
                    "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)]",
                  )}
                >
                  <option value="">
                    {speechConfig?.ttsProvider === "AZURE"
                      ? "صوت Azure تلقائي"
                      : "صوت عربي تلقائي"}
                  </option>
                  {voiceOptions.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.label}
                      {speechConfig?.ttsProvider !== "AZURE" ? ` (${v.lang})` : ""}
                    </option>
                  ))}
                </select>
              ) : null}
            </>
          ) : null}
          {messages.length > 0 ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={handleClear}
              disabled={thinking}
            >
              مسح المحادثة
            </Button>
          ) : null}
          {isOverlay && onClose ? (
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={onClose}
              aria-label="إغلاق المساعد"
            >
              <X className="size-4" />
            </Button>
          ) : null}
          {speechConfig?.ttsProvider === "AZURE" ? (
            <span className="inline-flex items-center gap-1 rounded-full border border-sky-200 bg-sky-50 px-2 py-1 text-[10px] font-semibold text-sky-800">
              Azure Voice
            </span>
          ) : null}
          <span className="hidden items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1 text-xs font-medium text-emerald-700 sm:inline-flex">
            <Sparkles className="size-3.5" />
            تحليل وتنفيذ ذكي
          </span>
        </div>
      </div>

      {pageContext.pageLabel ? (
        <div className="border-b border-[var(--color-border)] bg-[var(--color-gold-50)]/50 px-4 py-2 text-xs text-[var(--color-gold-800)] sm:px-5">
          <span className="font-semibold">سياق الصفحة: </span>
          {describePageContext(pageContext)}
        </div>
      ) : null}

      <div className="flex flex-col gap-2 border-b border-[var(--color-border)] bg-white px-4 py-3 sm:px-5">
        <div className="flex items-center gap-2">
          <Briefcase className="size-4 text-[var(--color-navy-600)]" />
          <label
            htmlFor={isOverlay ? "assistant-case-overlay" : "assistant-case"}
            className="text-xs font-medium text-[var(--color-foreground)]"
          >
            نطاق المحادثة
          </label>
        </div>
        <select
          id={isOverlay ? "assistant-case-overlay" : "assistant-case"}
          value={selectedCaseId}
          onChange={(e) => setSelectedCaseId(e.target.value)}
          disabled={casesLoading}
          className={cn(
            "h-10 w-full rounded-xl border border-[var(--color-border)] bg-white px-3 text-sm text-[var(--color-foreground)] shadow-sm transition-colors",
            "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-gold-400)] focus-visible:border-[var(--color-gold-400)]",
            "disabled:cursor-not-allowed disabled:opacity-60",
          )}
        >
          <option value={NO_CASE}>
            {casesLoading
              ? "جاري تحميل القضايا..."
              : "بدون قضية محددة (أسئلة عامة)"}
          </option>
          {cases.map((c) => {
            const client = c.client?.fullName ?? "—";
            return (
              <option key={c.id} value={c.id}>
                {`#${c.caseNumber} — ${c.title} — ${client}`}
              </option>
            );
          })}
        </select>
        {casesError ? (
          <p className="text-xs text-[var(--color-danger,_#dc2626)]">
            {casesError}
          </p>
        ) : selectedCase ? (
          <p className="text-[11px] text-[var(--color-muted-foreground)] ltr-nums">
            سيتم ربط أسئلتك وأوامرك بقضية #{selectedCase.caseNumber} تلقائياً.
          </p>
        ) : (
          <p className="text-[11px] text-[var(--color-muted-foreground)]">
            اسأل عن تحليل قضية، أو قل: «اعرض قائمة القضايا»، «جهزني للجلسة بكرة».
          </p>
        )}
      </div>

      <div
        ref={listRef}
        className="min-h-0 flex-1 overflow-y-auto bg-[var(--color-surface-muted)]/25 p-4 sm:p-5"
      >
        {messages.length === 0 ? (
          <EmptyAssistant />
        ) : (
          <ul className="flex flex-col gap-3">
            {messages.map((msg) =>
              msg.role === "user" ? (
                <UserBubble key={msg.id} message={msg} />
              ) : (
                <AssistantBubble
                  key={msg.id}
                  message={msg}
                  onSpeak={handleSpeak}
                  speaking={speakingId === msg.id}
                  supportsTts={shouldAttemptSpeak}
                />
              ),
            )}
            {thinking ? <ThinkingBubble /> : null}
          </ul>
        )}
      </div>

      {error ? (
        <div className="border-t border-red-200 bg-red-50 px-4 py-2 text-xs text-red-800">
          {error}
        </div>
      ) : null}
      {voiceError ? (
        <div className="border-t border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-800">
          {voiceError}
        </div>
      ) : null}
      {pendingActionId ? (
        <div className="flex flex-col gap-3 border-t border-orange-200 bg-orange-50 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-2">
            <AlertTriangle className="mt-0.5 size-4 shrink-0 text-orange-600" />
            <p className="text-sm text-orange-900">
              <span className="font-semibold">يتطلب تأكيدك: </span>
              {pendingSummary ?? "إجراء حساس يحتاج موافقتك."}
            </p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={handleCancelPending}
              disabled={thinking}
            >
              إلغاء
            </Button>
            <Button
              type="button"
              variant="gold"
              size="sm"
              onClick={handleConfirmPending}
              disabled={thinking}
            >
              تأكيد التنفيذ
            </Button>
          </div>
        </div>
      ) : null}

      <form
        onSubmit={handleSubmit}
        className="flex flex-col gap-2 border-t border-[var(--color-border)] bg-white p-3 sm:p-4"
      >
        <Textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="اسأل أو اطلب: «لخّص القضية»، «اعرض القضايا»، «جهزني للجلسة»..."
          rows={isOverlay ? 2 : 3}
          disabled={thinking}
          className={cn("resize-none", isOverlay ? "min-h-[72px]" : "min-h-[88px]")}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              if (input.trim() && !thinking) {
                (e.currentTarget.form as HTMLFormElement | null)?.requestSubmit();
              }
            }
          }}
        />
        <div className="flex items-center justify-between gap-3">
          <p className="text-[11px] text-[var(--color-muted-foreground)]">
            {voiceConversation
              ? listening
                ? "تكلّم الآن — سيُرسل كلامك تلقائياً."
                : speakingId
                  ? "استمع للرد... ثم تكلّم من جديد."
                  : "محادثة صوتية نشطة."
              : listening
                ? "جاري الاستماع..."
                : "Enter للإرسال — أو «محادثة صوتية»."}
          </p>
          <div className="flex items-center gap-2">
            {supportsSpeechInput ? (
              <Button
                type="button"
                variant={voiceConversation ? "destructive" : "gold"}
                size="md"
                onClick={toggleVoiceConversation}
                disabled={thinking && !voiceConversation}
                title={
                  voiceConversation
                    ? "إنهاء المحادثة الصوتية"
                    : "ابدأ محادثة صوتية"
                }
                className={cn(
                  voiceConversation &&
                    !listening &&
                    !speakingId &&
                    "animate-pulse",
                )}
              >
                {voiceConversation ? (
                  <MicOff className="size-4" />
                ) : (
                  <Mic className="size-4" />
                )}
                {voiceConversation ? "إيقاف" : "محادثة"}
              </Button>
            ) : null}
            <Button
              type="button"
              variant={listening ? "destructive" : "outline"}
              size="md"
              onClick={toggleListening}
              disabled={thinking || !supportsSpeechInput}
            >
              {listening ? (
                <MicOff className="size-4" />
              ) : (
                <Mic className="size-4" />
              )}
            </Button>
            <Button
              type="submit"
              variant="gold"
              size="md"
              disabled={!input.trim() || thinking}
              className="min-w-24"
            >
              <Send className="size-4" />
              {thinking ? "..." : "إرسال"}
            </Button>
          </div>
        </div>
      </form>
    </Card>
  );
}

function EmptyAssistant() {
  return (
    <div className="flex h-full min-h-[14rem] flex-col items-center justify-center gap-3 text-center">
      <div className="flex size-14 items-center justify-center rounded-2xl bg-[var(--color-gold-50)] text-[var(--color-gold-500)]">
        <Brain className="size-7" />
      </div>
      <div>
        <p className="text-base font-semibold text-[var(--color-foreground)]">
          المساعد جاهز
        </p>
        <p className="mt-1 text-sm text-[var(--color-muted-foreground)]">
          اسأل عن تحليل قضية، أو اطلب تنفيذ أمر مثل «اعرض القضايا».
        </p>
      </div>
    </div>
  );
}

function UserBubble({ message }: { message: AssistantChatMessage }) {
  return (
    <li className="flex justify-end">
      <div className="flex max-w-[80%] items-end gap-2">
        <div className="rounded-2xl rounded-bl-sm bg-[var(--color-navy-800)] px-4 py-2.5 text-sm leading-6 text-white shadow-sm">
          <p className="whitespace-pre-wrap">{message.content}</p>
        </div>
        <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-navy-100)] text-[var(--color-navy-700)]">
          <UserRound className="size-4" />
        </div>
      </div>
    </li>
  );
}

function AssistantBubble({
  message,
  onSpeak,
  speaking,
  supportsTts,
}: {
  message: AssistantChatMessage;
  onSpeak: (msg: AssistantChatMessage) => void;
  speaking: boolean;
  supportsTts: boolean;
}) {
  return (
    <li className="flex justify-start">
      <div className="flex max-w-[85%] items-start gap-2">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-gold-100)] text-[var(--color-gold-600)]">
          <Brain className="size-4" />
        </div>
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-1.5">
            <p className="text-[11px] font-medium text-[var(--color-muted-foreground)]">
              المساعد القانوني
            </p>
            {message.isAgent ? (
              <span className="inline-flex rounded-full border border-emerald-200 bg-emerald-50 px-1.5 py-0.5 text-[10px] font-medium text-emerald-700">
                تنفيذ
              </span>
            ) : null}
            {message.contextUsed ? (
              <span className="inline-flex items-center gap-1 rounded-full border border-[var(--color-gold-300)] bg-[var(--color-gold-50)] px-1.5 py-0.5 text-[10px] font-medium text-[var(--color-gold-700,_#92760a)]">
                <Briefcase className="size-2.5" />
                بيانات القضية
              </span>
            ) : null}
            {message.documentsUsed ? (
              <span className="inline-flex rounded-full border border-sky-200 bg-sky-50 px-1.5 py-0.5 text-[10px] font-medium text-sky-800">
                مستندات
              </span>
            ) : null}
            {message.provider ? (
              <span
                className="inline-flex rounded-full border border-[var(--color-border)] bg-white px-1.5 py-0.5 text-[10px] font-medium text-[var(--color-muted-foreground)]"
                title={message.model}
              >
                {message.provider}
              </span>
            ) : null}
            {message.toolsUsed && message.toolsUsed.length > 0 ? (
              <span
                className="inline-flex max-w-[12rem] truncate rounded-full border border-emerald-200 bg-emerald-50 px-1.5 py-0.5 text-[10px] font-medium text-emerald-700"
                title={message.toolsUsed.join(", ")}
              >
                {message.toolsUsed.join(" · ")}
              </span>
            ) : null}
            {message.streaming ? (
              <span className="inline-flex size-1.5 animate-pulse rounded-full bg-[var(--color-gold-500)]" />
            ) : null}
          </div>
          <div className="rounded-2xl rounded-br-sm border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] px-4 py-2.5 text-sm leading-6 text-[var(--color-foreground)] shadow-sm">
            <div className="flex items-start gap-3">
              <p className="flex-1 whitespace-pre-wrap">{message.content}</p>
              {supportsTts && !message.streaming ? (
                <button
                  type="button"
                  onClick={() => onSpeak(message)}
                  title={speaking ? "إيقاف القراءة" : "قراءة صوتية"}
                  aria-label={speaking ? "إيقاف القراءة" : "قراءة صوتية"}
                  className={cn(
                    "mt-0.5 inline-flex size-7 shrink-0 items-center justify-center rounded-full border border-transparent text-[var(--color-navy-700)] transition-all",
                    "hover:border-[var(--color-gold-300)] hover:bg-white hover:text-[var(--color-gold-600)]",
                    speaking &&
                      "border-[var(--color-gold-400)] bg-white text-[var(--color-gold-600)] shadow-sm",
                  )}
                >
                  {speaking ? (
                    <VolumeX className="size-3.5" />
                  ) : (
                    <Volume2 className="size-3.5" />
                  )}
                </button>
              ) : null}
            </div>
          </div>
        </div>
      </div>
    </li>
  );
}

function ThinkingBubble() {
  return (
    <li className="flex justify-start">
      <div className="flex items-start gap-2">
        <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-[var(--color-gold-100)] text-[var(--color-gold-600)]">
          <Brain className="size-4" />
        </div>
        <div className="rounded-2xl rounded-br-sm border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] px-4 py-3 shadow-sm">
          <div className="flex items-center gap-1.5">
            <span className="size-1.5 animate-pulse rounded-full bg-[var(--color-gold-500)]" />
            <span
              className="size-1.5 animate-pulse rounded-full bg-[var(--color-gold-500)]"
              style={{ animationDelay: "150ms" }}
            />
            <span
              className="size-1.5 animate-pulse rounded-full bg-[var(--color-gold-500)]"
              style={{ animationDelay: "300ms" }}
            />
          </div>
        </div>
      </div>
    </li>
  );
}
