import { useRef, useState } from "react";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { InputNumber } from "primereact/inputnumber";
import { Calendar } from "primereact/calendar";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { Button } from "primereact/button";
import { CreateEntityDialog } from "../components/CreateEntityDialog";
import { useOpenEntity } from "../panels/entityNavigation";
import { getEntityType } from "../entities/registry";
import type { CreatePrefill } from "../entities/types";
import type { FieldRendererProps } from "./registry";
import type { FieldEditContext } from "./editContext";
import {
  entityRowLabel,
  optionSourceFor,
  referenceTargetOf,
  supportsEmptyQuery,
  type FilterOption,
  type ReferenceTarget,
} from "./optionSources";

// Base renderers for the answerTypes that need no backend lookup — plain scalar inputs — then one
// generic reference picker (ResourceRefRenderer) for every answerType that points at a concept,
// person, project, place, recording unit, find, phase or container, backed by
// fields/optionSources.ts's async loaders. Still read-only (FallbackRenderer): action codes and
// addresses (the latter waits for the GéoPlateforme lookup).

// One TEXT renderer, two shapes: CustomFieldText.isTextArea is what picks p:inputTextarea over
// p:inputText in JSF (pages/shared/inplace/text.xhtml), and it now rides along on FieldResource,
// so the branch belongs here rather than in a second registry entry that only the fiche knows to
// ask for. The table's cell overlay gets the multi-line editor for `comments`/`scientificNotice`
// for free as a result.
export function TextRenderer(props: FieldRendererProps) {
  return props.field.isTextArea ? <TextAreaRenderer {...props} /> : <SingleLineTextRenderer {...props} />;
}

export function SingleLineTextRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
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

export function IntegerRenderer({ field, value, readOnly, required, onChange }: FieldRendererProps) {
  const numberValue = typeof value === "number" ? value : null;
  return (
    <InputNumber
      value={numberValue}
      disabled={readOnly}
      required={required}
      min={field.constraints?.min ?? undefined}
      max={field.constraints?.max ?? undefined}
      useGrouping={false}
      onValueChange={(e) => onChange(e.value ?? null)}
    />
  );
}

export function DecimalRenderer({ field, value, readOnly, required, onChange }: FieldRendererProps) {
  const numberValue = typeof value === "number" ? value : null;
  return (
    <InputNumber
      value={numberValue}
      disabled={readOnly}
      required={required}
      min={field.constraints?.min ?? undefined}
      max={field.constraints?.max ?? undefined}
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

// The one reference picker: an async, search-as-you-type single/multi AutoComplete over
// fields/optionSources.ts's loader for the field's answerType. What differs between "pick a
// concept", "pick persons" or "pick phases" is only the loader, the ResourceRef's resourceType and
// whether a « Nouveau » footer can create the target — all derived from the field
// (referenceTargetOf), so a new reference answerType is one line in optionSources.ts.
function ResourceRefRenderer({ field, value, readOnly, required, onChange, organizationId, context, multiple }: FieldRendererProps & { multiple: boolean }) {
  const [suggestions, setSuggestions] = useState<FilterOption[]>([]);
  const [creating, setCreating] = useState(false);
  const orgId = organizationId ?? context?.organizationId;
  const loadOptions = orgId != null ? optionSourceFor(field, orgId, context?.projectId) : null;
  const autoCompleteRef = useRef<AutoComplete>(null);
  const target = referenceTargetOf(field);
  const openEntity = useOpenEntity();
  // Only a value this app has a fiche for is a link.
  const linkEntityType = target.entityType && getEntityType(target.entityType) ? target.entityType : undefined;

  async function search(e: AutoCompleteCompleteEvent) {
    if (!loadOptions) return;
    setSuggestions(await loadOptions(e.query));
  }

  // PrimeReact only searches once something is typed, so a freshly-focused picker is a blank box
  // with no indication that there is anything to pick. Searching on focus with whatever the input
  // currently holds ("" on open) gives the user the starting list immediately — for concepts that
  // is the field's whole vocabulary, which is exactly what they would get by typing and deleting a
  // character. Skipped for the answerTypes whose endpoint rejects an empty query (spatial units),
  // where it would be a guaranteed-failing request.
  function onFocus(e: React.FocusEvent<HTMLInputElement>) {
    const query = e.target.value ?? "";
    if (!query && !supportsEmptyQuery(field)) return;
    // `search` is AutoComplete's own imperative entry point. The source must not be "input":
    // that is the only one it refuses a blank query for. "dropdown" is the typed value that means
    // "opened rather than typed into", which is exactly this case (there is no dropdown button).
    autoCompleteRef.current?.search(e, query, "dropdown");
  }

  const selected = multiple
    ? (Array.isArray(value) ? value.filter((v): v is ResourceRefLike | ResolvedResourceLike => isResourceRef(v) || isResolvedResource(v)).map(toOption) : [])
    : (isResourceRef(value) || isResolvedResource(value) ? toOption(value) : null);

  const toRef = (o: FilterOption): ResourceRefLike => ({ resourceId: o.id, resourceType: target.resourceType, label: o.label });

  function commit(next: FilterOption[] | FilterOption | null) {
    if (multiple) {
      onChange(((next as FilterOption[] | null) ?? []).map(toRef));
    } else {
      const option = next as FilterOption | null;
      onChange(option ? toRef(option) : null);
    }
  }

  // « Nouveau » creates the target in the edited entity's project and picks it straight away — it
  // is added to a multi-valued answer, it replaces a single one.
  const createConfig = !readOnly ? creatableConfig(target, context) : undefined;
  async function onCreated(id: string | number) {
    setCreating(false);
    let label = String(id);
    try {
      label = entityRowLabel(await createConfig!.api.get(id));
    } catch {
      // The entity exists; only its label could not be read back. The id stands in for it until
      // the edit reloads the row.
    }
    const created: FilterOption = { id: String(id), label };
    if (multiple) commit([...(selected as FilterOption[]).filter((o) => o.id !== created.id), created]);
    else commit(created);
  }

  const footer = createConfig
    ? (_: unknown, hide: () => void) => (
        <div className="resource-ref-create-footer">
          <Button
            type="button"
            text
            size="small"
            icon="bi bi-plus-lg"
            label={`Nouveau : ${createConfig.labels.singular.toLowerCase()}`}
            onClick={() => {
              hide();
              setCreating(true);
            }}
          />
        </div>
      )
    : undefined;

  // A picked entity's chip label opens its fiche (multi-valued pickers only: a single value sits in
  // the input itself, where a click means "edit the text").
  const renderToken =
    multiple && linkEntityType && openEntity
      ? (item: unknown) => {
          const option = item as FilterOption;
          return (
            <span
              className="resource-ref-token-link"
              role="link"
              tabIndex={-1}
              title={`Ouvrir « ${option.label} »`}
              // mousedown would focus the input and open the suggestions first.
              onMouseDown={(e) => {
                e.preventDefault();
                e.stopPropagation();
              }}
              onClick={(e) => {
                e.stopPropagation();
                openEntity(linkEntityType, option.id);
              }}
            >
              {option.label}
            </span>
          );
        }
      : undefined;

  return (
    <>
      <AutoComplete
        ref={autoCompleteRef}
        value={selected}
        suggestions={suggestions}
        completeMethod={search}
        field="label"
        multiple={multiple}
        dropdown={false}
        disabled={readOnly}
        required={required}
        onFocus={onFocus}
        // With a footer, the panel must open even on no match: that is exactly when « Nouveau »
        // is wanted.
        showEmptyMessage={footer != null}
        emptyMessage="Aucun résultat"
        panelFooterTemplate={footer}
        selectedItemTemplate={renderToken}
        onChange={(e) => commit(e.value as FilterOption[] | FilterOption | null)}
      />
      {createConfig && (
        // The dialog is portalled out of the edit overlay's DOM box but not out of its React tree:
        // keys typed in it would bubble to the overlay's own Escape/Enter handling (cancelling the
        // whole edit) without this boundary.
        <div onKeyDown={(e) => e.stopPropagation()} style={{ display: "contents" }}>
          <CreateEntityDialog
            entityType={target.createEntityType!}
            visible={creating}
            organizationId={orgId}
            scope={context?.projectId ? { entityType: "project", id: context.projectId } : undefined}
            prefill={createPrefill(target, context)}
            onCreated={(id) => void onCreated(id)}
            onHide={() => setCreating(false)}
          />
        </div>
      )}
    </>
  );
}

// The entity config a « Nouveau » creates, or undefined when the field offers none: its target is
// not created from a field, or it is created inside a project and the edited entity has none.
function creatableConfig(target: ReferenceTarget, context: FieldEditContext | undefined) {
  if (!target.createEntityType) return undefined;
  const config = getEntityType(target.createEntityType);
  if (!config?.list.createForm) return undefined;
  const projectBound = target.createEntityType !== "place";
  if (projectBound && !context?.projectId) return undefined;
  return config;
}

// A find created from a recording unit's field is created ON that recording unit — what JSF's
// "new find" row action does too. Nothing else has a link implied by the field it comes from.
function createPrefill(target: ReferenceTarget, context: FieldEditContext | undefined): CreatePrefill | undefined {
  if (target.createEntityType === "find" && context?.entityType === "recordingUnit" && context.entityId != null) {
    return { recordingUnit: { id: context.entityId, label: context.entityLabel ?? String(context.entityId) } };
  }
  return undefined;
}

export function SelectOneRefRenderer(props: FieldRendererProps) {
  return <ResourceRefRenderer {...props} multiple={false} />;
}

export function SelectManyRefRenderer(props: FieldRendererProps) {
  return <ResourceRefRenderer {...props} multiple />;
}

// Kept under their historical names: tests and callers reference them.
export const SelectOneConceptRenderer = SelectOneRefRenderer;
export const SelectManyConceptRenderer = SelectManyRefRenderer;
export const SelectOneSpatialUnitRenderer = SelectOneRefRenderer;
// SPATIAL_CONTEXT is a tree picker in JSF (siaInplace:spatialMultiple); here it is the flat "pick
// several places" sibling over the same /api/v1/places/autocomplete source.
export const SelectManySpatialUnitRenderer = SelectManyRefRenderer;

interface MeasurementValue {
  numericValue?: number | null;
  symbol?: string | null;
  normalizedValue?: number | null;
  comment?: string | null;
}

// MEASUREMENT: a number in the field's own unit (the server always applies that unit — the client
// never picks one) plus an optional comment. Emits the MeasurementRef shape the PATCH accepts
// ({ numericValue, comment }), or null once both are empty.
export function MeasurementRenderer({ field, value, readOnly, required, onChange }: FieldRendererProps) {
  const current = (value != null && typeof value === "object" ? value : {}) as MeasurementValue;
  const unit = field.constraints?.unit ?? current.symbol ?? null;

  function emit(next: MeasurementValue) {
    const empty = next.numericValue == null && !next.comment;
    onChange(empty ? null : { numericValue: next.numericValue ?? null, symbol: unit, comment: next.comment || null });
  }

  return (
    <div className="measurement-renderer">
      <div className="p-inputgroup">
        <InputNumber
          value={current.numericValue ?? null}
          disabled={readOnly}
          required={required}
          mode="decimal"
          maxFractionDigits={6}
          useGrouping={false}
          min={field.constraints?.min ?? undefined}
          max={field.constraints?.max ?? undefined}
          onValueChange={(e) => emit({ ...current, numericValue: e.value ?? null })}
        />
        {unit && <span className="p-inputgroup-addon">{unit}</span>}
      </div>
      <InputText
        value={current.comment ?? ""}
        disabled={readOnly}
        placeholder="Commentaire"
        onChange={(e) => emit({ ...current, comment: e.target.value })}
      />
    </div>
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
