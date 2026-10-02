---
name: ux
description: >-
  Polishes Kalima frontend UI/UX — Arabic-first RTL, navy/gold legal design system,
  dashboard-quality pages. Use when the user asks for better UI, UX, page polish,
  visual design, layout improvements, or invokes /ux.
---

# Kalima UX

## When to apply

User mentions: UI, UX, شكل الصفحة, تجربة المستخدم, polish, redesign, `/ux`, or wants a page to look professional.

## Before editing

1. Read `src/app/globals.css` for design tokens.
2. Read 1–2 reference pages (e.g. `DashboardHero`, `OperationalKpiCard`, existing target page).
3. Reuse shared components — do not invent parallel patterns.

## Design system (non-negotiable)

| Token | Usage |
|-------|--------|
| `--color-navy-*` | Primary brand, headers, emphasis |
| `--color-gold-*` | Accent, focus rings, highlights |
| `--color-surface` / `--color-background` | Cards / page bg |
| `--shadow-soft` / `--shadow-pop` | Default / hover elevation |
| `rounded-2xl` / `rounded-3xl` | Cards / hero strips |
| Cairo via `--font-sans` | All text |
| `ps-*` `pe-*` `ms-*` `me-*` | RTL logical spacing |
| `.ltr-nums` | Numbers, times, counts |

## Page structure template

```
1. Hero strip (optional) — navy gradient, gold accent line, 1-line context
2. KPI row — OperationalKpiCard (max 3–4 metrics)
3. Toolbar — filters + view switcher in one card
4. Main content — SectionCard flush or calendar shell
5. Overlays — sheets/dialogs with framer-motion (existing pattern)
```

## Component reuse

| Need | Use |
|------|-----|
| Page title | `PageHeader` or compact hero |
| Metric tiles | `OperationalKpiCard` |
| Grouped content | `SectionCard` |
| Empty / loading | `EmptyState`, `PageLoading` |
| Errors | `ApiErrorAlert` |
| Buttons | `Button` variants only |
| Motion | `framer-motion` — subtle y/opacity, 0.35s |

## UX rules

- **Hierarchy**: one primary action per screen (e.g. "موعد جديد"); secondary = outline.
- **Density**: comfortable on mobile; use `sm:` / `lg:` breakpoints — never cram desktop-only layouts.
- **Arabic copy**: concise labels; avoid English in user-facing strings unless domain terms (API types stay internal).
- **Feedback**: loading skeletons on KPI values; spin on refresh; toast on save/delete (existing `notifySuccess`).
- **Accessibility**: `aria-label` on icon-only buttons; focus-visible gold ring (global).
- **Scope**: polish the requested page only — no drive-by refactors.

## View switchers

Use pill/segmented control inside a muted track:

```tsx
<div className="inline-flex rounded-xl bg-[var(--color-surface-muted)] p-1 ring-1 ring-[var(--color-border)]/60">
  {/* active: bg-surface shadow-soft; inactive: text-muted hover */}
</div>
```

## FullCalendar

Style via `.calendar-shell` in `globals.css` — match navy buttons, gold today highlight, rounded events. Never import external FC themes.

## Checklist before finishing

- [ ] Matches navy/gold palette (no random hex outside tokens)
- [ ] RTL logical properties throughout
- [ ] Reuses shared components
- [ ] Mobile layout tested mentally (flex-wrap, grid-cols-1)
- [ ] No new dependencies unless user asked
- [ ] `npm run type-check` passes on touched files

## Anti-patterns

- Generic gray Bootstrap look
- LTR-only `ml-*` / `mr-*` / `left-*` / `right-*`
- Heavy animation or parallax
- Replacing working logic while polishing visuals
