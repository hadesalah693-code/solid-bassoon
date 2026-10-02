"use client";

import { FileText } from "lucide-react";
import { sanitizeHtml, isHtmlContent } from "@/lib/utils/sanitize-html";

// ─── Demo substitution values ────────────────────────────────────────────────

const DEMO_VALUES: Record<string, string> = {
  "{{company.name}}":     "مكتب كلمة للمحاماة",
  "{{client.name}}":      "هادي علي",
  "{{case.number}}":      "#DEMO-CLIENT-001",
  "{{case.type}}":        "قضية تجارية",
  "{{today}}":            new Date().toLocaleDateString("ar-SA"),
  "{{lawyer.name}}":      "أ. محمد الغامدي",
  "{{court.name}}":       "المحكمة التجارية بالرياض",
  "{{case.start_date}}":  "1446/01/01",
};

// ─── Plain-text → HTML (legacy renderer) ─────────────────────────────────────
// Kept for backward-compatibility: existing templates stored as plain text with
// the old markdown-like syntax ( **bold**, |table|, --- ) continue to work.

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function applyInlineFormatting(html: string): string {
  return html
    .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
    .replace(/__(.+?)__/g, "<u>$1</u>")
    .replace(/_(.+?)_/g, "<em>$1</em>");
}

function renderTableRows(rows: string[]): string {
  const parsed = rows.map((row) =>
    row
      .split("|")
      .map((c) => c.trim())
      .filter((_, i, arr) => i > 0 && i < arr.length - 1),
  );
  const isSeparator = (row: string[]) => row.every((c) => /^[-:]+$/.test(c));
  const headerRow = parsed[0] ?? [];
  const dataRows = parsed.slice(1).filter((r) => !isSeparator(r));

  const cellStyle = `style="padding:6px 10px;border:1px solid #e5e7eb;text-align:right;font-size:0.78rem"`;
  const thStyle   = `style="padding:6px 10px;border:1px solid #e5e7eb;text-align:right;font-size:0.78rem;background:#f9f7f2;color:#5a4a1a;font-weight:600"`;
  const trAltStyle = `style="background:#fafafa"`;

  let html = `<div style="overflow-x:auto;margin:10px 0"><table style="width:100%;border-collapse:collapse">`;
  html += `<thead><tr>`;
  for (const cell of headerRow) {
    html += `<th ${thStyle}>${applyInlineFormatting(escapeHtml(cell))}</th>`;
  }
  html += `</tr></thead><tbody>`;
  dataRows.forEach((row, ri) => {
    html += ri % 2 === 1 ? `<tr ${trAltStyle}>` : `<tr>`;
    for (const cell of row) {
      html += `<td ${cellStyle}>${applyInlineFormatting(escapeHtml(cell))}</td>`;
    }
    html += `</tr>`;
  });
  html += `</tbody></table></div>`;
  return html;
}

function plainContentToHtml(content: string): string {
  const lines = content.split("\n");
  const parts: string[] = [];
  let i = 0;

  while (i < lines.length) {
    const line   = lines[i]!;
    const trimmed = line.trim();

    if (trimmed.startsWith("|")) {
      const tableLines: string[] = [];
      while (i < lines.length && lines[i]!.trim().startsWith("|")) {
        tableLines.push(lines[i]!);
        i++;
      }
      parts.push(renderTableRows(tableLines));
      continue;
    }

    if (/^[━─=]{4,}/.test(trimmed)) {
      parts.push(`<hr style="border:none;border-top:2px solid #e5e7eb;margin:12px 0"/>`);
      i++;
      continue;
    }

    if (trimmed === "[center]")  { parts.push(`<div style="text-align:center">`);  i++; continue; }
    if (trimmed === "[right]")   { parts.push(`<div style="text-align:right">`);   i++; continue; }
    if (trimmed === "[left]")    { parts.push(`<div style="text-align:left">`);    i++; continue; }
    if (/^\[\/(center|right|left)\]$/.test(trimmed)) { parts.push(`</div>`); i++; continue; }

    const formatted = applyInlineFormatting(escapeHtml(line));
    parts.push(
      trimmed === ""
        ? `<div style="height:0.6em"></div>`
        : `<p style="margin:0;padding:2px 0;line-height:1.8">${formatted}</p>`,
    );
    i++;
  }

  return parts.join("");
}

// ─── Placeholder substitution ─────────────────────────────────────────────────

/**
 * Replaces {{token}} in HTML string with highlighted demo values.
 * Works for both plain-text-derived HTML and rich-editor HTML.
 */
function applyDemoPlaceholders(html: string): string {
  let result = html;
  for (const [token, value] of Object.entries(DEMO_VALUES)) {
    // Match the raw token text (may or may not be escaped inside HTML)
    const escaped = token.replace(/\{/g, "&#123;").replace(/\}/g, "&#125;");
    const mark = `<mark style="background:#fef3c7;color:#92400e;padding:0 3px;border-radius:3px;font-style:normal">${value}</mark>`;
    result = result.replaceAll(token, mark).replaceAll(escaped, mark);
  }
  return result;
}

// ─── Component ────────────────────────────────────────────────────────────────

type Props = {
  content: string;
  /**
   * When true, skips demo placeholder substitution and hides "demo" badge.
   * Use for documents that were already rendered by the backend.
   */
  isRendered?: boolean;
};

export function DocumentPreview({ content, isRendered = false }: Props) {
  const isEmpty = !content.trim();

  let html = "";
  if (!isEmpty) {
    if (isHtmlContent(content)) {
      // Rich-editor content — sanitize, then optionally replace placeholders
      const safe = sanitizeHtml(content);
      html = isRendered ? safe : applyDemoPlaceholders(safe);
    } else {
      // Legacy plain-text content — use original parser
      const legacy = plainContentToHtml(content);
      html = isRendered ? legacy : applyDemoPlaceholders(legacy);
    }
  }

  return (
    <div className="sticky top-6 space-y-2">
      {/* Label row */}
      <div className="flex items-center gap-2">
        <span className="text-xs font-medium text-[var(--color-muted-foreground)]">
          {isRendered ? "معاينة المستند" : "معاينة مباشرة"}
        </span>
        {!isRendered ? (
          <span className="rounded-full bg-[var(--color-gold-50)] px-2 py-0.5 text-[10px] font-medium text-[var(--color-gold-600)]">
            القيم تجريبية
          </span>
        ) : null}
      </div>

      {/* Document card */}
      <div
        dir="rtl"
        className="min-h-[580px] overflow-hidden rounded-xl border border-[var(--color-border)] bg-white shadow-[0_2px_12px_rgba(11,20,38,0.07)]"
      >
        {/* Gold top stripe */}
        <div className="h-1.5 bg-gradient-to-l from-[var(--color-gold-400)] to-[var(--color-navy-700)]" />

        {/* Paper content */}
        <div
          className="px-7 py-6 font-sans text-sm leading-7 text-gray-800"
          style={{ fontFamily: "var(--font-tajawal, system-ui, sans-serif)" }}
        >
          {isEmpty ? (
            <div className="flex min-h-[440px] flex-col items-center justify-center gap-3 text-center">
              <FileText className="size-12 text-gray-200" />
              <p className="text-sm text-gray-400">
                ابدأ بكتابة محتوى النموذج لترى المعاينة هنا
              </p>
            </div>
          ) : (
            /* dangerouslySetInnerHTML is used here intentionally.
               All HTML is passed through sanitizeHtml() above which removes
               <script> tags and dangerous event-handler attributes (on*).      */
            <div dangerouslySetInnerHTML={{ __html: html }} />
          )}
        </div>
      </div>

      {/* Legend */}
      {!isRendered ? (
        <p className="text-[10px] text-[var(--color-muted-foreground)]">
          القيم المميّزة بالأصفر هي متغيّرات ستُستبدل تلقائيًا عند توليد
          المستند.
        </p>
      ) : null}
    </div>
  );
}
