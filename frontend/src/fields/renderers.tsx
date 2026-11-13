import {ConceptOptionItem, useFieldWidthCap} from "./ConceptOptionItem";
import {PlaceOptionItem} from "./PlaceOptionItem";
import {ReferencePickerSheet} from "./ReferencePickerSheet";
import {useIsMobile} from "../viewport";
import {type CSSProperties, useRef, useState} from "react";
import {InputText} from "primereact/inputtext";
import {InputTextarea} from "primereact/inputtextarea";
import {Checkbox} from "primereact/checkbox";
import {Dropdown} from "primereact/dropdown";
import {Calendar} from "primereact/calendar";
import {AutoComplete, type AutoCompleteCompleteEvent} from "primereact/autocomplete";
import {Button} from "primereact/button";
import {OverlayPanel} from "primereact/overlaypanel";
import {CreateEntityOverlay} from "../components/create/CreateEntityOverlay";
import {AnchoredFormOverlay} from "../components/layout/AnchoredFormOverlay";
import {CreatePersonForm} from "./CreatePersonForm";
import {useOpenEntity} from "../panels/pane/entityNavigation";
import {getEntityType} from "../entities/registry";
import {PROJECT_KEY} from "../entities/keys";
import type {Bound} from "../rules";
import type {FieldRendererProps} from "./registry";
import type {FieldEditContext} from "./editContext";
import {formatDateAnswer, parseDateAnswer} from "./dateAnswer";
import {SignedInputNumber} from "./SignedInputNumber";
import {refColor, YesNoTriState} from "./display";
import {
    createPlaceFromSuggestion,
    entityRowLabel,
    type FilterOption,
    isExternalSuggestion,
    type OptionList,
    optionSourceFor,
    personNameFromQuery,
    type ReferenceTarget,
    referenceTargetOf,
} from "./optionSources";
import {t} from "../i18n";
import {calendarDateFormat, calendarHourFormat} from "../i18n/dates";
import {useNotify} from "../notify/NotifyProvider";
import {messageForError} from "../api/errors";

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

function SingleLineTextRenderer({ value, readOnly, required, onChange }: Readonly<FieldRendererProps>) {
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

function TextAreaRenderer({ value, readOnly, required, onChange }: Readonly<FieldRendererProps>) {
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

// A geometry's WKT, in the SRID it carries: displayed, never edited (the server sends the field read-only).
const GEOMETRY_PREVIEW_LENGTH = 20;

// A geometry's WKT can run to thousands of characters: the field shows its first few, the rest opens in an overlay.
export function GeometryRenderer({ value }: Readonly<FieldRendererProps>) {
  const overlayRef = useRef<OverlayPanel>(null);
  const wkt = typeof value === "string" && value.length > 0 ? value : null;
  if (!wkt) return <span>—</span>;
  if (wkt.length <= GEOMETRY_PREVIEW_LENGTH) return <code className="field-geometry">{wkt}</code>;
  return (
    <>
      <button type="button" className="field-geometry field-geometry-preview" onClick={(e) => overlayRef.current?.toggle(e)}>
        {wkt.slice(0, GEOMETRY_PREVIEW_LENGTH)}…
      </button>
      <OverlayPanel ref={overlayRef} className="field-geometry-overlay">
        <code className="field-geometry">{wkt}</code>
      </OverlayPanel>
    </>
  );
}

export function IntegerRenderer({ field, value, readOnly, required, onChange }: Readonly<FieldRendererProps>) {
  const numberValue = typeof value === "number" ? value : null;
  return (
    <SignedInputNumber
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

export function DecimalRenderer({ field, value, readOnly, required, onChange }: Readonly<FieldRendererProps>) {
  const numberValue = typeof value === "number" ? value : null;
  return (
    <SignedInputNumber
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

export function DateRenderer({ field, value, readOnly, required, onChange, bounds }: Readonly<FieldRendererProps>) {
  const showTime = field.constraints?.showTime === true;
  // A date field saves the instant its value changes (commitsImmediately), but PrimeReact reports a
  // valid partial parse on every keystroke ("12/03/20" is year 20): typed text is only held here
  // and committed when the input is left or Enter is pressed. A pick in the panel still commits at once.
  const typed = useRef<Date | null | undefined>(undefined);
  const commitTyped = () => {
    if (typed.current === undefined) return;
    const date = typed.current;
    typed.current = undefined;
    onChange(date ? formatDateAnswer(date, showTime) : null);
  };
  return (
    // role="presentation": the wrapper only catches the Enter typed in the calendar's input.
    <span role="presentation" style={{ display: "contents" }} onKeyDown={(e) => e.key === "Enter" && commitTyped()}>
      <Calendar
        value={parseDateAnswer(value, showTime)}
        disabled={readOnly}
        required={required}
        // Days outside another field's date (closing before opening…) can't be picked; the overlay
        // still refuses a typed-in one (CellEditOverlay's bound check).
        minDate={dayBound(bounds?.min, showTime, +1)}
        maxDate={dayBound(bounds?.max, showTime, -1)}
        dateFormat={calendarDateFormat()}
        showTime={showTime}
        hourFormat={calendarHourFormat()}
        // The panel lives inside the edit overlay's own fixed box instead of being appended to
        // <body>, where it grew the document past the 100vh shell (a page scrollbar) and landed
        // below the fold.
        appendTo="self"
        onBlur={commitTyped}
        onChange={(e) => {
          const date = (e.value as Date | null) ?? null;
          // React reports the typed-in text as an "input" event; a pick in the panel is a click/keydown.
          if (e.originalEvent?.type === "input" || e.originalEvent?.type === "change") {
            typed.current = date;
            return;
          }
          typed.current = undefined;
          onChange(date ? formatDateAnswer(date, showTime) : null);
        }}
      />
    </span>
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
// A person awaiting the creation of their account says so beside their name.
const personItem = (o: FilterOption) => (
  <span className="person-option-item">
    {o.label}
    {o.accountPending && <small className="person-option-pending">{t("field.personAccountPending")}</small>}
  </span>
);

function optionItemTemplate(resourceType: string) {
  if (resourceType === "concepts") return conceptItem;
  if (resourceType === "places") return placeItem;
  if (resourceType === "persons") return personItem;
  return undefined;
}

// A declared option matches a search on its label or, for a concept, on one of its alt labels — as
// the thesaurus search of any other concept picker does.
function declaredOptionMatches(option: FilterOption, needle: string): boolean {
  const labels = [option.label, ...(option.concept?.altLabels ?? [])];
  return labels.some((label) => label.toLowerCase().includes(needle));
}

type RefPick = FilterOption[] | FilterOption | null;
type OptionLoader = (q?: string) => Promise<FilterOption[]>;
type OpenEntity = ReturnType<typeof useOpenEntity>;
type CreateConfig = ReturnType<typeof creatableConfig>;
type OpenNew = (anchor: HTMLElement | null) => void;

// What the picker searches: the field's declared options, filtered locally, or its option source.
function loadOptionsOf(
  { field, context, optionsContext, declaredOptions, placeContext }: FieldRendererProps,
  orgId: number | undefined,
): OptionLoader | null {
  if (declaredOptions) {
    return async (q?: string) => {
      const needle = (q ?? "").trim().toLowerCase();
      return declaredOptions.filter((option) => needle === "" || declaredOptionMatches(option, needle));
    };
  }
  if (orgId == null) return null;
  return optionSourceFor(field, orgId, context?.projectId, { valueConceptId: context?.typeConceptId, optionsContext, placeContext });
}

// The picked values of the answer, as the picker's options: a list for a multiple picker, one option or null otherwise.
function selectedOptionsOf(value: unknown, multiple: boolean): RefPick {
  if (multiple) {
    return Array.isArray(value)
      ? value.filter((v): v is ResourceRefLike | ResolvedResourceLike => isResourceRef(v) || isResolvedResource(v)).map(toOption)
      : [];
  }
  return isResourceRef(value) || isResolvedResource(value) ? toOption(value) : null;
}

// The picked values as a list, whatever the picker's multiplicity.
function asOptionList(selected: RefPick): FilterOption[] {
  if (selected == null) return [];
  return Array.isArray(selected) ? selected : [selected];
}

// Some sources searched without their filter: say which field to fill (or what is wrong with its place).
function narrowHintOf(placeContext: FieldRendererProps["placeContext"], fieldLabelOf: FieldRendererProps["fieldLabelOf"]): string {
  const named = (ids: string[]) => ids.map((id) => `« ${fieldLabelOf?.(id) ?? id} »`).join(", ");
  const parents = Object.entries(placeContext?.deps ?? {});
  const emptyParents = parents.filter(([, placeId]) => placeId == null).map(([fieldId]) => fieldId);
  return emptyParents.length > 0
    ? t("field.fillToNarrow", { field: named(emptyParents) })
    : t("field.cannotNarrow", { field: named(parents.map(([fieldId]) => fieldId)) });
}

// The « Nouveau » button's label: the entity it creates, or a person.
function newLabelOf(createConfig: CreateConfig, personProjectId: string | undefined): string | undefined {
  if (createConfig) return t("field.newLower", { label: createConfig.labels.singular.toLowerCase() });
  if (personProjectId) return t("field.newPerson");
  return undefined;
}

// What « Nouveau » opens: the entity creation form, the person form, or nothing.
function openNewOf(
  createConfig: CreateConfig,
  personProjectId: string | undefined,
  setCreateAnchor: (anchor: HTMLElement | null) => void,
  setPersonAnchor: (anchor: HTMLElement | null) => void,
): OpenNew | undefined {
  if (createConfig) return setCreateAnchor;
  if (personProjectId) return setPersonAnchor;
  return undefined;
}

// Picks a value: places suggested by an external source (INSEE, GéoPlateforme) are created first —
// once per organization, the server answers the existing one — then the answer is changed.
function useResourceRefCommit(
  { multiple, target, orgId, projectId, onChange }: {
    multiple: boolean;
    target: ReferenceTarget;
    orgId: number | undefined;
    projectId: FieldEditContext["projectId"] | undefined;
    onChange: FieldRendererProps["onChange"];
  },
) {
  const notify = useNotify();
  const [creatingPlace, setCreatingPlace] = useState(false);
  const toRef = (o: FilterOption): ResourceRefLike => ({ resourceId: o.id, resourceType: target.resourceType, label: o.label });

  function commit(next: RefPick) {
    if (multiple) {
      onChange(((next as FilterOption[] | null) ?? []).map(toRef));
    } else {
      const option = next as FilterOption | null;
      onChange(option ? toRef(option) : null);
    }
  }

  async function commitPicked(next: RefPick) {
    const picked = asOptionList(next);
    if (orgId == null || !picked.some(isExternalSuggestion)) {
      commit(next);
      return;
    }
    setCreatingPlace(true);
    try {
      const resolved = await Promise.all(
        picked.map((o) => (isExternalSuggestion(o) ? createPlaceFromSuggestion(orgId, o, projectId) : Promise.resolve(o))),
      );
      commit(Array.isArray(next) ? resolved : resolved[0] ?? null);
    } catch (error) {
      notify.error(messageForError(error, t("field.placeCreateFailed")));
    } finally {
      setCreatingPlace(false);
    }
  }

  return { commit, commitPicked, creatingPlace };
}

// The suggestion panel's footer: why the list could not be narrowed, and the « Nouveau » button.
function ResourceRefFooter({ hint, openNew, newLabel, hide, anchorOf }: Readonly<{
  hint: string | undefined;
  openNew: OpenNew | undefined;
  newLabel: string | undefined;
  hide: () => void;
  anchorOf: () => HTMLElement | null;
}>) {
  return (
    <div className="resource-ref-create-footer">
      {hint && <small className="resource-ref-narrow-hint">{hint}</small>}
      {openNew && (
        <Button
          type="button"
          text
          size="small"
          icon="bi bi-plus-lg"
          label={newLabel}
          onClick={() => {
            hide();
            openNew(anchorOf());
          }}
        />
      )}
    </div>
  );
}

// A picked entity's chip label: it opens the entity's fiche.
function ResourceRefTokenLink({ option, entityType, openEntity }: Readonly<{
  option: FilterOption;
  entityType: string;
  openEntity: NonNullable<OpenEntity>;
}>) {
  const entityIcon = getEntityType(entityType)?.icon;
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
        openEntity(entityType, option.id);
      }}
    >
      {entityIcon && <i className={`${entityIcon} ref-chip-icon`} aria-hidden="true" />}
      {option.label}
    </span>
  );
}

// The forms « Nouveau » opens next to the picker.
function ResourceRefCreateOverlays({ target, context, orgId, createConfig, createAnchor, onCreated, onHideCreate, personProjectId, personAnchor, personQuery, onPersonCreated, onHidePerson }: Readonly<{
  target: ReferenceTarget;
  context: FieldEditContext | undefined;
  orgId: number | undefined;
  createConfig: CreateConfig;
  createAnchor: HTMLElement | null;
  onCreated: (id: string | number) => void;
  onHideCreate: () => void;
  personProjectId: string | undefined;
  personAnchor: HTMLElement | null;
  personQuery: string;
  onPersonCreated: (person: FilterOption) => void;
  onHidePerson: () => void;
}>) {
  return (
    <>
      {createConfig && (
        // Opens next to the picker, in an overlay that stops key events from reaching the edit
        // overlay's own Escape/Enter handling (see CreateEntityOverlay).
        <CreateEntityOverlay
          entityType={target.createEntityType!}
          anchor={createAnchor}
          organizationId={orgId}
          scope={context?.projectId ? { entityType: PROJECT_KEY, id: context.projectId } : undefined}
          prefill={context ? createConfig.list.createPrefillFrom?.(context) : undefined}
          onCreated={onCreated}
          onHide={onHideCreate}
        />
      )}
      {personProjectId && (
        <AnchoredFormOverlay anchor={personAnchor} ariaLabel={t("person.create.title")} onHide={onHidePerson}>
          {(close) => (
            <CreatePersonForm
              projectId={personProjectId}
              initial={personNameFromQuery(personQuery)}
              onCreated={onPersonCreated}
              onCancel={close}
            />
          )}
        </AnchoredFormOverlay>
      )}
    </>
  );
}

// A dependent list (rules: options RELATED_CONCEPTS) offers nothing while the field it depends on is empty.
function isWaitingForParent(optionsContext: FieldRendererProps["optionsContext"]): boolean {
  return optionsContext?.kind === "RELATED_CONCEPTS" && optionsContext.relatedTo == null;
}

// The entity type a picked value links to: only a value this app has a fiche for is a link.
function linkEntityTypeOf(target: ReferenceTarget): string | undefined {
  if (!target.entityType) return undefined;
  return getEntityType(target.entityType) ? target.entityType : undefined;
}

// A single reference to an entity with a fiche is shown as a token too, not as the input's text:
// the token is what links to the fiche (a click in the input means "edit the text"). Picking a
// suggestion replaces it, its × clears the field.
function isTokenPicker(multiple: boolean, linkEntityType: string | undefined, openEntity: OpenEntity): boolean {
  if (multiple) return true;
  return linkEntityType != null && openEntity != null;
}

// The query a focused picker searches with. A single picker whose input still shows the picked value's
// label searches with "": that label would narrow the list to the value already there.
function focusQuery(text: string, selected: RefPick, tokenPicker: boolean): string {
  if (tokenPicker || Array.isArray(selected)) return text;
  return text === selected?.label ? "" : text;
}

// The pick a change of the picker means: for a single token picker, its last token (none left: cleared).
function pickOfChange(next: RefPick, tokenPicker: boolean, multiple: boolean): RefPick {
  if (!tokenPicker || multiple) return next;
  const picked = (next as FilterOption[] | null) ?? [];
  return picked.length > 0 ? picked[picked.length - 1] : null;
}

// The pick the phone drawer commits: its whole selection, or its first option for a single picker.
function sheetPickOf(next: FilterOption[], multiple: boolean): RefPick {
  return multiple ? next : (next[0] ?? null);
}

// What the AutoComplete shows: the tokens, or the typed text, or the picked option.
function pickerValueOf(tokenPicker: boolean, selected: RefPick, typed: string | null) {
  if (tokenPicker) return asOptionList(selected);
  return typed ?? selected;
}

// The suggestion panel's footer template, when it has something to show (a hint or « Nouveau »).
function footerTemplateOf(hint: string | undefined, openNew: OpenNew | undefined, newLabel: string | undefined, anchorOf: () => HTMLElement | null) {
  if (!openNew && !hint) return undefined;
  return (_: unknown, hide: () => void) => (
    <ResourceRefFooter hint={hint} openNew={openNew} newLabel={newLabel} hide={hide} anchorOf={anchorOf} />
  );
}

// The template of a picked token: a link to its fiche, for an entity the app can open.
function tokenTemplateOf(tokenPicker: boolean, linkEntityType: string | undefined, openEntity: OpenEntity) {
  if (!tokenPicker || !linkEntityType || !openEntity) return undefined;
  return (item: unknown) => <ResourceRefTokenLink option={item as FilterOption} entityType={linkEntityType} openEntity={openEntity} />;
}

// The search behind the picker: its suggestions, the sources it could not narrow and the last text searched.
function useResourceRefSearch(loadOptions: OptionLoader | null, capTo: (e: AutoCompleteCompleteEvent) => void) {
  const [suggestions, setSuggestions] = useState<FilterOption[]>([]);
  // Sources of the last search that could not be narrowed because the field they depend on is empty.
  const [unnarrowedSources, setUnnarrowedSources] = useState<string[]>([]);
  // The last text searched: what « Nouvelle personne » is prefilled with.
  const lastQuery = useRef("");

  async function search(e: AutoCompleteCompleteEvent) {
    if (!loadOptions) return;
    lastQuery.current = e.query;
    capTo(e);
    const found = (await loadOptions(e.query)) as OptionList;
    setUnnarrowedSources(found.unnarrowedSources ?? []);
    setSuggestions(found);
  }

  return { suggestions, unnarrowedSources, setUnnarrowedSources, lastQuery, search };
}

// What is typed in a single picker's input and what a change of the picker commits. PrimeReact reports
// typed text through onChange as a plain string, which is not a pick and must never reach the answer.
function usePickerChange(tokenPicker: boolean, multiple: boolean, commitPicked: (next: RefPick) => Promise<void>) {
  const [typed, setTyped] = useState<string | null>(null);

  function onTyped(text: string) {
    setTyped(text);
    // An empty string means the input was cleared: commit null so the required-field
    // check can fire (and the field can actually be cleared).
    if (text === "") void commitPicked(null);
  }

  function onPickerChange(next: unknown) {
    if (typeof next === "string") {
      onTyped(next);
      return;
    }
    setTyped(null);
    void commitPicked(pickOfChange(next as RefPick, tokenPicker, multiple));
  }

  return { typed, clearTyped: () => setTyped(null), onPickerChange };
}

// « Nouveau » creates the target in the edited entity's project and picks it straight away — it is added
// to a multi-valued answer, it replaces a single one. A person is never created as an entity of the
// registry: « Nouvelle personne » creates someone awaiting their account, member of the edited entity's
// project — so only inside a project.
function useResourceRefCreation(
  { target, context, readOnly, multiple, selectedList, commit }: {
    target: ReferenceTarget;
    context: FieldEditContext | undefined;
    readOnly: boolean | undefined;
    multiple: boolean;
    selectedList: FilterOption[];
    commit: (next: RefPick) => void;
  },
) {
  // Where the « Nouveau » form is open (the picker itself), or null.
  const [createAnchor, setCreateAnchor] = useState<HTMLElement | null>(null);
  // Where the « Nouvelle personne » form is open (the picker itself), or null.
  const [personAnchor, setPersonAnchor] = useState<HTMLElement | null>(null);
  const createConfig = readOnly ? undefined : creatableConfig(target, context);
  const personProjectId = readOnly || target.resourceType !== "persons" ? undefined : context?.projectId;

  function pickCreated(created: FilterOption) {
    commit(multiple ? [...selectedList.filter((o) => o.id !== created.id), created] : created);
  }

  async function onCreated(id: string | number) {
    setCreateAnchor(null);
    let label = String(id);
    try {
      label = entityRowLabel(await createConfig!.api.get(id));
    } catch {
      // The entity exists; only its label could not be read back. The id stands in for it until
      // the edit reloads the row.
    }
    pickCreated({ id: String(id), label });
  }

  function onPersonCreated(person: FilterOption) {
    setPersonAnchor(null);
    pickCreated(person);
  }

  return {
    createConfig,
    personProjectId,
    createAnchor,
    personAnchor,
    closeCreate: () => setCreateAnchor(null),
    closePerson: () => setPersonAnchor(null),
    onCreated,
    onPersonCreated,
    newLabel: newLabelOf(createConfig, personProjectId),
    openNew: openNewOf(createConfig, personProjectId, setCreateAnchor, setPersonAnchor),
  };
}

function ResourceRefRenderer(props: FieldRendererProps & { multiple: boolean }) {
  const { field, value, readOnly, required, onChange, organizationId, context, optionsContext, placeContext, fieldLabelOf, multiple } = props;
  const orgId = organizationId ?? context?.organizationId;
  const loadOptions = loadOptionsOf(props, orgId);
  const autoCompleteRef = useRef<AutoComplete>(null);
  const mobile = useIsMobile();
  const { capTo, panelStyle } = useFieldWidthCap();
  const target = referenceTargetOf(field);
  const waitingForParent = isWaitingForParent(optionsContext);
  const openEntity = useOpenEntity();
  const linkEntityType = linkEntityTypeOf(target);
  const tokenPicker = isTokenPicker(multiple, linkEntityType, openEntity);
  const selected = selectedOptionsOf(value, multiple);
  const selectedList = asOptionList(selected);

  const { commit, commitPicked, creatingPlace } = useResourceRefCommit({ multiple, target, orgId, projectId: context?.projectId, onChange });
  const { suggestions, unnarrowedSources, setUnnarrowedSources, lastQuery, search } = useResourceRefSearch(loadOptions, capTo);
  const { typed, clearTyped, onPickerChange } = usePickerChange(tokenPicker, multiple, commitPicked);
  const creation = useResourceRefCreation({ target, context, readOnly, multiple, selectedList, commit });

  // PrimeReact only searches once something is typed, so a freshly-focused picker is a blank box
  // with no indication that there is anything to pick. Searching on focus gives the user the
  // starting list immediately — every option source answers an empty query with its first page.
  // `search` is AutoComplete's own imperative entry point. The source must not be "input": that is
  // the only one it refuses a blank query for. "dropdown" is the typed value that means "opened
  // rather than typed into", which is exactly this case (there is no dropdown button).
  function onFocus(e: React.FocusEvent<HTMLInputElement>) {
    autoCompleteRef.current?.search(e, focusQuery(e.target.value ?? "", selected, tokenPicker), "dropdown");
  }

  const narrowHint = unnarrowedSources.length > 0 ? narrowHintOf(placeContext, fieldLabelOf) : undefined;
  const footer = footerTemplateOf(narrowHint, creation.openNew, creation.newLabel, () => autoCompleteRef.current?.getElement() ?? null);
  const refStyle = { "--ref-chip-color": refColor(target.resourceType) } as CSSProperties;
  const fillParentFirst = waitingForParent ? t("field.fillParentFirst") : undefined;
  const disabled = Boolean(readOnly) || creatingPlace;
  const itemTemplate = optionItemTemplate(target.resourceType);

  // Phone width: the field is a button opening a drawer (a dropdown under the keyboard is unusable).
  const picker = mobile ? (
    <ReferencePickerSheet
      multiple={multiple}
      selected={selectedList}
      disabled={disabled}
      placeholder={fillParentFirst}
      emptyMessage={fillParentFirst}
      loadOptions={loadOptions}
      itemTemplate={itemTemplate}
      onSearched={(found) => setUnnarrowedSources(found.unnarrowedSources ?? [])}
      hint={narrowHint}
      onCommit={(next) => void commitPicked(sheetPickOf(next, multiple))}
      onNew={creation.openNew}
      newLabel={creation.newLabel}
      style={refStyle}
    />
  ) : (
    <AutoComplete
      ref={autoCompleteRef}
      // Picked tokens take their type's colour, the same as the read-only chips (fields/display.tsx).
      className="resource-ref-picker"
      style={refStyle}
      // itemTemplate narrows the picker's value type, which excludes the null of an empty single pick.
      value={pickerValueOf(tokenPicker, selected, typed) as FilterOption[] | FilterOption | undefined}
      suggestions={suggestions}
      completeMethod={search}
      field="label"
      multiple={tokenPicker}
      dropdown={false}
      disabled={disabled}
      required={required}
      onFocus={onFocus}
      onBlur={clearTyped}
      // A dependent list offers nothing while the field it depends on is empty — say so rather than
      // showing an unexplained empty list.
      placeholder={fillParentFirst}
      // With a footer, the panel must open even on no match: that is exactly when « Nouveau » is wanted.
      showEmptyMessage={footer != null || waitingForParent}
      emptyMessage={fillParentFirst ?? t("field.noResult")}
      panelFooterTemplate={footer}
      panelStyle={target.resourceType === "concepts" ? panelStyle : undefined}
      itemTemplate={itemTemplate}
      selectedItemTemplate={tokenTemplateOf(tokenPicker, linkEntityType, openEntity)}
      onChange={(e) => onPickerChange(e.value)}
    />
  );

  return (
    <>
      {picker}
      <ResourceRefCreateOverlays
        target={target}
        context={context}
        orgId={orgId}
        createConfig={creation.createConfig}
        createAnchor={creation.createAnchor}
        onCreated={(id) => void creation.onCreated(id)}
        onHideCreate={creation.closeCreate}
        personProjectId={creation.personProjectId}
        personAnchor={creation.personAnchor}
        personQuery={lastQuery.current}
        onPersonCreated={creation.onPersonCreated}
        onHidePerson={creation.closePerson}
      />
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

export function SelectOneRefRenderer(props: Readonly<FieldRendererProps>) {
  return <ResourceRefRenderer {...props} multiple={false} />;
}

export function SelectManyRefRenderer(props: Readonly<FieldRendererProps>) {
  return <ResourceRefRenderer {...props} multiple />;
}

// Kept under their historical names: tests and callers reference them.
export const SelectOneConceptRenderer = SelectOneRefRenderer;
export const SelectManyConceptRenderer = SelectManyRefRenderer;
export const SelectOneSpatialUnitRenderer = SelectOneRefRenderer;

// BOOLEAN: a checkbox, where unchecked reads as "no". A field that allows "no answer" has a third
// state, so it gets PrimeReact's TriStateCheckbox: not specified ("?"), yes (ticked), no (empty).
export function BooleanRenderer({ field, value, readOnly, required, onChange }: Readonly<FieldRendererProps>) {
  const current = typeof value === "boolean" ? value : null;

  function resolveLabel() {
    if (current === true) return t("field.yes");
    if (current === false) return t("field.no");
    return t("field.notSpecified");
  }

  if (field.constraints?.allowNoAnswer) {
    // The renderer only lives in the editing overlay: the current state is named beside the box there,
    // while a read-only display (fields/display.tsx) shows the bare disabled checkbox.
    const label = resolveLabel()
    return (
      // The gap is inline, like the overlay's padding: it must not depend on a stylesheet being reloaded.
      <span className="boolean-renderer boolean-renderer-tristate" style={{ display: "inline-flex", alignItems: "center", gap: "0.6rem" }}>
        <YesNoTriState value={current} disabled={readOnly} onChange={onChange} />
        <span className="boolean-renderer-label" style={{ marginLeft: "0.4rem" }}>{label}</span>
      </span>
    );
  }
  return (
    <Checkbox
      className="boolean-renderer"
      checked={current === true}
      disabled={readOnly}
      required={required}
      onChange={(e) => onChange(e.checked === true)}
    />
  );
}

interface MeasurementValue {
  numericValue?: number | null;
  symbol?: string | null;
  normalizedValue?: number | null;
  comment?: string | null;
}

// The measurement form's layout is inline: it must not depend on a stylesheet having been reloaded.
// A label sits above its field (a column), the value and the unit side by side, the comment below.
const MEASUREMENT_FORM: CSSProperties = { display: "flex", flexDirection: "column", gap: "0.75rem", minWidth: "16rem" };
const MEASUREMENT_ROW: CSSProperties = { display: "flex", gap: "0.75rem", alignItems: "flex-end" };
const MEASUREMENT_FIELD: CSSProperties = { display: "flex", flexDirection: "column", gap: "0.25rem", minWidth: 0 };
const MEASUREMENT_LABEL: CSSProperties = {
  fontSize: "0.75rem",
  letterSpacing: "0.04em",
  textTransform: "uppercase",
  color: "var(--text-color-secondary, #6b7280)",
};

// MEASUREMENT: a number in a unit plus an optional comment. The unit is a selector over the units the
// field's configuration offers (constraints.units), disabled when there is only one; the picked symbol
// travels with the number. Emits the MeasurementRef shape the PATCH accepts
// ({ numericValue, symbol, comment }), or null once the number and the comment are both empty.
export function MeasurementRenderer({ field, value, readOnly, required, onChange }: Readonly<FieldRendererProps>) {
  const current = (value != null && typeof value === "object" ? value : {}) as MeasurementValue;
  const units = field.constraints?.units ?? null;
  // Before any number is entered there is no answer to carry the choice: keep it here until there is.
  const [picked, setPicked] = useState<string | null>(null);
  const unit = current.symbol ?? picked ?? field.constraints?.unit ?? null;

  function emit(next: MeasurementValue) {
    const empty = next.numericValue == null && !next.comment;
    onChange(empty ? null : { numericValue: next.numericValue ?? null, symbol: unit, comment: next.comment || null });
  }

  const inputNumber = (
    <SignedInputNumber
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
  );

  function resolveOffered() {
    if (units && units.length > 0) return units;
    if (unit) return [unit];
    return [];
  }

  // The unit is always a selector over the units the field's configuration offers; with a single one
  // it is shown disabled.
  const offered = resolveOffered();
  const onlyOne = offered.length <= 1;

  // A small form: the value and its unit on the first line, the comment on the second.
  return (
    <div className="measurement-renderer" style={MEASUREMENT_FORM}>
      <div className="measurement-row" style={MEASUREMENT_ROW}>
        <label className="measurement-field measurement-value" style={{ ...MEASUREMENT_FIELD, flex: "1 1 auto" }}>
          <span className="measurement-label" style={MEASUREMENT_LABEL}>{t("field.value")}</span>
          {inputNumber}
        </label>
        {offered.length > 0 && (
          <label className="measurement-field measurement-unit-field" style={{ ...MEASUREMENT_FIELD, flex: "0 0 8rem" }}>
            <span className="measurement-label" style={MEASUREMENT_LABEL}>{t("field.unit")}</span>
            <Dropdown
              className="measurement-unit"
              value={unit}
              options={offered}
              disabled={readOnly || onlyOne}
              aria-label={t("field.unit")}
              onChange={(e) => {
                const next = e.value as string;
                setPicked(next);
                if (current.numericValue != null || current.comment) {
                  onChange({ numericValue: current.numericValue ?? null, symbol: next, comment: current.comment || null });
                }
              }}
            />
          </label>
        )}
      </div>
      <label className="measurement-field" style={MEASUREMENT_FIELD}>
        <span className="measurement-label" style={MEASUREMENT_LABEL}>{t("field.comment")}</span>
        <InputText
          value={current.comment ?? ""}
          disabled={readOnly}
          onChange={(e) => emit({ ...current, comment: e.target.value })}
        />
      </label>
    </div>
  );
}

export function FallbackRenderer({ field, value }: Readonly<FieldRendererProps>) {
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
