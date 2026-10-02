"use client";

import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Award,
  Bold,
  FileMinus,
  Italic,
  PenLine,
  Table,
  Type,
  Underline,
} from "lucide-react";

import { cn } from "@/lib/utils/cn";

export type EditorAction =
  | { kind: "insert"; text: string }
  | { kind: "wrap"; before: string; after: string; placeholder: string };

type ToolItem = {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  action: EditorAction;
};

const TABLE_SNIPPET =
  "\n| العمود 1 | العمود 2 | العمود 3 |\n|----------|----------|----------|\n| بيانات   | بيانات   | بيانات   |\n| بيانات   | بيانات   | بيانات   |\n";

const HEADER_SNIPPET =
  "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n{{company.name}}\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n";

const FOOTER_SNIPPET =
  "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\nجميع الحقوق محفوظة · {{company.name}} · {{today}}\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n";

const SIGNATURE_SNIPPET =
  "\n\nالمختص بالتوقيع: _______________________\nالاسم:           _______________________\nالتاريخ:         _______________________\n";

const STAMP_SNIPPET = "\n[ختم المكتب الرسمي]\n_______________________\n";

const TOOLS: (ToolItem | "divider")[] = [
  {
    icon: Bold,
    title: "غامق",
    action: { kind: "wrap", before: "**", after: "**", placeholder: "نص غامق" },
  },
  {
    icon: Italic,
    title: "مائل",
    action: { kind: "wrap", before: "_", after: "_", placeholder: "نص مائل" },
  },
  {
    icon: Underline,
    title: "تسطير",
    action: { kind: "wrap", before: "__", after: "__", placeholder: "نص مسطّر" },
  },
  "divider",
  {
    icon: AlignRight,
    title: "محاذاة يمين",
    action: { kind: "insert", text: "[right]\n" },
  },
  {
    icon: AlignCenter,
    title: "محاذاة وسط",
    action: { kind: "insert", text: "[center]\n" },
  },
  {
    icon: AlignLeft,
    title: "محاذاة يسار",
    action: { kind: "insert", text: "[left]\n" },
  },
  "divider",
  {
    icon: Table,
    title: "إدراج جدول",
    action: { kind: "insert", text: TABLE_SNIPPET },
  },
  {
    icon: Type,
    title: "إدراج رأس المستند",
    action: { kind: "insert", text: HEADER_SNIPPET },
  },
  {
    icon: FileMinus,
    title: "إدراج تذييل المستند",
    action: { kind: "insert", text: FOOTER_SNIPPET },
  },
  {
    icon: PenLine,
    title: "إدراج كتلة توقيع",
    action: { kind: "insert", text: SIGNATURE_SNIPPET },
  },
  {
    icon: Award,
    title: "إدراج ختم",
    action: { kind: "insert", text: STAMP_SNIPPET },
  },
];

type Props = {
  onAction: (action: EditorAction) => void;
  disabled?: boolean;
};

export function EditorToolbar({ onAction, disabled }: Props) {
  return (
    <div
      dir="ltr"
      className="flex flex-wrap items-center gap-px rounded-t-xl border border-b-0 border-[var(--color-border)] bg-[var(--color-surface-muted)]/60 px-2 py-1.5"
    >
      {TOOLS.map((item, idx) =>
        item === "divider" ? (
          <div
            key={`div-${idx}`}
            className="mx-1.5 h-5 w-px bg-[var(--color-border)]"
          />
        ) : (
          <button
            key={`btn-${idx}`}
            type="button"
            title={item.title}
            disabled={disabled}
            onClick={() => onAction(item.action)}
            className={cn(
              "flex h-7 w-7 items-center justify-center rounded-md text-[var(--color-muted-foreground)] transition-colors",
              "hover:bg-[var(--color-border)] hover:text-[var(--color-foreground)]",
              "disabled:pointer-events-none disabled:opacity-40",
            )}
          >
            <item.icon className="size-3.5" />
            <span className="sr-only">{item.title}</span>
          </button>
        ),
      )}
    </div>
  );
}
