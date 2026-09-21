import { useState } from "react";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { InputNumber } from "primereact/inputnumber";
import { Calendar } from "primereact/calendar";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import type { FieldRendererProps } from "./registry";
import { optionSourceFor, type FilterOption } from "./optionSources";

// Base renderers for the answerTypes that need no backend lookup — plain scalar inputs.
// answerTypes that resolve against a vocabulary/entity (SELECT_ONE_FROM_FIELD_CODE,
// SELECT_MULTIPLE_FROM_FIELD_CODE, SELECT_ONE_SPATIAL_UNIT) are implemented further down, backed
// by fields/optionSources.ts's async loaders (org-scoped concepts / places autocomplete). Still
// deliberately unimplemented: SELECT_MULTIPLE_SPATIAL_UNIT_TREE (a tree picker, out of scope this
// phase) and the rarer SELECT_* variants (person/action-unit/recording-unit/...), which have no
// option source client yet — those fall through to FallbackRenderer, same as before.

export function TextRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
  const stringValue = typeof value === "string" ? value : "";
  return (
    <InputText
      value={stringValue}
      disabled={readOnly}
      required={required}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

export function TextAreaRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
  const stringValue = typeof value === "string" ? value : "";
  return (
    <InputTextarea
      value={stringValue}
      disabled={readOnly}
      required={required}
      onChange={(e) => onChange(e.target.value)}
      autoResize
    />
  );
}

export function IntegerRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
  const numberValue = typeof value === "number" ? value : null;
  return (
    <InputNumber
      value={numberValue}
      disabled={readOnly}
      required={required}
      useGrouping={false}
      onValueChange={(e) => onChange(e.value ?? null)}
    />
  );
}

export function DecimalRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
  const numberValue = typeof value === "number" ? value : null;
  return (
    <InputNumber
      value={numberValue}
      disabled={readOnly}
      required={required}
      mode="decimal"
      maxFractionDigits={6}
      useGrouping={false}
      onValueChange={(e) => onChange(e.value ?? null)}
    />
  );
}

export function DateRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
  const dateValue = typeof value === "string" && value ? new Date(value) : null;
  return (
    <Calendar
      value={dateValue}
      disabled={readOnly}
      required={required}
      dateFormat="dd/mm/yy"
      onChange={(e) => onChange(e.value ? (e.value as Date).toISOString().slice(0, 10) : null)}
    />
  );
}

interface ResourceRefLike {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

// Two shapes carry "a resolved concept/place reference" in this codebase, and the same field can
// arrive as either depending on where its value came from: ProjectAnswersProjector's `answers` map
// (list rows, phase 1) emits {resourceId, resourceType, label} (ResourceRef); ProjectResource's own
// flat properties (type, mainLocation — GET /api/v1/projects/{id}, no `answers`) use
// {id, resourceType, resolvedLabel|name} (ResolvedConceptResource/PlaceLightResource). Both are
// accepted here so the same renderer works regardless of which path resolveValueBinding took.
interface ResolvedResourceLike {
  id: string;
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

function toOption(value: ResourceRefLike | ResolvedResourceLike): FilterOption {
  if ("resourceId" in value) {
    return { id: value.resourceId, label: value.label ?? value.resourceId };
  }
  return { id: value.id, label: value.resolvedLabel ?? value.name ?? value.id };
}

// Shared by the three SELECT_* renderers below: an async, search-as-you-type single/multi picker
// over fields/optionSources.ts's loader. One component rather than three near-identical ones,
// since the only real difference between "pick a concept", "pick several concepts" and "pick a
// spatial unit" is the loader and whether `multiple` is set — the value shape (ResourceRef /
// ResourceRef[]) and the AutoComplete wiring are otherwise the same.
function ResourceRefRenderer({ field, value, readOnly, required, onChange, organizationId, multiple }: FieldRendererProps & { multiple: boolean }) {
  const [suggestions, setSuggestions] = useState<FilterOption[]>([]);
  const loadOptions = organizationId != null ? optionSourceFor(field, organizationId) : null;

  async function search(e: AutoCompleteCompleteEvent) {
    if (!loadOptions) return;
    setSuggestions(await loadOptions(e.query));
  }

  const selected = multiple
    ? (Array.isArray(value) ? value.filter((v): v is ResourceRefLike | ResolvedResourceLike => isResourceRef(v) || isResolvedResource(v)).map(toOption) : [])
    : (isResourceRef(value) || isResolvedResource(value) ? toOption(value) : null);

  function commit(next: FilterOption[] | FilterOption | null) {
    if (multiple) {
      const options = (next as FilterOption[] | null) ?? [];
      onChange(options.map((o): ResourceRefLike => ({ resourceId: o.id, resourceType: "concepts", label: o.label })));
    } else {
      const option = next as FilterOption | null;
      onChange(option ? { resourceId: option.id, resourceType: "concepts", label: option.label } : null);
    }
  }

  return (
    <AutoComplete
      value={selected}
      suggestions={suggestions}
      completeMethod={search}
      field="label"
      multiple={multiple}
      dropdown={false}
      disabled={readOnly}
      required={required}
      onChange={(e) => commit(e.value as FilterOption[] | FilterOption | null)}
    />
  );
}

export function SelectOneConceptRenderer(props: FieldRendererProps) {
  return <ResourceRefRenderer {...props} multiple={false} />;
}

export function SelectManyConceptRenderer(props: FieldRendererProps) {
  return <ResourceRefRenderer {...props} multiple />;
}

export function SelectOneSpatialUnitRenderer(props: FieldRendererProps) {
  return <ResourceRefRenderer {...props} multiple={false} />;
}

export function FallbackRenderer({ field, value }: FieldRendererProps) {
  return (
    <span className="field-renderer-unsupported" title={`answerType "${field.answerType}" has no renderer yet`}>
      {formatFallbackValue(value)}
    </span>
  );
}

// Values bound to a resource object (ProjectResource.type/mainLocation, both ResolvedConcept/
// PlaceLight-shaped, and spatialContext, an array of the latter) would otherwise print
// "[object Object]" via a plain String(value) — this is the fallback's own display logic, not a
// per-entity concern, since any future entity's SELECT_*-typed fields hit the same shape before
// their real renderer exists.
function formatFallbackValue(value: unknown): string {
  if (value == null) return "—";
  if (Array.isArray(value)) {
    const labels = value.map(formatFallbackValue).filter((label) => label !== "—");
    return labels.length ? labels.join(", ") : "—";
  }
  if (typeof value === "object") {
    const label = (value as { resolvedLabel?: unknown; name?: unknown }).resolvedLabel
      ?? (value as { name?: unknown }).name;
    return typeof label === "string" && label ? label : "—";
  }
  return String(value);
}
