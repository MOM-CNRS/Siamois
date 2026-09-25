// Mirrors the JSON shape FormUiDtoLayoutJson.serialize produces for FormResource.layoutJson
// (plan §6/§8 phase 6) — panels/rows/columns referencing fieldId into the sibling `fields`
// catalog from GET /api/v1/organizations/{id}/project-types. Kept generic (nothing Project-typed
// here): any future entity whose form is schema-driven the same way can reuse this parser.

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
  isRequired: boolean;
  isReadOnly: boolean;
  fieldId?: number | string | null;
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
 * inline-size }`) — the usual 768/992 breakpoint values, same percentage math,
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
  return JSON.parse(layoutJson) as FormLayoutPanel[];
}

// Panel `name` is an i18n message code (JSF resolves it against the message bundle), not a
// ready-to-display label — ActionUnitDetailsForm only ever uses these four. Falls back to the
// raw code for any panel this map doesn't know about, rather than crashing on the next one added.
const PANEL_LABELS: Record<string, string> = {
  "common.header.general": "Général",
  "common.label.localisation": "Localisation",
  "actionunit.header.administrative": "Administratif",
  "actionunit.header.documentation": "Documentation",
};

export function panelLabel(nameCode: string): string {
  return PANEL_LABELS[nameCode] ?? nameCode;
}
