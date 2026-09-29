// Read-only cell formatting for a dynamic (schema-driven) list column. Deliberately separate from
// fields/renderers.tsx: those are full PrimeReact edit-mode widgets meant for a form or an edit
// overlay, which is far more machinery than a table cell needs. A cell just needs a string.
import { useRef, useState, type CSSProperties, type ReactNode, type SyntheticEvent } from "react";
import { OverlayPanel } from "primereact/overlaypanel";
import type { FieldResource, MultiValueAnswer } from "./types";
import { readMultiValue, unwrapAnswer } from "./types";
import { fetchAllValues } from "./multiValues";
import { getEntityType } from "../entities/registry";
import { useOpenEntity } from "../panels/entityNavigation";

// ResourceRef.StratigraphicQualifier: what a stratigraphic relationship says of its other unit.
interface StratigraphicQualifier {
  concept?: { label?: string | null } | null;
  position?: "anterior" | "posterior" | "synchronous" | null;
  uncertain?: boolean | null;
}

interface ResourceRefLike {
  resourceId: string;
  resourceType: string;
  label?: string | null;
  qualifier?: StratigraphicQualifier | null;
}

// The other shape a resolved reference arrives in: a flat entity property (ProjectResource.type /
// mainLocation / spatialContext — ResolvedConceptResource, PlaceLightResource).
interface ResolvedResourceLike {
  id: string | number;
  resourceType: string;
  resolvedLabel?: string | null;
  name?: string | null;
}

function isResourceRef(value: unknown): value is ResourceRefLike {
  return value != null && typeof value === "object" && "resourceId" in value;
}

function isResolvedResource(value: unknown): value is ResolvedResourceLike {
  return value != null && typeof value === "object" && "id" in value && "resourceType" in value;
}

/**
 * A qualified reference's label: the relationship's concept before the other unit ("coupe US 12"),
 * a "?" after it when the relationship is uncertain.
 */
function qualifiedLabel(label: string, qualifier: StratigraphicQualifier | null | undefined): string {
  if (!qualifier) return label;
  const concept = qualifier.concept?.label;
  return `${concept ? `${concept} ` : ""}${label}${qualifier.uncertain ? " ?" : ""}`;
}

/** A reference value as {id, label, resourceType}, whichever shape it arrived in; null otherwise. */
function asRef(value: unknown): { id: string; label: string; resourceType: string } | null {
  if (isResourceRef(value)) {
    const label = qualifiedLabel(value.label ?? String(value.resourceId), value.qualifier);
    return { id: String(value.resourceId), label, resourceType: value.resourceType };
  }
  if (isResolvedResource(value)) {
    return { id: String(value.id), label: value.resolvedLabel ?? value.name ?? String(value.id), resourceType: value.resourceType };
  }
  return null;
}

// Each entity type's colour — the theme's own (themed-panel in _panels.scss): context for places and
// projects, ground for recording units, finds and phases, third for containers. A chip showing an
// entity always takes its entity's colour, never the colour of the panel it sits in.
const ENTITY_COLORS: Record<string, string> = {
  place: "var(--context-main-color, #068da9)",
  project: "var(--context-main-color, #068da9)",
  recordingUnit: "var(--ground-main-color, #7e1717)",
  find: "var(--ground-main-color, #7e1717)",
  phase: "var(--ground-main-color, #7e1717)",
  container: "var(--third-main-color, #e55807)",
};

/** An entity type's colour (registry key), or undefined for a type without one. */
export function entityColor(entityType: string | undefined): string | undefined {
  return entityType ? ENTITY_COLORS[entityType] : undefined;
}

/** The inline style giving an entity chip (.entity-nav-chip) its entity's colour. */
export function entityChipStyle(entityType: string | undefined): CSSProperties | undefined {
  const color = entityColor(entityType);
  return color ? ({ "--entity-chip-color": color } as CSSProperties) : undefined;
}

// The -100 variant of each palette (_variables.scss): the read-only chip's border, light enough
// that a cell full of references doesn't read as a wall of boxes, the label keeping the full colour.
const CONTEXT_BORDER = "var(--context-light-color-100, #cde8ee)";
const GROUND_BORDER = "var(--ground-light-color-100, #e5d1d1)";
const THIRD_BORDER = "var(--third-light-color-100, #fadecd)";
// Not --light-color-100: _panels.scss redefines that one per panel (ground red in a recording-unit
// panel), and a vocabulary concept is Siamois green wherever it sits.
const GREEN_BORDER = "var(--siamois-green-light-100, #e6f0e6)";

// What a reference points at, by its resourceType: the registry entity type its chip opens (none
// for concepts and persons, which have no fiche), its colour — its entity's, Siamois green for
// vocabulary concepts — and the lighter one its read-only chip is outlined with.
const REF_KINDS: Record<string, { entityType?: string; color: string; border: string }> = {
  "spatial-units": { entityType: "place", color: ENTITY_COLORS.place, border: CONTEXT_BORDER },
  places: { entityType: "place", color: ENTITY_COLORS.place, border: CONTEXT_BORDER },
  "action-units": { entityType: "project", color: ENTITY_COLORS.project, border: CONTEXT_BORDER },
  projects: { entityType: "project", color: ENTITY_COLORS.project, border: CONTEXT_BORDER },
  "recording-units": { entityType: "recordingUnit", color: ENTITY_COLORS.recordingUnit, border: GROUND_BORDER },
  finds: { entityType: "find", color: ENTITY_COLORS.find, border: GROUND_BORDER },
  phases: { entityType: "phase", color: ENTITY_COLORS.phase, border: GROUND_BORDER },
  containers: { entityType: "container", color: ENTITY_COLORS.container, border: THIRD_BORDER },
  concepts: { color: "var(--siamois-green, #80b480)", border: GREEN_BORDER },
  persons: { color: "var(--text-color-secondary, #6b7280)", border: "var(--surface-300, #e0e0e0)" },
};
const DEFAULT_REF_COLOR = "var(--siamois-green, #80b480)";

/** The colour a reference's chip takes — also what its edit-mode token uses (fields/renderers.tsx). */
export function refColor(resourceType: string): string {
  return REF_KINDS[resourceType]?.color ?? DEFAULT_REF_COLOR;
}

/**
 * A read-only reference: a light outlined chip in its type's colour — the same token the
 * reference picker shows in edit mode, minus the remove button. A reference to an entity with a
 * fiche opens it in the overview; the click stops there, so it doesn't also open the field's editor.
 */
export function RefChip({ label, id, resourceType }: { label: string; id: string; resourceType: string }) {
  const openEntity = useOpenEntity();
  const entityType = REF_KINDS[resourceType]?.entityType;
  const linkable = entityType != null && openEntity != null && getEntityType(entityType) != null;
  const style = {
    "--ref-chip-color": refColor(resourceType),
    "--ref-chip-border": REF_KINDS[resourceType]?.border ?? GREEN_BORDER,
  } as CSSProperties;

  function open(e: SyntheticEvent) {
    e.stopPropagation();
    openEntity!(entityType!, id);
  }

  return linkable ? (
    <span
      className="ref-chip ref-chip-link"
      style={style}
      role="link"
      tabIndex={0}
      title={`Ouvrir « ${label} »`}
      onClick={open}
      onKeyDown={(e) => {
        if (e.key === "Enter") open(e);
      }}
    >
      {label}
    </span>
  ) : (
    <span className="ref-chip" style={style} title={label}>
      {label}
    </span>
  );
}

function formatOne(field: FieldResource, value: unknown): string {
  // A null inside a multi-valued answer would otherwise print the string "null" and, worse, count
  // towards the "+N" — the array filters on label length, not on the raw item.
  if (value == null) return "";
  const ref = asRef(value);
  if (ref) return ref.label;
  if (field.answerType === "MEASUREMENT" && typeof value === "object") {
    const m = value as { numericValue?: number | null; symbol?: string | null; comment?: string | null };
    const measure = m.numericValue != null ? [m.numericValue, m.symbol].filter((p) => p != null && p !== "").join(" ") : "";
    return [measure, m.comment].filter(Boolean).join(" — ");
  }
  if (field.answerType === "DATETIME" && typeof value === "string") {
    return value.slice(0, 10);
  }
  if (typeof value === "number") {
    return String(value);
  }
  return String(value);
}

/**
 * Formats a projected `answers[fieldId]` value for a read-only table cell. Accepts either the raw
 * value ProjectAnswersProjector emits on list rows or a FieldAnswer envelope (unwrapped the same
 * way resolveValueBinding does), so callers can pass either straight through.
 */
export function renderAnswerValue(field: FieldResource, rawValue: unknown): string {
  const value = unwrapAnswer(rawValue);
  if (value == null) return "";
  if (Array.isArray(value)) {
    const shown = value.map((item) => formatOne(field, item)).filter((s) => s.length > 0);
    // A preview says how many values it leaves out rather than passing for the whole list.
    const hidden = (readMultiValue(rawValue)?.total ?? value.length) - value.length;
    return shown.join(", ") + (hidden > 0 ? ` (+${hidden})` : "");
  }
  return formatOne(field, value);
}

/**
 * Cell rendering for a projected answer — the display counterpart of {@link renderAnswerValue},
 * which stays the plain-text version (tooltips, tests, anything that needs a string).
 *
 * <p>References render as {@link RefChip}s. By default (a table cell, one line tall) a multi-valued
 * answer shows its first value plus a {@code +N} count, never the full list: a joined list is just a
 * long string that gets cut mid-label. The full list stays available as the counter's tooltip.
 * {@code all} (a fiche field, which wraps) shows every value.</p>
 */
export function renderAnswerCell(field: FieldResource, rawValue: unknown, options: { all?: boolean } = {}): ReactNode {
  const value = unwrapAnswer(rawValue);
  if (value == null) return "";
  const items = (Array.isArray(value) ? value : [value]).filter((item) => formatOne(field, item).length > 0);
  // A multi-valued answer may be a preview: its total, not its preview's length, is how many
  // values there are.
  const multi = Array.isArray(rawValue) ? null : readMultiValue(rawValue);
  const total = multi ? Math.max(multi.total, items.length) : items.length;
  if (total === 0) return "";

  const render = (item: unknown, key?: number): ReactNode => {
    const ref = asRef(item);
    return ref ? <RefChip key={key} {...ref} /> : formatOne(field, item);
  };

  if (items.length === 1 && total === 1) return render(items[0]);
  const complete = multi == null || multi.complete;
  if (options.all && complete) {
    return <span className="cell-multi cell-multi-all">{items.map((item, i) => render(item, i))}</span>;
  }
  // A cell shows its first value; a fiche every value it has. Either way, what isn't shown is a
  // "+N" — which, when the answer is only a preview, opens the whole list.
  const shown = options.all ? items : items.slice(0, 1);
  const hidden = total - shown.length;
  return (
    <span className={options.all ? "cell-multi cell-multi-all" : "cell-multi"}>
      {options.all
        ? shown.map((item, i) => render(item, i))
        : shown.length > 0 && <span className="cell-multi-first">{render(shown[0])}</span>}
      {hidden > 0 &&
        (complete ? (
          <span className="cell-multi-more" title={items.map((item) => formatOne(field, item)).join(", ")}>
            +{hidden}
          </span>
        ) : (
          <MoreValues field={field} answer={multi!} hidden={hidden} render={render} />
        ))}
    </span>
  );
}

/**
 * The "+N" of a preview: a button that fetches the answer's whole list (its `_links.values`) and
 * shows it in a popover. The click stops there, so it doesn't also open the field's editor.
 */
function MoreValues({
  field,
  answer,
  hidden,
  render,
}: {
  field: FieldResource;
  answer: MultiValueAnswer;
  hidden: number;
  render: (item: unknown, key?: number) => ReactNode;
}) {
  const panelRef = useRef<OverlayPanel>(null);
  const [values, setValues] = useState<unknown[] | null>(null);
  const [failed, setFailed] = useState(false);

  function open(e: SyntheticEvent) {
    e.stopPropagation();
    panelRef.current?.toggle(e);
    if (values != null) return;
    fetchAllValues(answer)
      .then(setValues)
      .catch(() => setFailed(true));
  }

  return (
    <>
      <span
        className="cell-multi-more cell-multi-more-link"
        role="button"
        tabIndex={0}
        title={`Voir les ${answer.total} valeurs de « ${field.label} »`}
        onClick={open}
        onKeyDown={(e) => {
          if (e.key === "Enter") open(e);
        }}
      >
        +{hidden}
      </span>
      <OverlayPanel ref={panelRef} className="cell-multi-overlay" onClick={(e) => e.stopPropagation()}>
        {failed ? (
          <span className="cell-multi-overlay-status">Impossible de charger les valeurs.</span>
        ) : values == null ? (
          <span className="cell-multi-overlay-status">Chargement…</span>
        ) : (
          <span className="cell-multi cell-multi-all">{values.map((item, i) => render(item, i))}</span>
        )}
      </OverlayPanel>
    </>
  );
}
