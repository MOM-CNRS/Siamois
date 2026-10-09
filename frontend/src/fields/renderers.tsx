import { ConceptOptionItem, useFieldWidthCap } from "./ConceptOptionItem";
import { PlaceOptionItem } from "./PlaceOptionItem";
import { useRef, useState, type CSSProperties } from "react";
import { InputText } from "primereact/inputtext";
import { InputTextarea } from "primereact/inputtextarea";
import { InputNumber } from "primereact/inputnumber";
import { Calendar } from "primereact/calendar";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { Button } from "primereact/button";
import { CreateEntityOverlay } from "../components/CreateEntityOverlay";
import { useOpenEntity } from "../panels/entityNavigation";
import { getEntityType } from "../entities/registry";
import { PROJECT_KEY } from "../entities/keys";
import type { Bound } from "../rules";
import type { FieldRendererProps } from "./registry";
import type { FieldEditContext } from "./editContext";
import { formatDateAnswer, parseDateAnswer } from "./dateAnswer";
import { refColor } from "./display";
import {
  createPlaceFromSuggestion,
  entityRowLabel,
  isExternalSuggestion,
  optionSourceFor,
  referenceTargetOf,
  type FilterOption,
  type OptionList,
  type ReferenceTarget,
} from "./optionSources";
import { t } from "../i18n";
import { useNotify } from "../notify/NotifyProvider";
import { messageForError } from "../api/errors";

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

function SingleLineTextRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
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

function TextAreaRenderer({ value, readOnly, required, onChange }: FieldRendererProps) {
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
      // onChange, not onValueChange: InputNumber only fires onValueChange on blur, and the cell
      // editor commits on Enter or on an outside mousedown — both before that blur — so the draft
      // would still hold the old value and nothing would be saved.
      onChange={(e) => onChange(e.value ?? null)}
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
      // See IntegerRenderer: onValueChange would only fire after the editor has already committed.
      onChange={(e) => onChange(e.value ?? null)}
    />
  );
}

export function DateRenderer({ field, value, readOnly, required, onChange, bounds }: FieldRendererProps) {
  const showTime = field.constraints?.showTime === true;
  return (
    <Calendar
      value={parseDateAnswer(value, showTime)}
      disabled={readOnly}
      required={required}
      // Days outside another field's date (closing before opening…) can't be picked; the overlay
      // still refuses a typed-in one (CellEditOverlay's bound check).
      minDate={dayBound(bounds?.min, showTime, +1)}
      maxDate={dayBound(bounds?.max, showTime, -1)}
      dateFormat="dd/mm/yy"
      showTime={showTime}
      hourFormat="24"
      // The panel lives inside the edit overlay's own fixed box instead of being appended to
      // <body>, where it grew the document past the 100vh shell (a page scrollbar) and landed
      // below the fold.
      appendTo="self"
      onChange={(e) => onChange(e.value ? formatDateAnswer(e.value as Date, showTime) : null)}
    />
  );
}

/** A rules bound as a Calendar limit: the bound's own day, shifted a day for an exclusive one. */
function dayBound(bound: Bound | undefined, showTime: boolean, exclusiveShift: 1 | -1): Date | undefined {
  if (!bound) return undefined;
  const date = parseDateAnswer(bound.raw, showTime);
  if (!date) return undefined;
  if (bound.exclusive && !showTime) date.setDate(date.getDate() + exclusiveShift);
  return date;
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
const conceptItem = (o: FilterOption) => <ConceptOptionItem option={o} />;
const placeItem = (o: FilterOption) => <PlaceOptionItem option={o} />;

function ResourceRefRenderer({ field, value, readOnly, required, onChange, organizationId, context, optionsContext, declaredOptions, placeContext, fieldLabelOf, multiple }: FieldRendererProps & { multiple: boolean }) {
  const [suggestions, setSuggestions] = useState<FilterOption[]>([]);
  // Sources of the last search that could not be narrowed because the field they depend on is empty.
  const [unnarrowedSources, setUnnarrowedSources] = useState<string[]>([]);
  // Where the « Nouveau » form is open (the picker itself), or null.
  const [createAnchor, setCreateAnchor] = useState<HTMLElement | null>(null);
  const orgId = organizationId ?? context?.organizationId;
  const sourceOptions =
    orgId != null
      ? optionSourceFor(field, orgId, context?.projectId, { valueConceptId: context?.typeConceptId, optionsContext, placeContext })
      : null;
  const loadOptions = declaredOptions
    ? async (q?: string) => {
        const needle = (q ?? "").trim().toLowerCase();
        return declaredOptions.filter((option) => needle === "" || option.label.toLowerCase().includes(needle));
      }
    : sourceOptions;
  const autoCompleteRef = useRef<AutoComplete>(null);
  const { capTo, panelStyle } = useFieldWidthCap();
  const target = referenceTargetOf(field);
  const waitingForParent = optionsContext?.kind === "RELATED_CONCEPTS" && optionsContext.relatedTo == null;
  const openEntity = useOpenEntity();
  // Only a value this app has a fiche for is a link.
  const linkEntityType = target.entityType && getEntityType(target.entityType) ? target.entityType : undefined;

  async function search(e: AutoCompleteCompleteEvent) {
    if (!loadOptions) return;
    capTo(e);
    const found = (await loadOptions(e.query)) as OptionList;
    setUnnarrowedSources(found.unnarrowedSources ?? []);
    setSuggestions(found);
  }

  // PrimeReact only searches once something is typed, so a freshly-focused picker is a blank box
  // with no indication that there is anything to pick. Searching on focus gives the user the
  // starting list immediately — every option source answers an empty query with its first page.
  // A single picker whose input still shows the picked value's label searches with "" too: that
  // label would narrow the list to the value already there.
  function onFocus(e: React.FocusEvent<HTMLInputElement>) {
    const text = e.target.value ?? "";
    const current = !multiple && !asTokens ? (selected as FilterOption | null)?.label : undefined;
    const query = text === current ? "" : text;
    // `search` is AutoComplete's own imperative entry point. The source must not be "input":
    // that is the only one it refuses a blank query for. "dropdown" is the typed value that means
    // "opened rather than typed into", which is exactly this case (there is no dropdown button).
    autoCompleteRef.current?.search(e, query, "dropdown");
  }

  const selected = multiple
    ? (Array.isArray(value) ? value.filter((v): v is ResourceRefLike | ResolvedResourceLike => isResourceRef(v) || isResolvedResource(v)).map(toOption) : [])
    : (isResourceRef(value) || isResolvedResource(value) ? toOption(value) : null);

  // A single reference to an entity with a fiche is shown as a token too, not as the input's text:
  // the token is what links to the fiche (a click in the input means "edit the text"). Picking a
  // suggestion replaces it, its × clears the field.
  const asTokens = multiple || (linkEntityType != null && openEntity != null);
  const tokens = multiple ? selected : selected ? [selected as FilterOption] : [];

  const toRef = (o: FilterOption): ResourceRefLike => ({ resourceId: o.id, resourceType: target.resourceType, label: o.label });

  function commit(next: FilterOption[] | FilterOption | null) {
    if (multiple) {
      onChange(((next as FilterOption[] | null) ?? []).map(toRef));
    } else {
      const option = next as FilterOption | null;
      onChange(option ? toRef(option) : null);
    }
  }

  // A suggestion from an external source (INSEE, GéoPlateforme) is not a place yet: it is created
  // — once per organization, the server answers the existing one — before it can be picked.
  const notify = useNotify();
  const [creatingPlace, setCreatingPlace] = useState(false);
  async function commitPicked(next: FilterOption[] | FilterOption | null) {
    const picked = next == null ? [] : Array.isArray(next) ? next : [next];
    if (orgId == null || !picked.some(isExternalSuggestion)) {
      commit(next);
      return;
    }
    setCreatingPlace(true);
    try {
      const resolved = await Promise.all(
        picked.map((o) => (isExternalSuggestion(o) ? createPlaceFromSuggestion(orgId, o, context?.projectId) : o)),
      );
      commit(Array.isArray(next) ? resolved : resolved[0] ?? null);
    } catch (error) {
      notify.error(messageForError(error, t("field.placeCreateFailed")));
    } finally {
      setCreatingPlace(false);
    }
  }

  // « Nouveau » creates the target in the edited entity's project and picks it straight away — it
  // is added to a multi-valued answer, it replaces a single one.
  const createConfig = !readOnly ? creatableConfig(target, context) : undefined;
  async function onCreated(id: string | number) {
    setCreateAnchor(null);
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

  // Some sources searched without their filter: say which field to fill (or what is wrong with its place).
  const named = (ids: string[]) => ids.map((id) => `« ${fieldLabelOf?.(id) ?? id} »`).join(", ");
  const parents = Object.entries(placeContext?.deps ?? {});
  const emptyParents = parents.filter(([, placeId]) => placeId == null).map(([fieldId]) => fieldId);
  const narrowHint =
    emptyParents.length > 0
      ? t("field.fillToNarrow", { field: named(emptyParents) })
      : t("field.cannotNarrow", { field: named(parents.map(([fieldId]) => fieldId)) });

  const footer = createConfig || unnarrowedSources.length > 0
    ? (_: unknown, hide: () => void) => (
        <div className="resource-ref-create-footer">
          {unnarrowedSources.length > 0 && <small className="resource-ref-narrow-hint">{narrowHint}</small>}
          {createConfig && (
            <Button
              type="button"
              text
              size="small"
              icon="bi bi-plus-lg"
              label={t("field.newLower", { label: createConfig.labels.singular.toLowerCase() })}
              onClick={() => {
                hide();
                setCreateAnchor(autoCompleteRef.current?.getElement() ?? null);
              }}
            />
          )}
        </div>
      )
    : undefined;

  // A picked entity's chip label opens its fiche.
  const renderToken =
    asTokens && linkEntityType && openEntity
      ? (item: unknown) => {
          const option = item as FilterOption;
          return (
            <span
              className="resource-ref-token-link"
              role="link"
              tabIndex={-1}
              title={t("field.open", { label: option.label })}
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
        // Picked tokens take their type's colour, the same as the read-only chips (fields/display.tsx).
        className="resource-ref-picker"
        style={{ "--ref-chip-color": refColor(target.resourceType) } as CSSProperties}
        // itemTemplate narrows the picker's value type, which excludes the null of an empty single pick.
        value={(asTokens ? tokens : selected) as FilterOption[] | FilterOption | undefined}
        suggestions={suggestions}
        completeMethod={search}
        field="label"
        multiple={asTokens}
        dropdown={false}
        disabled={readOnly || creatingPlace}
        required={required}
        onFocus={onFocus}
        // A dependent list (rules: options RELATED_CONCEPTS) offers nothing while the field it
        // depends on is empty — say so rather than showing an unexplained empty list.
        placeholder={waitingForParent ? t("field.fillParentFirst") : undefined}
        // With a footer, the panel must open even on no match: that is exactly when « Nouveau »
        // is wanted.
        showEmptyMessage={footer != null || waitingForParent}
        emptyMessage={waitingForParent ? t("field.fillParentFirst") : t("field.noResult")}
        panelFooterTemplate={footer}
        panelStyle={target.resourceType === "concepts" ? panelStyle : undefined}
        itemTemplate={target.resourceType === "concepts" ? conceptItem : target.resourceType === "spatial-units" ? placeItem : undefined}
        selectedItemTemplate={renderToken}
        onChange={(e) => {
          if (asTokens && !multiple) {
            // The last pick is the value; none left means cleared.
            const picked = (e.value as FilterOption[] | null) ?? [];
            void commitPicked(picked.length > 0 ? picked[picked.length - 1] : null);
          } else {
            void commitPicked(e.value as FilterOption[] | FilterOption | null);
          }
        }}
      />
      {createConfig && (
        // Opens next to the picker, in an overlay that stops key events from reaching the edit
        // overlay's own Escape/Enter handling (see CreateEntityOverlay).
        <CreateEntityOverlay
          entityType={target.createEntityType!}
          anchor={createAnchor}
          organizationId={orgId}
          scope={context?.projectId ? { entityType: PROJECT_KEY, id: context.projectId } : undefined}
          prefill={context ? createConfig.list.createPrefillFrom?.(context) : undefined}
          onCreated={(id) => void onCreated(id)}
          onHide={() => setCreateAnchor(null)}
        />
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
  // Created inside a project: needs the edited entity's one.
  if (config.list.createProjectKind && !context?.projectId) return undefined;
  return config;
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
          // See IntegerRenderer: onValueChange would only fire after the editor has already committed.
          onChange={(e) => emit({ ...current, numericValue: e.value ?? null })}
        />
        {unit && <span className="p-inputgroup-addon">{unit}</span>}
      </div>
      <InputText
        value={current.comment ?? ""}
        disabled={readOnly}
        placeholder={t("field.comment")}
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
