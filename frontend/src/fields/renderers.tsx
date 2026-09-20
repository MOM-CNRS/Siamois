import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { InputNumber } from "primereact/inputnumber";
import { Calendar } from "primereact/calendar";
import type { FieldRendererProps } from "./registry";

// Base renderers for the answerTypes that need no backend lookup — plain scalar inputs.
// answerTypes that resolve against a vocabulary/entity (SELECT_ONE_FROM_FIELD_CODE,
// SELECT_MULTIPLE_FROM_FIELD_CODE, SELECT_ONE_SPATIAL_UNIT, SELECT_MULTIPLE_SPATIAL_UNIT_TREE,
// and the several other SELECT_* variants CustomField has) are deliberately NOT implemented
// here yet: they need the concepts/autocomplete API client, which lands in the API-prep phase
// (plan §8 phase 3) — see FallbackRenderer below. Don't build them ahead of that client "for
// completeness".

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
