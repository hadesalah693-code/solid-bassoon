"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ArrowRight,
  Hash,
  MessageCircle,
  Search,
} from "lucide-react";

import { CaseMessagesPanel } from "@/components/cases/CaseMessagesPanel";
import { ApiErrorAlert } from "@/components/shared/ApiErrorAlert";
import { ApiRequestError } from "@/lib/api/client";
import { messagesApi } from "@/lib/api/messages";
import { setUnreadMessages } from "@/lib/hooks/use-unread-messages";
import { cn } from "@/lib/utils/cn";
import { formatRelative, formatTime } from "@/lib/utils/format-date";
import type { MessageInboxItemDto } from "@/types/api";

/* ─── helpers ──────────────────────────────────────────────────── */

function clientName(item: MessageInboxItemDto) {
  return item.client.fullName ?? "عميل";
}

function lastMessagePreview(item: MessageInboxItemDto): string {
  const msg = item.lastMessage;
  if (!msg) return "ابدأ المحادثة...";
  const prefix =
    msg.senderType === "BOT"
      ? "🤖"
      : msg.sender?.fullName?.split(" ")[0] ?? "مستخدم";
  const content =
    msg.content.length > 50 ? msg.content.slice(0, 50) + "…" : msg.content;
  return `${prefix}: ${content}`;
}

function lastMessageTime(item: MessageInboxItemDto): string | null {
  if (!item.lastMessage) return null;
  const today = new Date().toISOString().slice(0, 10);
  const msgDay = item.lastMessage.createdAt.slice(0, 10);
  return today === msgDay
    ? formatTime(item.lastMessage.createdAt)
    : formatRelative(item.lastMessage.createdAt);
}

/** Two-letter initials from an Arabic/Latin name */
function initials(name: string | null): string {
  const src = name?.trim() || "؟";
  return src
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0])
    .join("")
    .toUpperCase();
}

/** Stable pastel color from the name string */
const AVATAR_PALETTES = [
  { bg: "bg-[var(--color-navy-700)]", text: "text-[var(--color-gold-300)]" },
  { bg: "bg-emerald-700",             text: "text-emerald-100" },
  { bg: "bg-violet-700",              text: "text-violet-100" },
  { bg: "bg-rose-700",                text: "text-rose-100" },
  { bg: "bg-sky-700",                 text: "text-sky-100" },
  { bg: "bg-amber-700",               text: "text-amber-100" },
];
function avatarPalette(name: string | null) {
  const code = [...(name ?? "")].reduce((a, c) => a + c.charCodeAt(0), 0);
  return AVATAR_PALETTES[code % AVATAR_PALETTES.length];
}

/* ─── Skeleton ─────────────────────────────────────────────────── */

function ConversationSkeleton() {
  return (
    <div className="flex animate-pulse items-center gap-3 px-4 py-3">
      <div className="size-11 shrink-0 rounded-full bg-white/10" />
      <div className="flex-1 space-y-2">
        <div className="flex justify-between">
          <div className="h-3.5 w-28 rounded-full bg-white/10" />
          <div className="h-3 w-10 rounded-full bg-white/10" />
        </div>
        <div className="h-3 w-44 rounded-full bg-white/10" />
      </div>
    </div>
  );
}

/* ─── Page ─────────────────────────────────────────────────────── */

export default function MessagesInboxPage() {
  const [items, setItems] = useState<MessageInboxItemDto[]>([]);
  const [selectedCaseId, setSelectedCaseId] = useState<string | null>(null);
  const [mobileView, setMobileView] = useState<"list" | "chat">("list");
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const searchRef = useRef<HTMLInputElement>(null);

  const refreshConversations = useCallback(() => setReloadKey((n) => n + 1), []);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await messagesApi.inbox();
        if (cancelled) return;
        setItems(data);
        setUnreadMessages(data.reduce((s, it) => s + it.unreadCount, 0));
      } catch (e) {
        if (cancelled) return;
        setError(
          e instanceof ApiRequestError
            ? (e.details?.message ?? e.message)
            : "تعذر تحميل المحادثات",
        );
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [reloadKey]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (it) =>
        clientName(it).toLowerCase().includes(q) ||
        it.caseTitle.toLowerCase().includes(q) ||
        it.caseNumber.toLowerCase().includes(q) ||
        (it.lastMessage?.content ?? "").toLowerCase().includes(q),
    );
  }, [items, search]);

  const selected = items.find((it) => it.caseId === selectedCaseId) ?? null;
  const totalUnread = useMemo(
    () => items.reduce((s, it) => s + it.unreadCount, 0),
    [items],
  );

  function selectConversation(caseId: string) {
    setSelectedCaseId(caseId);
    setMobileView("chat");
  }

  return (
    <div className="mx-auto flex w-full max-w-[88rem] flex-col gap-4">
      {error && (
        <ApiErrorAlert
          message={error}
          title="تعذر تحميل المحادثات"
          onRetry={refreshConversations}
        />
      )}

      {/* ── Unified panel card ────────────────────────────────────── */}
      <div
        className={cn(
          "flex overflow-hidden rounded-2xl border border-[var(--color-border)]",
          "shadow-[var(--shadow-pop)]",
          "h-[calc(100vh-10rem)] min-h-[36rem]",
        )}
      >
        {/* ════ SIDEBAR ════════════════════════════════════════════ */}
        <aside
          className={cn(
            "flex w-full flex-col border-e border-[var(--color-border)] bg-[var(--color-navy-900)]",
            "lg:flex lg:w-[22rem] lg:shrink-0",
            mobileView === "chat" ? "hidden" : "flex",
          )}
        >
          {/* Sidebar top-bar */}
          <div className="flex items-center justify-between gap-3 px-5 py-4">
            <div>
              <h1 className="text-base font-bold text-white">
                المحادثات
                {!loading && totalUnread > 0 && (
                  <span className="ms-2 inline-flex min-w-[1.25rem] items-center justify-center rounded-full bg-red-500 px-1.5 py-px text-[11px] font-bold leading-none text-white">
                    {totalUnread > 99 ? "99+" : totalUnread}
                  </span>
                )}
              </h1>
              <p className="mt-0.5 text-xs text-[var(--color-navy-300)]">
                {loading ? "جاري التحميل..." : `${filtered.length} محادثة`}
              </p>
            </div>
            <div className="flex size-9 items-center justify-center rounded-xl bg-white/10 text-[var(--color-gold-300)]">
              <MessageCircle className="size-5" />
            </div>
          </div>

          {/* Search */}
          <div className="px-4 pb-3">
            <div className="relative">
              <Search className="pointer-events-none absolute end-3 top-1/2 size-4 -translate-y-1/2 text-[var(--color-navy-400)]" />
              <input
                ref={searchRef}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="بحث..."
                className={cn(
                  "w-full rounded-xl border border-white/10 bg-white/8 py-2.5 pe-9 ps-3.5 text-sm text-white",
                  "placeholder:text-[var(--color-navy-400)]",
                  "focus:border-[var(--color-gold-400)] focus:bg-white/12 focus:outline-none",
                  "transition-colors",
                )}
              />
            </div>
          </div>

          {/* Conversation list */}
          <div className="min-h-0 flex-1 overflow-y-auto">
            {loading ? (
              <div className="flex flex-col divide-y divide-white/5">
                {Array.from({ length: 6 }).map((_, i) => (
                  <ConversationSkeleton key={i} />
                ))}
              </div>
            ) : filtered.length === 0 ? (
              <div className="flex flex-col items-center justify-center px-6 py-16 text-center">
                <div className="mb-3 flex size-14 items-center justify-center rounded-2xl bg-white/10 text-[var(--color-navy-400)]">
                  <MessageCircle className="size-7" />
                </div>
                <p className="text-sm font-medium text-[var(--color-navy-300)]">
                  {search.trim() ? "لا توجد نتائج" : "لا توجد محادثات"}
                </p>
                <p className="mt-1 text-xs text-[var(--color-navy-400)]">
                  ستظهر هنا القضايا المتاحة للمحادثة
                </p>
              </div>
            ) : (
              <ul className="divide-y divide-white/5">
                {filtered.map((item) => {
                  const active = selectedCaseId === item.caseId;
                  const hasUnread = item.unreadCount > 0;
                  const pal = avatarPalette(clientName(item));
                  const timeLabel = lastMessageTime(item);
                  return (
                    <li key={item.caseId}>
                      <button
                        type="button"
                        onClick={() => selectConversation(item.caseId)}
                        className={cn(
                          "group flex w-full items-center gap-3 px-4 py-3 text-start transition-colors",
                          active
                            ? "bg-[var(--color-gold-400)]/15 border-e-2 border-[var(--color-gold-400)]"
                            : "hover:bg-white/6",
                        )}
                      >
                        {/* Avatar */}
                        <div className="relative shrink-0">
                          <div
                            className={cn(
                              "flex size-11 items-center justify-center rounded-full text-sm font-bold",
                              pal.bg,
                              pal.text,
                            )}
                          >
                            {initials(clientName(item))}
                          </div>
                          {hasUnread && (
                            <span className="absolute -end-0.5 -top-0.5 flex min-w-[1rem] items-center justify-center rounded-full bg-red-500 px-1 py-px text-[9px] font-bold leading-none text-white ring-2 ring-[var(--color-navy-900)]">
                              {item.unreadCount > 99 ? "99+" : item.unreadCount}
                            </span>
                          )}
                        </div>

                        {/* Text */}
                        <div className="min-w-0 flex-1">
                          <div className="flex items-baseline justify-between gap-2">
                            <p
                              className={cn(
                                "truncate text-sm",
                                hasUnread
                                  ? "font-bold text-white"
                                  : "font-medium text-[var(--color-navy-100)]",
                              )}
                            >
                              {clientName(item)}
                            </p>
                            {timeLabel && (
                              <span
                                className={cn(
                                  "shrink-0 text-[10px] ltr-nums",
                                  hasUnread
                                    ? "text-[var(--color-gold-400)]"
                                    : "text-[var(--color-navy-400)]",
                                )}
                              >
                                {timeLabel}
                              </span>
                            )}
                          </div>
                          <p className="mt-0.5 flex items-center gap-1 truncate text-xs text-[var(--color-navy-400)] ltr-nums">
                            <Hash className="size-3 shrink-0" />
                            {item.caseNumber}
                          </p>
                          <p
                            className={cn(
                              "mt-0.5 truncate text-xs leading-5",
                              hasUnread
                                ? "font-medium text-[var(--color-navy-200)]"
                                : "text-[var(--color-navy-400)]",
                            )}
                          >
                            {lastMessagePreview(item)}
                          </p>
                        </div>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </aside>

        {/* ════ CHAT PANEL ═════════════════════════════════════════ */}
        <main
          className={cn(
            "flex min-w-0 flex-1 flex-col bg-white",
            "lg:flex",
            mobileView === "list" ? "hidden" : "flex",
          )}
        >
          {selected ? (
            <>
              {/* Chat header */}
              <div
                className={cn(
                  "flex shrink-0 items-center gap-3 border-b border-[var(--color-border)] px-4 py-3",
                  "bg-gradient-to-e from-[var(--color-navy-50)] to-white",
                )}
              >
                {/* Mobile back */}
                <button
                  type="button"
                  onClick={() => setMobileView("list")}
                  aria-label="رجوع"
                  className="flex size-9 shrink-0 items-center justify-center rounded-xl border border-[var(--color-border)] bg-white text-[var(--color-muted-foreground)] hover:bg-[var(--color-surface-muted)] transition-colors lg:hidden"
                >
                  <ArrowRight className="size-4" />
                </button>

                {/* Avatar */}
                {(() => {
                  const pal = avatarPalette(clientName(selected));
                  return (
                    <div
                      className={cn(
                        "flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-bold",
                        pal.bg,
                        pal.text,
                      )}
                    >
                      {initials(clientName(selected))}
                    </div>
                  );
                })()}

                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold text-[var(--color-foreground)]">
                    {clientName(selected)}
                  </p>
                  <p className="flex items-center gap-1 truncate text-xs text-[var(--color-muted-foreground)] ltr-nums">
                    <Hash className="size-3" />
                    {selected.caseNumber}
                    <span className="mx-1 text-[var(--color-border-strong)]">·</span>
                    <span className="truncate">{selected.caseTitle}</span>
                  </p>
                </div>

                {selected.assignedLawyer && (
                  <span className="hidden shrink-0 items-center gap-1.5 rounded-full border border-[var(--color-navy-200)] bg-[var(--color-navy-50)] px-2.5 py-1 text-xs font-medium text-[var(--color-navy-700)] sm:inline-flex">
                    {selected.assignedLawyer.fullName ?? "محامي المكتب"}
                  </span>
                )}
              </div>

              {/* Messages — fills remaining height after the header */}
              <div className="flex min-h-0 flex-1 flex-col p-3 sm:p-4">
                <CaseMessagesPanel
                  key={selected.caseId}
                  caseId={selected.caseId}
                  embedded
                  fullHeight
                  onMessagesChanged={refreshConversations}
                />
              </div>
            </>
          ) : (
            /* Empty state */
            <div className="flex h-full flex-col items-center justify-center gap-4 px-8 text-center">
              <div
                className={cn(
                  "flex size-20 items-center justify-center rounded-3xl",
                  "bg-gradient-to-br from-[var(--color-navy-50)] to-[var(--color-gold-50)]",
                  "border border-[var(--color-border)] shadow-[var(--shadow-soft)]",
                )}
              >
                <MessageCircle className="size-9 text-[var(--color-navy-400)]" />
              </div>
              <div>
                <p className="text-base font-bold text-[var(--color-foreground)]">
                  اختر محادثة
                </p>
                <p className="mt-1 max-w-xs text-sm text-[var(--color-muted-foreground)]">
                  اختر قضية من القائمة على اليسار لفتح المحادثة الخاصة بها
                </p>
              </div>
              <div className="h-px w-16 rounded-full bg-[var(--color-border)]" />
              <p className="text-xs text-[var(--color-muted-foreground)]">
                {loading
                  ? "جاري تحميل المحادثات..."
                  : `${items.length} محادثة متاحة`}
              </p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
