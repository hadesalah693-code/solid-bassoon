"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AnimatePresence, motion } from "framer-motion";
import {
  ArrowRight,
  ChevronLeft,
  Loader2,
  MessageCircle,
  MessageSquare,
  Search,
  Send,
  Sparkles,
  User,
  X,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { ApiRequestError } from "@/lib/api/client";
import { chatbotApi } from "@/lib/api/chatbot";
import {
  chatScopeApi,
  type ChatScopeCase,
  type ChatScopeClient,
} from "@/lib/api/chat-scope";
import { messagesApi } from "@/lib/api/messages";
import { getStoredUser } from "@/lib/auth/session";
import { prependUniqueMessages } from "@/lib/messages/message-list-utils";
import { notifyApiError, notifyError } from "@/lib/feedback";
import { useDebounce } from "@/hooks/use-debounce";
import { cn } from "@/lib/utils/cn";
import { formatDateTime, formatTime } from "@/lib/utils/format-date";
import type { MessageResponseDto } from "@/types/api";

/* ─── types ────────────────────────────────────────────────────── */

type Step = "clients" | "cases" | "chat";

/* ─── helpers ──────────────────────────────────────────────────── */

function clientLabel(p: ChatScopeClient): string {
  return p.fullName ?? p.companyName ?? p.email ?? "عميل";
}

function caseLabel(c: ChatScopeCase): string {
  return `${c.caseNumber} — ${c.title}`;
}

function matchesQuery(p: ChatScopeClient, q: string): boolean {
  if (!q) return true;
  const needle = q.toLowerCase();
  return (
    (p.fullName ?? "").toLowerCase().includes(needle) ||
    (p.companyName ?? "").toLowerCase().includes(needle) ||
    (p.email ?? "").toLowerCase().includes(needle)
  );
}

/* ─── main component ───────────────────────────────────────────── */

export function GlobalChatLauncher() {
  const [open, setOpen] = useState(false);
  const [step, setStep] = useState<Step>("clients");

  const [client, setClient] = useState<ChatScopeClient | null>(null);
  const [caseFile, setCaseFile] = useState<ChatScopeCase | null>(null);

  function reset() {
    setStep("clients");
    setClient(null);
    setCaseFile(null);
  }

  function close() {
    setOpen(false);
    setTimeout(reset, 300);
  }

  return (
    <>
      {/* Floating launcher button */}
      <motion.button
        type="button"
        onClick={() => setOpen(true)}
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.95 }}
        aria-label="فتح المحادثات"
        className={cn(
          "fixed bottom-5 end-5 z-[80] flex h-14 items-center gap-2 rounded-full px-5",
          "bg-[var(--color-navy-900)] text-[var(--color-gold-400)]",
          "shadow-[0_8px_28px_rgba(11,20,38,0.45),_0_0_0_1px_rgba(212,175,55,0.18)]",
          "hover:bg-[var(--color-navy-800)]",
        )}
      >
        <MessageCircle className="size-5" />
        <span className="text-sm font-semibold">المحادثات</span>
      </motion.button>

      {/* Drawer */}
      <AnimatePresence>
        {open ? (
          <>
            <motion.div
              key="overlay"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={close}
              className="fixed inset-0 z-[90] bg-black/40 backdrop-blur-sm"
            />
            <motion.aside
              key="drawer"
              initial={{ x: "100%" }}
              animate={{ x: 0 }}
              exit={{ x: "100%" }}
              transition={{ type: "spring", damping: 28, stiffness: 280 }}
              className={cn(
                "fixed inset-y-0 end-0 z-[91] flex w-full max-w-md flex-col",
                "bg-[var(--color-surface)] shadow-2xl",
                "border-s border-[var(--color-border)]",
              )}
            >
              <DrawerHeader
                step={step}
                client={client}
                caseFile={caseFile}
                onClose={close}
                onBack={() => {
                  if (step === "chat") {
                    setStep("cases");
                    setCaseFile(null);
                  } else if (step === "cases") {
                    setStep("clients");
                    setClient(null);
                  } else {
                    close();
                  }
                }}
              />

              <div className="flex-1 min-h-0 overflow-hidden">
                {step === "clients" ? (
                  <ClientsStep
                    onPick={(c) => {
                      setClient(c);
                      setStep("cases");
                    }}
                  />
                ) : null}

                {step === "cases" && client ? (
                  <CasesStep
                    client={client}
                    onPick={(c) => {
                      setCaseFile(c);
                      setStep("chat");
                    }}
                  />
                ) : null}

                {step === "chat" && caseFile ? (
                  <ChatStep caseFile={caseFile} />
                ) : null}
              </div>
            </motion.aside>
          </>
        ) : null}
      </AnimatePresence>
    </>
  );
}

/* ─── header ───────────────────────────────────────────────────── */

function DrawerHeader({
  step,
  client,
  caseFile,
  onClose,
  onBack,
}: {
  step: Step;
  client: ChatScopeClient | null;
  caseFile: ChatScopeCase | null;
  onClose: () => void;
  onBack: () => void;
}) {
  const title =
    step === "clients"
      ? "اختر عميلاً"
      : step === "cases"
        ? "اختر القضية"
        : "المحادثة";
  const subtitle =
    step === "clients"
      ? "ابدأ محادثة مع أحد العملاء"
      : step === "cases" && client
        ? clientLabel(client)
        : caseFile
          ? caseLabel(caseFile)
          : "";

  const showBack = step !== "clients";

  return (
    <header
      className={cn(
        "flex shrink-0 items-center gap-3 border-b border-[var(--color-border)]",
        "bg-[var(--color-navy-900)] px-4 py-4 text-white",
      )}
    >
      {showBack ? (
        <button
          type="button"
          onClick={onBack}
          aria-label="رجوع"
          className="rounded-lg p-1.5 text-white/70 transition-colors hover:bg-white/10 hover:text-white"
        >
          <ChevronLeft className="size-5 rtl:rotate-180" />
        </button>
      ) : (
        <div
          className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-[var(--color-gold-400)] text-[var(--color-navy-900)]"
          style={{ boxShadow: "0 0 18px rgba(212,175,55,0.35)" }}
        >
          <MessageSquare className="size-4" />
        </div>
      )}
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">{title}</p>
        {subtitle ? (
          <p className="truncate text-xs text-[var(--color-navy-300)]">
            {subtitle}
          </p>
        ) : null}
      </div>
      <button
        type="button"
        onClick={onClose}
        aria-label="إغلاق"
        className="rounded-lg p-1.5 text-white/70 transition-colors hover:bg-white/10 hover:text-white"
      >
        <X className="size-5" />
      </button>
    </header>
  );
}

/* ─── step 1: clients ──────────────────────────────────────────── */

function ClientsStep({ onPick }: { onPick: (c: ChatScopeClient) => void }) {
  const [search, setSearch] = useState("");
  const debounced = useDebounce(search, 200);
  const [items, setItems] = useState<ChatScopeClient[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        // Backend filters this list to only clients the actor can chat with.
        const res = await chatScopeApi.listClients();
        if (cancelled) return;
        setItems(res);
      } catch (e) {
        if (cancelled) return;
        setError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل قائمة العملاء",
        );
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Search is purely client-side over the already-authorised list.
  const visible = items.filter((p) => matchesQuery(p, debounced.trim()));

  return (
    <div className="flex h-full flex-col">
      <div className="shrink-0 border-b border-[var(--color-border)] bg-[var(--color-surface)] p-3">
        <div className="relative">
          <Search className="pointer-events-none absolute start-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-muted-foreground)]" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="ابحث بالاسم أو البريد..."
            className={cn(
              "h-10 w-full rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] ps-9 pe-3 text-sm",
              "placeholder:text-[var(--color-muted-foreground)]",
              "focus:outline-none focus:border-[var(--color-gold-400)] focus:shadow-[0_0_0_3px_rgba(212,175,55,0.15)]",
            )}
          />
        </div>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto">
        {loading ? (
          <CenteredState
            icon={Loader2}
            spin
            title="جاري تحميل العملاء..."
          />
        ) : error ? (
          <CenteredState icon={MessageSquare} title="تعذر التحميل" desc={error} />
        ) : visible.length === 0 ? (
          <CenteredState
            icon={User}
            title="لا يوجد عملاء"
            desc={
              search
                ? "لا نتائج لبحثك."
                : "لا يوجد عملاء ضمن صلاحياتك للدردشة."
            }
          />
        ) : (
          <ul className="divide-y divide-[var(--color-border)]">
            {visible.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  onClick={() => onPick(p)}
                  className={cn(
                    "flex w-full items-center gap-3 px-4 py-3 text-start transition-colors",
                    "hover:bg-[var(--color-surface-muted)]",
                  )}
                >
                  <div className="flex size-10 shrink-0 items-center justify-center rounded-full bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
                    <User className="size-5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-[var(--color-foreground)]">
                      {clientLabel(p)}
                    </p>
                    {p.email ? (
                      <p className="truncate text-xs text-[var(--color-muted-foreground)] ltr-nums">
                        {p.email}
                      </p>
                    ) : null}
                  </div>
                  <ArrowRight className="size-4 shrink-0 text-[var(--color-muted-foreground)] rtl:rotate-180" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

/* ─── step 2: cases ────────────────────────────────────────────── */

function CasesStep({
  client,
  onPick,
}: {
  client: ChatScopeClient;
  onPick: (c: ChatScopeCase) => void;
}) {
  const [items, setItems] = useState<ChatScopeCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const autoPickedRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        // Backend filters to only cases the actor can chat in for this client.
        const res = await chatScopeApi.listCases(client.id);
        if (cancelled) return;
        setItems(res);
        if (res.length === 1 && !autoPickedRef.current) {
          autoPickedRef.current = true;
          onPick(res[0]);
        }
      } catch (e) {
        if (cancelled) return;
        setError(
          e instanceof ApiRequestError
            ? e.details?.message ?? e.message
            : "تعذر تحميل قضايا العميل",
        );
        setItems([]);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [client.id, onPick]);

  if (loading) {
    return <CenteredState icon={Loader2} spin title="جاري تحميل القضايا..." />;
  }
  if (error) {
    return <CenteredState icon={MessageSquare} title="تعذر التحميل" desc={error} />;
  }
  if (items.length === 0) {
    return (
      <CenteredState
        icon={MessageSquare}
        title="لا توجد قضايا"
        desc="لا توجد قضايا متاحة لك مع هذا العميل."
      />
    );
  }
  return (
    <ul className="divide-y divide-[var(--color-border)]">
      {items.map((c) => (
        <li key={c.id}>
          <button
            type="button"
            onClick={() => onPick(c)}
            className={cn(
              "flex w-full items-center gap-3 px-4 py-3 text-start transition-colors",
              "hover:bg-[var(--color-surface-muted)]",
            )}
          >
            <div className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-[var(--color-gold-50)] text-[var(--color-gold-600)]">
              <MessageSquare className="size-5" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-[var(--color-foreground)] ltr-nums">
                {c.caseNumber}
              </p>
              <p className="truncate text-xs text-[var(--color-muted-foreground)]">
                {c.title}
              </p>
            </div>
            <ArrowRight className="size-4 shrink-0 text-[var(--color-muted-foreground)] rtl:rotate-180" />
          </button>
        </li>
      ))}
    </ul>
  );
}

/* ─── step 3: chat ─────────────────────────────────────────────── */

function ChatStep({ caseFile }: { caseFile: ChatScopeCase }) {
  const scrollAreaRef = useRef<HTMLDivElement | null>(null);
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const shouldScrollToBottomRef = useRef(true);
  const [items, setItems] = useState<MessageResponseDto[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(true);
  const [sending, setSending] = useState(false);
  const [askingBot, setAskingBot] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [me, setMe] = useState<ReturnType<typeof getStoredUser>>();

  useEffect(() => {
    setMe(getStoredUser());
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    setHasMore(false);
    setNextCursor(null);
    shouldScrollToBottomRef.current = true;
    try {
      const page = await messagesApi.list(caseFile.id);
      setItems(page.items);
      setHasMore(page.hasMore);
      setNextCursor(page.nextCursor);
    } catch (e) {
      setError(
        e instanceof ApiRequestError
          ? e.details?.message ?? e.message
          : "تعذر تحميل المحادثة",
      );
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [caseFile.id]);

  const loadOlderMessages = useCallback(async () => {
    if (!nextCursor || loadingOlder || loading) return;
    const scrollEl = scrollAreaRef.current;
    const prevScrollHeight = scrollEl?.scrollHeight ?? 0;
    const prevScrollTop = scrollEl?.scrollTop ?? 0;
    setLoadingOlder(true);
    try {
      const page = await messagesApi.list(caseFile.id, { before: nextCursor });
      setHasMore(page.hasMore);
      setNextCursor(page.nextCursor);
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
  }, [caseFile.id, loading, loadingOlder, nextCursor]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (loading || loadingOlder) return;
    if (!shouldScrollToBottomRef.current) return;
    bottomRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
    shouldScrollToBottomRef.current = false;
  }, [items.length, loading, loadingOlder]);

  // Append helper that dedupes by id, used by both REST send response and any
  // future realtime push.
  const appendUnique = useCallback((m: MessageResponseDto) => {
    shouldScrollToBottomRef.current = true;
    setItems((prev) => (prev.some((x) => x.id === m.id) ? prev : [...prev, m]));
  }, []);

  async function send() {
    const text = content.trim();
    if (!text) {
      notifyError("اكتب الرسالة أولا");
      return;
    }
    setSending(true);
    try {
      const created = await messagesApi.send(caseFile.id, text);
      appendUnique(created);
      setContent("");
    } catch (e) {
      notifyApiError(e, "تعذر إرسال الرسالة");
    } finally {
      setSending(false);
    }
  }

  async function askBot() {
    const text = content.trim();
    if (!text) {
      notifyError("اكتب سؤالك أولا");
      return;
    }
    setAskingBot(true);
    try {
      // The backend persists the answer as a BOT-typed Message. Re-load the
      // thread to pick it up — keeps this drawer simple (no separate WS
      // subscription needed) while avoiding any local-vs-server duplication.
      await chatbotApi.ask(caseFile.id, { question: text });
      setContent("");
      await load();
    } catch (e) {
      notifyApiError(e, "تعذر الحصول على إجابة من المساعد");
    } finally {
      setAskingBot(false);
    }
  }

  return (
    <div className="flex h-full flex-col">
      {/* Thread */}
      <div
        ref={scrollAreaRef}
        className={cn(
          "flex-1 min-h-0 overflow-y-auto",
          "bg-gradient-to-b from-[var(--color-surface-muted)]/40 to-[var(--color-surface)]",
          "p-4",
        )}
      >
        {error ? (
          <CenteredState
            icon={MessageSquare}
            title="تعذر تحميل المحادثة"
            desc={error}
            actionLabel="إعادة المحاولة"
            onAction={() => void load()}
          />
        ) : loading ? (
          <CenteredState icon={Loader2} spin title="جاري تحميل المحادثة..." />
        ) : items.length === 0 ? (
          <CenteredState
            icon={MessageSquare}
            title="لا توجد رسائل بعد"
            desc="ابدأ المحادثة أو اطرح سؤالاً على المساعد الذكي."
          />
        ) : (
          <>
            {hasMore ? (
              <div className="mb-3 flex justify-center">
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
            <ChatThread items={items} meId={me?.id ?? null} />
          </>
        )}
        <div ref={bottomRef} />
      </div>

      {/* Composer */}
      <div className="shrink-0 space-y-2 border-t border-[var(--color-border)] bg-[var(--color-surface)] p-3">
        <textarea
          rows={2}
          value={content}
          disabled={sending || askingBot}
          placeholder="اكتب رسالتك أو سؤالك..."
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
              e.preventDefault();
              void send();
            }
          }}
          className={cn(
            "block w-full resize-none rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-3 text-sm leading-6",
            "placeholder:text-[var(--color-muted-foreground)]",
            "focus:outline-none focus:border-[var(--color-gold-400)] focus:shadow-[0_0_0_3px_rgba(212,175,55,0.15)]",
            "disabled:cursor-not-allowed disabled:opacity-70",
          )}
        />
        <div className="flex flex-wrap items-center justify-end gap-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={sending || askingBot || !content.trim()}
            onClick={() => void askBot()}
            className="gap-2"
          >
            {askingBot ? (
              <Loader2 className="size-4 animate-spin" />
            ) : (
              <Sparkles className="size-4 text-[var(--color-gold-500)]" />
            )}
            اسأل المساعد
          </Button>
          <Button
            type="button"
            variant="gold"
            size="sm"
            disabled={sending || askingBot || !content.trim()}
            onClick={() => void send()}
            className="gap-2"
          >
            {sending ? (
              <>
                <Loader2 className="size-4 animate-spin" />
                جاري الإرسال...
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
  );
}

/* ─── chat thread renderer ─────────────────────────────────────── */

function ChatThread({
  items,
  meId,
}: {
  items: MessageResponseDto[];
  meId: string | null;
}) {
  return (
    <div className="space-y-3">
      {items.map((m) => {
        if (m.senderType === "BOT") {
          return <BotBubble key={m.id} message={m} />;
        }
        const mine = Boolean(meId && m.sender?.id === meId);
        return <MsgBubble key={m.id} message={m} mine={mine} />;
      })}
    </div>
  );
}

function MsgBubble({
  message,
  mine,
}: {
  message: MessageResponseDto;
  mine: boolean;
}) {
  return (
    <div className={cn("flex", mine ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "max-w-[80%] rounded-2xl px-3.5 py-2 text-sm leading-6 shadow-[var(--shadow-soft)]",
          mine
            ? "rounded-ee-sm bg-[var(--color-navy-800)] text-white"
            : "rounded-es-sm border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-foreground)]",
        )}
      >
        <p className="whitespace-pre-wrap break-words">{message.content}</p>
        <p
          className={cn(
            "mt-1 text-[10px] ltr-nums",
            mine ? "text-white/60" : "text-[var(--color-muted-foreground)]",
          )}
          title={formatDateTime(message.createdAt)}
        >
          {formatTime(message.createdAt)}
        </p>
      </div>
    </div>
  );
}

function BotBubble({ message }: { message: MessageResponseDto }) {
  return (
    <div className="flex justify-start">
      <div className="flex max-w-[88%] gap-2">
        <div
          className={cn(
            "flex size-8 shrink-0 items-center justify-center rounded-full",
            "bg-gradient-to-br from-[var(--color-gold-300)] to-[var(--color-gold-500)] text-[var(--color-navy-900)]",
          )}
          aria-label="المساعد الذكي"
        >
          <Sparkles className="size-4" />
        </div>
        <div
          className={cn(
            "rounded-2xl rounded-es-sm px-3.5 py-2 text-sm leading-6",
            "border border-[var(--color-gold-200)] bg-[var(--color-gold-50)] text-[var(--color-foreground)]",
            "shadow-[var(--shadow-soft)]",
          )}
        >
          <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-[var(--color-gold-600)]">
            🤖 المساعد
          </p>
          <p className="whitespace-pre-wrap break-words">{message.content}</p>
          <p
            className="mt-1 text-[10px] text-[var(--color-muted-foreground)] ltr-nums"
            title={formatDateTime(message.createdAt)}
          >
            {formatTime(message.createdAt)}
          </p>
        </div>
      </div>
    </div>
  );
}

/* ─── shared empty/loading state ───────────────────────────────── */

function CenteredState({
  icon: Icon,
  spin,
  title,
  desc,
  actionLabel,
  onAction,
}: {
  icon: React.ComponentType<{ className?: string }>;
  spin?: boolean;
  title: string;
  desc?: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <div className="flex h-full min-h-[14rem] flex-col items-center justify-center px-6 py-10 text-center">
      <div className="mb-3 flex size-12 items-center justify-center rounded-2xl bg-[var(--color-navy-50)] text-[var(--color-navy-700)]">
        <Icon className={cn("size-6", spin && "animate-spin")} />
      </div>
      <p className="text-sm font-semibold text-[var(--color-foreground)]">
        {title}
      </p>
      {desc ? (
        <p className="mt-1 max-w-sm text-xs text-[var(--color-muted-foreground)]">
          {desc}
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
