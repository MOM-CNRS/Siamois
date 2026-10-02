import type { FieldRules } from "../rules/types";
import { t, type MessageKey } from "../i18n";

// The fiche layout of every entity type: the JSON FormUiDtoLayoutJson.serialize produces for
// FormResource.layoutJson — panels/rows/columns whose fieldId points into the sibling `fields`
// catalog.

// Mirrors the server's own ColumnWidth (fr.siamois.ui.form.dto.ColumnWidth) exactly: mobile-first,
// `span` is the width below `md`, `md`/`lg` override it from that breakpoint up when present. The
// server converts the SAME object to PrimeFaces' `ui-g-N ui-md-N ui-lg-N` for JSF
// (CustomColUiDto#getClassName()); toGridClass below is this side's conversion — neither
// side invents its own numbers, both read this wire shape. See that function's own doc for why
// the md/lg breakpoints are container queries here, not viewport media queries.
export interface ColumnWidth {
  span: number;
  md?: number;
  lg?: number;
}

export interface FormLayoutCol {
  width: ColumnWidth;
  hidden?: boolean;
  // Raw PrimeFaces class string — only sent for a column still built server-side with the old
  // CustomColUiDto.Builder#className(...) instead of .width(...). parseLayout converts it to
  // `width`/`hidden` (see normalizeCol), so renderers never need to read it.
  className?: string | null;
  isRequired: boolean;
  isReadOnly: boolean;
  fieldId?: number | string | null;
  // Conditional rules (enabledWhen / requiredWhen / options / constraints) — see rules/types.ts.
  rules?: FieldRules | null;
}

/**
 * Grid classes for a column's width — see ColumnWidth's own doc above. All of them are our own
 * `sia-` classes (main-panel.css), not PrimeFlex's: PrimeFlex's global `.grid`/`.col-N` utilities
 * collided with Bootstrap's on the host page, so the bundle no longer ships it. The base span is
 * `sia-col-N` (percentage width, no breakpoint — always applies). The md/lg overrides are not
 * viewport breakpoints (PrimeFlex's `md:col-N` style): those are `@media`-gated, i.e. keyed to
 * the BROWSER WINDOW's width — fine for the main panel (which is the window, roughly), wrong for the fiche opened in the narrow overview pane, where the window
 * can stay wide (say 1400px, well past the lg breakpoint) while the pane itself is 300px. That
 * combination left the overview fiche stuck at 4 narrow columns no matter how narrow the pane
 * got — found live, resizing the overview's own splitter, not the window.
 *
 * <p>`sia-md-col-N`/`sia-lg-col-N` are matched by `@container`
 * rules in main-panel.css scoped to the fiche's own root (`.sia-fiche-tab { container-type:
 * inline-size }`) — breakpoints sized for the fiche's own width (400/720px, see there), same percentage math,
 * but measured against the fiche's own rendered width, which shrinks with the pane it's actually
 * in, main panel or overview alike.</p>
 *
 * <p>`width` is typed as required (every column ActionUnitDetailsForm/RecordingUnitDetailsForm
 * build carries one), but this is also a network-boundary value: a server-side column built
 * without going through {@code .width(...)} — EffectiveFormResolver's own "additional fields"
 * column was exactly that gap, found the hard way as a crash on `undefined.span` — would otherwise
 * take the whole fiche down with it. Falls back to a full-width column rather than throwing, same
 * as the server's own CustomColUiDto#getClassName() degrades gracefully when width is unset.</p>
 */
export function toGridClass(width: ColumnWidth | null | undefined): string {
  if (!width) return "sia-col-12";
  const classes = [`sia-col-${width.span}`];
  if (width.md != null) classes.push(`sia-md-col-${width.md}`);
  if (width.lg != null) classes.push(`sia-lg-col-${width.lg}`);
  return classes.join(" ");
}

export interface FormLayoutRow {
  columns: FormLayoutCol[];
}

export interface FormLayoutPanel {
  className?: string | null;
  name: string;
  canUserAddFields?: boolean | null;
  isSystemPanel?: boolean | null;
  rows: FormLayoutRow[];
}

export function parseLayout(layoutJson: string): FormLayoutPanel[] {
  if (!layoutJson) return [];
  const panels = JSON.parse(layoutJson) as FormLayoutPanel[];
  return panels.map((panel) => ({
    ...panel,
    rows: (panel.rows ?? []).map((row) => ({ ...row, columns: (row.columns ?? []).map(normalizeCol) })),
  }));
}

/**
 * A column built server-side with the legacy `.className("ui-g-12 ui-md-6 ui-lg-3")` arrives with
 * no `width` (FormUiDtoLayoutJson#serializeCol only emits `width` for `.width(...)` columns) —
 * without this, toGridClass would fall back to full width and every field would stack. Parses the
 * PrimeFaces classes back into a ColumnWidth, and `d-none` into `hidden`. A structured `width`
 * always wins.
 */
function normalizeCol(col: FormLayoutCol): FormLayoutCol {
  if (col.width || !col.className) return col;
  const classes = col.className.split(/\s+/);
  const read = (prefix: string) => {
    const match = classes.find((c) => c.startsWith(prefix));
    const n = match ? Number(match.slice(prefix.length)) : NaN;
    return Number.isInteger(n) && n >= 1 && n <= 12 ? n : undefined;
  };
  const span = read("ui-g-");
  const md = read("ui-md-");
  const lg = read("ui-lg-");
  const width: ColumnWidth | undefined =
    span != null || md != null || lg != null
      ? { span: span ?? 12, ...(md != null && { md }), ...(lg != null && { lg }) }
      : undefined;
  return {
    ...col,
    width: width as ColumnWidth,
    hidden: col.hidden || classes.includes("d-none"),
  };
}

// Panel `name` is an i18n message code (JSF resolves it against the message bundle), not a
// ready-to-display label. One map for every entity type — the codes don't collide. Falls back to
// the raw code for a panel it doesn't know (a group created in the form builder).
const PANEL_LABELS: Record<string, MessageKey> = {
  "common.header.general": "panel.general",
  "common.label.localisation": "panel.localisation",
  "common.header.chronology": "panel.chronology",
  "common.header.chronologie": "panel.chronology",
  "common.header.measurement": "panel.measurements",
  "common.header.dimensions": "panel.dimensions",
  "actionunit.header.administrative": "panel.administrative",
  "actionunit.header.documentation": "panel.documentation",
  "recordingunit.panel.chronology": "panel.chronology",
  "recordingunit.panel.measurements": "panel.measurements",
  "document.header.identification": "panel.document.identification",
  "document.header.description": "panel.document.description",
  "document.header.authorsRights": "panel.document.authorsRights",
  "document.header.file": "panel.document.file",
  "document.header.links": "panel.document.links",
  "document.header.technical": "panel.document.technical",
};

export function panelLabel(nameCode: string): string {
  const key = PANEL_LABELS[nameCode];
  return key ? t(key) : nameCode;
}
