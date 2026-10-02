/**
 * Minimal HTML sanitizer — strips <script> tags and dangerous event-handler
 * attributes (on*) before rendering user-supplied HTML with dangerouslySetInnerHTML.
 *
 * This covers the most common XSS vectors. It is NOT a full XSS library.
 * For stronger hardening, add DOMPurify if the bundle policy allows it.
 *
 * No external dependencies — uses the browser's built-in DOMParser.
 */

/** Remove scripts, dangerous event handlers, and javascript: hrefs/srcs. */
export function sanitizeHtml(html: string): string {
  if (!html) return "";

  if (typeof window === "undefined") {
    // SSR fallback — best-effort regex strip (no DOM available)
    return html
      .replace(/<script[\s\S]*?<\/script>/gi, "")
      .replace(/\s+on\w+="[^"]*"/gi, "")
      .replace(/\s+on\w+='[^']*'/gi, "")
      .replace(/javascript:/gi, "");
  }

  const parser = new DOMParser();
  const doc = parser.parseFromString(html, "text/html");

  // Remove script and style elements entirely
  doc.querySelectorAll("script, style").forEach((el) => el.remove());

  // Strip dangerous attributes from every element
  doc.querySelectorAll("*").forEach((el) => {
    Array.from(el.attributes).forEach((attr) => {
      const name = attr.name.toLowerCase();
      const value = attr.value.toLowerCase().replace(/\s/g, "");
      if (
        name.startsWith("on") ||
        (name === "href" && value.startsWith("javascript:")) ||
        (name === "src" && value.startsWith("javascript:")) ||
        name === "formaction"
      ) {
        el.removeAttribute(attr.name);
      }
    });
  });

  return doc.body.innerHTML;
}

/** Returns true when the string contains at least one HTML tag. */
export function isHtmlContent(content: string): boolean {
  return /<[a-z][\s\S]*?>/i.test(content.trim());
}

/**
 * Converts a plain-text string to basic HTML paragraphs for display
 * inside a contentEditable editor.  Blank lines become <p><br></p>.
 */
export function plainTextToHtml(text: string): string {
  if (!text) return "";
  return text
    .split("\n")
    .map((line) => {
      const escaped = line
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
      return escaped.trim() === "" ? "<p><br></p>" : `<p>${escaped}</p>`;
    })
    .join("");
}
