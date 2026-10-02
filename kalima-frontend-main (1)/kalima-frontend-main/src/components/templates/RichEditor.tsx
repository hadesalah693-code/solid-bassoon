"use client";

/**
 * RichEditor — contentEditable-based rich text editor for legal templates.
 *
 * Uses browser-native document.execCommand (deprecated but universally
 * supported in all current browsers) for formatting, avoiding new npm deps.
 *
 * Exposes insertAtCursor() via forwardRef so parent components (e.g. variable
 * chip buttons) can inject content even after the editor loses focus.
 */

import {
  forwardRef,
  useEffect,
  useImperativeHandle,
  useRef,
  useState,
} from "react";
import {
  AlignCenter,
  AlignLeft,
  AlignRight,
  Bold,
  Image,
  Italic,
  List,
  ListOrdered,
  Palette,
  Table2,
  Underline,
  X,
} from "lucide-react";

import { cn } from "@/lib/utils/cn";
import {
  isHtmlContent,
  plainTextToHtml,
  sanitizeHtml,
} from "@/lib/utils/sanitize-html";

// ─── Toolbar data ────────────────────────────────────────────────────────────

const FONT_FAMILIES = [
  { label: "Tajawal (عربي)", value: "Tajawal, sans-serif" },
  { label: "Arial", value: "Arial, sans-serif" },
  { label: "Georgia", value: "Georgia, serif" },
  { label: "Times New Roman", value: "'Times New Roman', serif" },
  { label: "Courier New", value: "'Courier New', monospace" },
];

const FONT_SIZES: { label: string; value: string }[] = [
  { label: "10px", value: "1" },
  { label: "13px", value: "2" },
  { label: "16px", value: "3" },
  { label: "18px", value: "4" },
  { label: "24px", value: "5" },
  { label: "32px", value: "6" },
  { label: "48px", value: "7" },
];

// ─── Types ────────────────────────────────────────────────────────────────────

export type RichEditorHandle = {
  /** Insert arbitrary HTML at the current cursor / saved selection position. */
  insertAtCursor: (html: string) => void;
};

type Props = {
  defaultValue?: string;
  onChange: (html: string) => void;
  disabled?: boolean;
  placeholder?: string;
  className?: string;
};

// ─── Component ────────────────────────────────────────────────────────────────

export const RichEditor = forwardRef<RichEditorHandle, Props>(
  function RichEditor(
    { defaultValue = "", onChange, disabled = false, placeholder, className },
    ref,
  ) {
    const editorRef = useRef<HTMLDivElement>(null);
    const savedRangeRef = useRef<Range | null>(null);
    const [colorValue, setColorValue] = useState("#0b1426");
    const [showImageInput, setShowImageInput] = useState(false);
    const [imageUrl, setImageUrl] = useState("");
    const [imageError, setImageError] = useState("");
    const [isEmpty, setIsEmpty] = useState(!defaultValue);

    // ── Initialise content once on mount ─────────────────────────────────────
    useEffect(() => {
      const el = editorRef.current;
      if (!el) return;
      const val = defaultValue ?? "";
      if (!val) {
        el.innerHTML = "";
        setIsEmpty(true);
        return;
      }
      el.innerHTML = isHtmlContent(val)
        ? sanitizeHtml(val)
        : plainTextToHtml(val);
      setIsEmpty(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []); // mount-only — parent should use key= prop if switching templates

    // ── Selection helpers ─────────────────────────────────────────────────────

    function saveRange() {
      const sel = window.getSelection();
      if (sel && sel.rangeCount > 0) {
        savedRangeRef.current = sel.getRangeAt(0).cloneRange();
      }
    }

    function restoreRange() {
      const sel = window.getSelection();
      if (sel && savedRangeRef.current) {
        sel.removeAllRanges();
        sel.addRange(savedRangeRef.current);
      }
    }

    // ── Core command helpers ──────────────────────────────────────────────────

    /** For toolbar buttons that use onMouseDown+preventDefault (focus stays). */
    function execCmd(command: string, value?: string) {
      editorRef.current?.focus();
      // eslint-disable-next-line @typescript-eslint/no-deprecated
      document.execCommand(command, false, value ?? undefined);
      emit();
    }

    /** For selects/color that cause blur first — restore saved range. */
    function execSaved(command: string, value?: string) {
      editorRef.current?.focus();
      restoreRange();
      // eslint-disable-next-line @typescript-eslint/no-deprecated
      document.execCommand(command, false, value ?? undefined);
      emit();
    }

    function insertHtml(html: string) {
      editorRef.current?.focus();
      restoreRange();
      // eslint-disable-next-line @typescript-eslint/no-deprecated
      document.execCommand("insertHTML", false, html);
      emit();
    }

    function emit() {
      const html = editorRef.current?.innerHTML ?? "";
      const empty =
        html === "" || html === "<br>" || /^(<p>(<br>|&nbsp;|\s)*<\/p>)+$/.test(html);
      setIsEmpty(empty);
      onChange(empty ? "" : html);
    }

    // ── Exposed handle ────────────────────────────────────────────────────────

    useImperativeHandle(ref, () => ({
      insertAtCursor(html: string) {
        insertHtml(html);
      },
    }));

    // ── Input / paste ─────────────────────────────────────────────────────────

    function handleInput() {
      emit();
    }

    function handlePaste(e: React.ClipboardEvent<HTMLDivElement>) {
      const html = e.clipboardData.getData("text/html");
      if (html) {
        e.preventDefault();
        // eslint-disable-next-line @typescript-eslint/no-deprecated
        document.execCommand("insertHTML", false, sanitizeHtml(html));
        emit();
      }
      // Plain-text paste: let browser handle natively
    }

    // ── Insert blocks ─────────────────────────────────────────────────────────

    function insertTable() {
      const cell = `style="border:1px solid #d1d5db;padding:8px 12px;text-align:right"`;
      const th = `style="border:1px solid #d1d5db;padding:8px 12px;text-align:right;font-weight:600;background:#f9f7f2;color:#5a4a1a"`;
      insertHtml(
        `<table style="width:100%;border-collapse:collapse;direction:rtl;margin:12px 0">` +
          `<thead><tr><th ${th}>العنوان 1</th><th ${th}>العنوان 2</th><th ${th}>العنوان 3</th></tr></thead>` +
          `<tbody>` +
          `<tr><td ${cell}>بيانات</td><td ${cell}>بيانات</td><td ${cell}>بيانات</td></tr>` +
          `<tr style="background:#fafafa"><td ${cell}>بيانات</td><td ${cell}>بيانات</td><td ${cell}>بيانات</td></tr>` +
          `<tr><td ${cell}>بيانات</td><td ${cell}>بيانات</td><td ${cell}>بيانات</td></tr>` +
          `</tbody></table><br>`,
      );
    }

    function insertHeader() {
      insertHtml(
        `<div style="border-bottom:2px solid #b8941f;padding-bottom:14px;margin-bottom:18px;direction:rtl">` +
          `<div style="display:flex;justify-content:space-between;align-items:center;gap:12px">` +
          `<div>` +
          `<div style="font-size:20px;font-weight:700;color:#0b1426">{{company.name}}</div>` +
          `<div style="font-size:12px;color:#64748b;margin-top:3px">مكتب محاماة — خدمات قانونية متكاملة</div>` +
          `</div>` +
          `<div style="font-size:12px;color:#64748b;text-align:left">التاريخ: {{today}}</div>` +
          `</div></div><br>`,
      );
    }

    function insertFooter() {
      insertHtml(
        `<br><div style="border-top:2px solid #b8941f;padding-top:14px;margin-top:18px;direction:rtl">` +
          `<div style="display:flex;justify-content:space-between;align-items:flex-end;gap:12px">` +
          `<div style="font-size:12px;color:#64748b"><div>{{company.name}}</div><div>التاريخ: {{today}}</div></div>` +
          `<div style="text-align:center">` +
          `<div style="border-top:1px solid #374151;width:160px;padding-top:4px;font-size:12px;color:#374151">توقيع المحامي المختص</div>` +
          `</div></div></div>`,
      );
    }

    function handleInsertImage() {
      const url = imageUrl.trim();
      setImageError("");
      if (!url) {
        setImageError("أدخل رابط الصورة");
        return;
      }
      if (!/^https?:\/\//i.test(url)) {
        setImageError("الرابط يجب أن يبدأ بـ https:// أو http://");
        return;
      }
      const safeUrl = url.replace(/"/g, "%22");
      insertHtml(
        `<img src="${safeUrl}" alt="صورة" style="max-width:100%;height:auto;display:block;margin:8px auto;border-radius:6px" /><br>`,
      );
      setImageUrl("");
      setShowImageInput(false);
    }

    // ── Toolbar helper — mousedown prevents focus loss ────────────────────────

    function toolBtn(
      command: string,
      value?: string,
    ): React.MouseEventHandler<HTMLButtonElement> {
      return (e) => {
        e.preventDefault();
        execCmd(command, value);
      };
    }

    // ─────────────────────────────────────────────────────────────────────────

    return (
      <div
        className={cn(
          "rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-sm",
          className,
        )}
      >
        {/* ── Toolbar ──────────────────────────────────────────────────────── */}
        <div className="flex flex-wrap items-center gap-x-0.5 gap-y-1 border-b border-[var(--color-border)] bg-[var(--color-surface-muted)]/50 px-2 py-2">

          {/* Text format */}
          <TGroup>
            <TBtn title="غامق (Ctrl+B)" onMouseDown={toolBtn("bold")}>
              <Bold className="size-3.5" />
            </TBtn>
            <TBtn title="مائل (Ctrl+I)" onMouseDown={toolBtn("italic")}>
              <Italic className="size-3.5" />
            </TBtn>
            <TBtn title="تسطير (Ctrl+U)" onMouseDown={toolBtn("underline")}>
              <Underline className="size-3.5" />
            </TBtn>
          </TGroup>

          <TDiv />

          {/* Font family */}
          <select
            className={selectCls}
            title="نوع الخط"
            defaultValue=""
            onMouseDown={saveRange}
            onChange={(e) => execSaved("fontName", e.target.value)}
          >
            <option value="" disabled>
              الخط
            </option>
            {FONT_FAMILIES.map((f) => (
              <option key={f.value} value={f.value}>
                {f.label}
              </option>
            ))}
          </select>

          {/* Font size */}
          <select
            className={cn(selectCls, "w-16")}
            title="حجم الخط"
            defaultValue="3"
            onMouseDown={saveRange}
            onChange={(e) => execSaved("fontSize", e.target.value)}
          >
            {FONT_SIZES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>

          {/* Text color */}
          <label
            className="flex cursor-pointer items-center gap-1 rounded border border-[var(--color-border)] px-1.5 py-0.5 hover:bg-[var(--color-navy-50)]"
            title="لون النص"
            onMouseDown={saveRange}
          >
            <Palette className="size-3.5 text-[var(--color-muted-foreground)]" />
            <input
              type="color"
              className="h-4 w-4 cursor-pointer rounded border-0 bg-transparent p-0"
              value={colorValue}
              onChange={(e) => {
                setColorValue(e.target.value);
                execSaved("foreColor", e.target.value);
              }}
            />
          </label>

          <TDiv />

          {/* Alignment */}
          <TGroup>
            <TBtn title="محاذاة يمين" onMouseDown={toolBtn("justifyRight")}>
              <AlignRight className="size-3.5" />
            </TBtn>
            <TBtn title="توسيط" onMouseDown={toolBtn("justifyCenter")}>
              <AlignCenter className="size-3.5" />
            </TBtn>
            <TBtn title="محاذاة يسار" onMouseDown={toolBtn("justifyLeft")}>
              <AlignLeft className="size-3.5" />
            </TBtn>
          </TGroup>

          <TDiv />

          {/* Lists */}
          <TGroup>
            <TBtn
              title="قائمة نقطية"
              onMouseDown={toolBtn("insertUnorderedList")}
            >
              <List className="size-3.5" />
            </TBtn>
            <TBtn
              title="قائمة مرقّمة"
              onMouseDown={toolBtn("insertOrderedList")}
            >
              <ListOrdered className="size-3.5" />
            </TBtn>
          </TGroup>

          <TDiv />

          {/* Insert */}
          <TGroup>
            <TBtn
              title="إضافة جدول"
              onMouseDown={(e) => {
                e.preventDefault();
                insertTable();
              }}
            >
              <Table2 className="size-3.5" />
            </TBtn>
            <TBtn
              title="إضافة ترويسة"
              onMouseDown={(e) => {
                e.preventDefault();
                insertHeader();
              }}
            >
              <span className="text-[10px] font-bold leading-none">رأس</span>
            </TBtn>
            <TBtn
              title="إضافة تذييل"
              onMouseDown={(e) => {
                e.preventDefault();
                insertFooter();
              }}
            >
              <span className="text-[10px] font-bold leading-none">ذيل</span>
            </TBtn>
            <TBtn
              title="إدراج صورة برابط"
              onMouseDown={(e) => {
                e.preventDefault();
                saveRange();
                setShowImageInput((v) => !v);
                setImageError("");
              }}
            >
              <Image className="size-3.5" />
            </TBtn>
          </TGroup>
        </div>

        {/* ── Image URL input panel ─────────────────────────────────────────── */}
        {showImageInput && (
          <div className="flex flex-wrap items-center gap-2 border-b border-[var(--color-border)] bg-[var(--color-gold-50)] px-3 py-2.5">
            <div className="flex flex-1 items-center gap-2">
              <input
                type="url"
                placeholder="أدخل رابط الصورة (https://…)"
                value={imageUrl}
                onChange={(e) => {
                  setImageUrl(e.target.value);
                  setImageError("");
                }}
                className="h-8 min-w-0 flex-1 rounded-lg border border-[var(--color-border)] bg-white px-2.5 text-xs focus:outline-none focus:ring-1 focus:ring-[var(--color-gold-400)]"
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    e.preventDefault();
                    handleInsertImage();
                  }
                  if (e.key === "Escape") setShowImageInput(false);
                }}
                autoFocus
              />
              <button
                type="button"
                onClick={handleInsertImage}
                className="h-8 rounded-lg bg-[var(--color-gold-500)] px-3 text-xs font-medium text-white hover:bg-[var(--color-gold-600)]"
              >
                إدراج
              </button>
              <button
                type="button"
                onClick={() => setShowImageInput(false)}
                className="flex size-8 items-center justify-center rounded-lg text-[var(--color-muted-foreground)] hover:bg-[var(--color-surface-muted)]"
              >
                <X className="size-3.5" />
              </button>
            </div>
            {imageError ? (
              <p className="w-full text-[10px] text-[var(--color-danger)]">
                {imageError}
              </p>
            ) : (
              <p className="w-full text-[10px] text-[var(--color-muted-foreground)]">
                {/* TODO: backend image-upload endpoint needed for direct file uploads */}
                رفع الملفات مباشرة يتطلب نقطة نهاية في الباك اند — الربط برابط متاح الآن
              </p>
            )}
          </div>
        )}

        {/* ── Editable area ─────────────────────────────────────────────────── */}
        <div className="relative">
          {isEmpty && !disabled && (
            <div
              aria-hidden
              className="pointer-events-none absolute right-6 top-4 select-none text-sm text-[var(--color-muted-foreground)]"
            >
              {placeholder ?? "ابدأ بكتابة محتوى النموذج…"}
            </div>
          )}
          <div
            ref={editorRef}
            contentEditable={!disabled}
            suppressContentEditableWarning
            dir="rtl"
            onInput={handleInput}
            onBlur={saveRange}
            onPaste={handlePaste}
            className={cn(
              "min-h-[22rem] px-6 py-4 text-sm leading-7 text-[var(--color-foreground)] focus:outline-none",
              disabled && "pointer-events-none opacity-60",
            )}
            style={{ fontFamily: "var(--font-tajawal, system-ui, sans-serif)" }}
          />
        </div>
      </div>
    );
  },
);

RichEditor.displayName = "RichEditor";

// ─── Small toolbar sub-components ────────────────────────────────────────────

const selectCls =
  "h-7 rounded border border-[var(--color-border)] bg-[var(--color-surface)] px-1.5 text-xs focus:outline-none focus:ring-1 focus:ring-[var(--color-gold-400)] cursor-pointer";

function TGroup({ children }: { children: React.ReactNode }) {
  return <div className="flex items-center gap-0.5">{children}</div>;
}

function TDiv() {
  return (
    <div className="mx-1 h-5 w-px shrink-0 bg-[var(--color-border)]" />
  );
}

type TBtnProps = React.ButtonHTMLAttributes<HTMLButtonElement> & {
  title: string;
};

function TBtn({ children, className, ...props }: TBtnProps) {
  return (
    <button
      type="button"
      className={cn(
        "flex h-7 min-w-[1.75rem] items-center justify-center rounded px-1 text-[var(--color-foreground)] transition-colors hover:bg-[var(--color-navy-50)] hover:text-[var(--color-navy-700)]",
        className,
      )}
      {...props}
    >
      {children}
    </button>
  );
}
