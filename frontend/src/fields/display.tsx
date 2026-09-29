// Read-only cell formatting for a dynamic (schema-driven) list column. Deliberately separate from
// fields/renderers.tsx: those are full PrimeReact edit-mode widgets meant for a form or an edit
// overlay, which is far more machinery than a table cell needs. A cell just needs a string.
import type { CSSProperties, ReactNode, SyntheticEvent } from "react";
import type { FieldResource } from "./types";
import { unwrapAnswer } from "./types";
import { getEntityType } from "../entities/registry";
import { useOpenEntity } from "../panels/entityNavigation";

interface ResourceRefLike {
  resourceId: string;
  resourceType: string;
  label?: string | null;
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

/** A reference value as {id, label, resourceType}, whichever shape it arrived in; null otherwise. */
function asRef(value: unknown): { id: string; label: string; resourceType: string } | null {
  if (isResourceRef(value)) {
    return { id: String(value.resourceId), label: value.label ?? String(value.resourceId), resourceType: value.resourceType };
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

// What a reference points at, by its resourceType: the registry entity type its chip opens (none
// for concepts and persons, which have no fiche) and its colour — its entity's, Siamois green for
// vocabulary concepts.
const REF_KINDS: Record<string, { entityType?: string; color: string }> = {
  "spatial-units": { entityType: "place", color: ENTITY_COLORS.place },
  places: { entityType: "place", color: ENTITY_COLORS.place },
  "action-units": { entityType: "project", color: ENTITY_COLORS.project },
  projects: { entityType: "project", color: ENTITY_COLORS.project },
  "recording-units": { entityType: "recordingUnit", color: ENTITY_COLORS.recordingUnit },
  finds: { entityType: "find", color: ENTITY_COLORS.find },
  phases: { entityType: "phase", color: ENTITY_COLORS.phase },
  containers: { entityType: "container", color: ENTITY_COLORS.container },
  concepts: { color: "var(--siamois-green, #80b480)" },
  persons: { color: "var(--text-color-secondary, #6b7280)" },
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
  const style = { "--ref-chip-color": refColor(resourceType) } as CSSProperties;

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
    return value
        .map((item) => formatOne(field, item))
        .filter((s) => s.length > 0)
        .join(", ");
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
  if (items.length === 0) return "";

  const render = (item: unknown, key?: number): ReactNode => {
    const ref = asRef(item);
    return ref ? <RefChip key={key} {...ref} /> : formatOne(field, item);
  };

  if (items.length === 1) return render(items[0]);
  if (options.all) {
    return <span className="cell-multi cell-multi-all">{items.map((item, i) => render(item, i))}</span>;
  }
  return (
    <span className="cell-multi">
      <span className="cell-multi-first">{render(items[0])}</span>
      <span className="cell-multi-more" title={items.map((item) => formatOne(field, item)).join(", ")}>
        +{items.length - 1}
      </span>
    </span>
  );
}
