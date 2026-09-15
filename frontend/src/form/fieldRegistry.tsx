import { useCallback, useState, type ComponentType } from "react";
import { InputText } from "primereact/inputtext";
import { InputNumber } from "primereact/inputnumber";
import { Calendar } from "primereact/calendar";
import { AutoComplete } from "primereact/autocomplete";
import { searchConcepts, searchContainers, searchPhases, searchSpecimens } from "../api/autocomplete";
import type { LocalValue } from "./answerCodec";
import type { FieldResource, ResourceRef } from "./schema";

export interface FieldRendererProps {
  field: FieldResource;
  value: LocalValue;
  onChange: (value: LocalValue) => void;
  disabled: boolean;
  /** Needed by the project-scoped autocomplete sources (concepts/phases/containers/specimens). */
  projectId: string;
}

function TextRenderer({ value, onChange, disabled, field }: FieldRendererProps) {
  return (
    <InputText
      value={(value as string) ?? ""}
      onChange={(e) => onChange(e.target.value === "" ? null : e.target.value)}
      disabled={disabled}
      placeholder={field.hint ?? undefined}
      className="w-full"
    />
  );
}

function IntegerRenderer({ value, onChange, disabled }: FieldRendererProps) {
  return (
    <InputNumber
      value={value as number | null}
      onValueChange={(e) => onChange(e.value ?? null)}
      disabled={disabled}
      useGrouping={false}
      className="w-full"
    />
  );
}

function DecimalRenderer({ value, onChange, disabled }: FieldRendererProps) {
  // DECIMAL answers travel as stringified text server-side (see schema.ts) — parse for editing,
  // stringify back on change, so the local value stays a string all the way to toAnswerInput.
  const numeric = value == null || value === "" ? null : Number(value);
  return (
    <InputNumber
      value={Number.isNaN(numeric) ? null : numeric}
      onValueChange={(e) => onChange(e.value == null ? null : String(e.value))}
      disabled={disabled}
      mode="decimal"
      minFractionDigits={0}
      maxFractionDigits={6}
      className="w-full"
    />
  );
}

function DateTimeRenderer({ value, onChange, disabled }: FieldRendererProps) {
  const date = value ? new Date(value as string) : null;
  return (
    <Calendar
      value={date}
      onChange={(e) => onChange(e.value ? (e.value as Date).toISOString() : null)}
      disabled={disabled}
      showIcon
      dateFormat="dd/mm/yy"
      className="w-full"
    />
  );
}

type SearchFn = (projectId: string, q: string) => Promise<ResourceRef[]>;

/** PrimeReact doesn't export this event's type from its public entry point; shaped to match it. */
interface CompleteEvent {
  query: string;
}

/** Single-select autocomplete over a project-scoped search source. */
function makeSelectOneAutoComplete(search: SearchFn) {
  return function SelectOneAutoComplete({ value, onChange, disabled, projectId }: FieldRendererProps) {
    const [suggestions, setSuggestions] = useState<ResourceRef[]>([]);
    const complete = useCallback(
      (e: CompleteEvent) => {
        search(projectId, e.query).then(setSuggestions).catch(() => setSuggestions([]));
      },
      [projectId],
    );
    return (
      <AutoComplete
        value={value as ResourceRef | null}
        suggestions={suggestions}
        completeMethod={complete}
        field="label"
        onChange={(e) => onChange((e.value as ResourceRef) ?? null)}
        disabled={disabled}
        dropdown
        className="w-full"
      />
    );
  };
}

/** Multi-select autocomplete over a project-scoped search source. */
function makeSelectManyAutoComplete(search: SearchFn) {
  return function SelectManyAutoComplete({ value, onChange, disabled, projectId }: FieldRendererProps) {
    const [suggestions, setSuggestions] = useState<ResourceRef[]>([]);
    const complete = useCallback(
      (e: CompleteEvent) => {
        search(projectId, e.query).then(setSuggestions).catch(() => setSuggestions([]));
      },
      [projectId],
    );
    return (
      <AutoComplete
        value={(value as ResourceRef[]) ?? []}
        suggestions={suggestions}
        completeMethod={complete}
        field="label"
        multiple
        onChange={(e) => onChange((e.value as ResourceRef[]) ?? [])}
        disabled={disabled}
        className="w-full"
      />
    );
  };
}

/** Concept autocomplete: the field's own `fieldCode` selects the vocabulary — only wired for the
 *  *_FROM_FIELD_CODE answerTypes, since plain SELECT_ONE/SELECT_MULTIPLE fields carry no fieldCode
 *  and GET /api/v1/projects/{id}/concepts requires one (400 without it). */
function ConceptSelectOne(props: FieldRendererProps) {
  const Component = makeSelectOneAutoComplete((projectId, q) => searchConcepts(projectId, props.field.fieldCode ?? "", q));
  return <Component {...props} />;
}
function ConceptSelectMany(props: FieldRendererProps) {
  const Component = makeSelectManyAutoComplete((projectId, q) => searchConcepts(projectId, props.field.fieldCode ?? "", q));
  return <Component {...props} />;
}

const PhaseSelectMany = makeSelectManyAutoComplete(searchPhases);
const ContainerSelectMany = makeSelectManyAutoComplete(searchContainers);
const SpecimenSelectMany = makeSelectManyAutoComplete(searchSpecimens);

/** Honest placeholder for answerTypes with no wired data source yet (see api/autocomplete.ts header
 *  comment for why: institution-scoped pickers need an organizationId this panel doesn't have). */
function UnsupportedField({ field, value }: FieldRendererProps) {
  const label =
    value && typeof value === "object" && "label" in value
      ? ((value as ResourceRef).label ?? "")
      : Array.isArray(value)
        ? value.map((v) => v.label).join(", ")
        : String(value ?? "");
  return (
    <div className="p-inputtext w-full" style={{ opacity: 0.7 }}>
      {label || "—"}
      <div style={{ fontSize: "0.75rem", opacity: 0.7 }}>
        Édition non disponible dans ce panel pour le type « {field.answerType} ».
      </div>
    </div>
  );
}

const registry: Partial<Record<FieldResource["answerType"], ComponentType<FieldRendererProps>>> = {
  TEXT: TextRenderer,
  INTEGER: IntegerRenderer,
  DECIMAL: DecimalRenderer,
  DATETIME: DateTimeRenderer,
  SELECT_ONE_FROM_FIELD_CODE: ConceptSelectOne,
  SELECT_MULTIPLE_FROM_FIELD_CODE: ConceptSelectMany,
  SELECT_MULTIPLE_PHASE: PhaseSelectMany,
  SELECT_MULTIPLE_CONTAINER: ContainerSelectMany,
  SELECT_MULTIPLE_SPECIMEN: SpecimenSelectMany,
};

export function getFieldRenderer(answerType: FieldResource["answerType"]): ComponentType<FieldRendererProps> {
  return registry[answerType] ?? UnsupportedField;
}
