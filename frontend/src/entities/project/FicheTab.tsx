import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Menu } from "primereact/menu";
import { Message } from "primereact/message";
import { Panel } from "primereact/panel";
import { Toolbar } from "primereact/toolbar";
import { Tooltip } from "primereact/tooltip";
import { ApiError } from "../../api/client";
import { commitsImmediately, sameValue } from "../../fields/commit";
import { getFieldRenderer } from "../../fields/registry";
import { resolveValueBinding, toAnswerInput, type AnswerInputBody } from "../../fields/types";
import type { FieldResource } from "../../fields/types";
import { useCanEdit } from "../../panels/writeMode";
import { getProjectHistory, type ProjectHistoryEntry } from "./history";
import { parseLayout, panelLabel, type FormLayoutCol } from "./form";
import { getProjectTypes } from "./projectTypes";
import { patchProject, type ProjectPatch } from "./api";
import type { ProjectDetail } from "./types";

// The fiche, schema-driven off GET /api/v1/organizations/{id}/project-types — it renders every
// panel/row/field ActionUnit.DETAILS_FORM's layout defines (4 panels, 33 fields), same as JSF's
// "Détails" tab (panel/tab/detailsTab.xhtml → pages/shared/form/customForm.xhtml).
//
// All 33 of those fields have a value on the wire: GET /api/v1/projects/{id}?fields=all projects
// the whole catalog into `answers` (entities/project/config.tsx asks for it), and PATCH writes any
// of them back through the same `answers` map the list's cell editor uses
// (ProjectApiService.applyAnswerPatch).
//
// EDIT MODEL — the fiche has no Modifier/Enregistrer/Annuler of its own, exactly like JSF's:
// - WHETHER a field is editable is the app's global read/write switch (FlowBean.isWriteMode, the
//   topbar toggle) AND the user's own right on this project (_permissions.canEdit, the same
//   permission canUserEditUnit() checks — see headerEditControls.xhtml and bug #448). In JSF the
//   switch is literally what FlowBean.getInPlaceFieldMode() reads to hand every field "input" or
//   "output"; here it arrives as WriteModeProvider (panels/writeMode.tsx).
// - WHEN an edit is persisted is per field, with no Save button, matching both JSF's own
//   autosaving inplace components (pages/shared/inplace/*: a debounced ajax on keyup/blur) and the
//   list's cell editor. The commit boundaries are literally shared with that editor
//   (fields/commit.ts): a picker or date saves the moment it changes, a text/number input saves on
//   blur or Enter, and Escape reverts the field.
//
// Still deliberately absent, all for want of a REST equivalent rather than by oversight:
// - "Incertain" per-field toggle (panelField.xhtml's selectBooleanCheckbox → formContext
//   .toggleUncertainty): no uncertainty flag on FieldResource or in AnswerInput.
// - "Ajouter une mesure" / remove-field on a canUserAddFields panel (NewFieldManagerBean): no
//   endpoint to create a CustomFieldMeasurement, and Project's layout marks no panel as
//   canUserAddFields anyway.
// - "Validate" (INCOMPLETE → COMPLETE → VALIDATED, ActionUnitService.toggleValidated) and
//   prev/next (findNextByInstitution/findPreviousByInstitution): no endpoint for either.

// The one field of the 33 that PATCH cannot take through `answers`: coerceScalarAnswer throws a
// 400 for CustomFieldSelectMultipleSpatialUnitTree. It has a flat alias on ProjectPatchRequest
// instead, so the save below routes it there. Keyed by answerType, not by field id, because the
// restriction is the answerType's, not this one field's.
const SPATIAL_CONTEXT_ANSWER_TYPE = "SELECT_MULTIPLE_SPATIAL_UNIT_TREE";

interface ProjectFicheTabProps {
  entity: ProjectDetail;
  onSaved: () => void;
}

export function ProjectFicheTab({ entity, onSaved }: ProjectFicheTabProps) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const canEdit = useCanEdit(entity);

  const typesQuery = useQuery({
    queryKey: ["project-types", organizationIdRaw],
    queryFn: () => getProjectTypes(organizationIdRaw as string),
    enabled: organizationIdRaw != null,
  });

  const historyQuery = useQuery({
    queryKey: ["project-history", entity.id],
    queryFn: () => getProjectHistory(entity.id),
  });

  const fields = typesQuery.data?.fields;
  const panels = useMemo(
    () => (typesQuery.data ? parseLayout(typesQuery.data.layoutJson) : []),
    [typesQuery.data],
  );

  // ProjectFieldConfigResource.active — JSF's own isColumnEnabled(field) gate
  // (EntityFormContext:237), which hides a field deactivated for this project type unless it
  // already holds an answer. Same escape hatch here: a deactivated field with a value stays
  // visible rather than hiding data the user can see in JSF.
  const inactiveFieldIds = useMemo(() => {
    const set = new Set<string>();
    for (const config of typesQuery.data?.fieldConfigs ?? []) {
      if (!config.active) set.add(config.field);
    }
    return set;
  }, [typesQuery.data]);

  const save = useCallback(
    async (field: FieldResource, value: unknown) => {
      await patchProject(entity.id, buildPatch({ [field.id]: value }, { [field.id]: field }));
      onSaved();
    },
    [entity.id, onSaved],
  );

  return (
    <div className="project-fiche-tab">
      {/* No edit/save toolbar: the app's own read/write switch decides, and each field persists
          itself. The identifier and category chips keep their explicit pencil/apply/cancel, in
          ProjectDetailHeader — that is what JSF does too (headerEditControls.xhtml). */}
      {organizationIdRaw == null && (
        <Message severity="warn" text="Organisation inconnue : impossible de charger le formulaire" />
      )}
      {typesQuery.isLoading && <div>Chargement du formulaire…</div>}
      {typesQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {fields &&
        panels.map((panel, panelIndex) => (
          // customForm.xhtml wraps each CustomFormPanelUiDto in a toggleable p:panel with the
          // sia-form-panel class, not a fieldset.
          <Panel
            key={panelIndex}
            header={panelLabel(panel.name)}
            toggleable
            className={`sia-form-panel ${panel.className ?? ""}`.trim()}
          >
            {panel.rows.map((row, rowIndex) => (
              <div key={rowIndex} className="project-fiche-tab-row">
                {row.columns.map((col, colIndex) => (
                  <FormField
                    key={colIndex}
                    col={col}
                    fields={fields}
                    entity={entity}
                    canEdit={canEdit}
                    organizationId={organizationId}
                    inactiveFieldIds={inactiveFieldIds}
                    onSave={save}
                  />
                ))}
              </div>
            ))}
            {panel.rows.length === 0 && <i>Aucun champ</i>}
          </Panel>
        ))}

      <FicheFooter entity={entity} history={historyQuery.data} />
    </div>
  );
}

/**
 * singleUnitPanel.xhtml's own p:toolbar class="panel-footer": created on/by, modified on/by,
 * validated, contributors. Everything except the validation DATE is derivable from
 * GET /api/v1/projects/{id}/history, which returns the full revision list — the oldest revision is
 * the creation, the newest the last modification, and the distinct authors are the contributors.
 * ActionUnit.validatedAt has no place on ProjectResource, so the validated line states the status
 * without a date rather than inventing one.
 */
function FicheFooter({ entity, history }: { entity: ProjectDetail; history?: ProjectHistoryEntry[] }) {
  if (!history || history.length === 0) return null;

  // ProjectHistoryControllerApi returns newest-first (the fiche header's "last modified" line has
  // always read data[0]); the creation is therefore the last element.
  const latest = history[0];
  const created = history[history.length - 1];
  const contributors = [
    ...new Set(history.map((h) => authorName(h)).filter((name): name is string => name != null)),
  ];

  return (
    <Toolbar
      className="panel-footer"
      start={
        <div className="project-fiche-tab-footer">
          <FooterLine label="Créé le" date={created.revisionDate} author={authorName(created)} />
          <FooterLine label="Modifié le" date={latest.revisionDate} author={authorName(latest)} />
          {entity.validated === "VALIDATED" && (
            <small>
              <i>
                Validé <span className="panel-history-colored-span">oui</span>
              </i>
            </small>
          )}
          {contributors.length > 0 && (
            <small>
              <i>
                Contributeurs : <span className="panel-history-colored-span">{contributors.join(", ")}</span>
              </i>
            </small>
          )}
        </div>
      }
    />
  );
}

function FooterLine({ label, date, author }: { label: string; date: string; author?: string | null }) {
  return (
    <small>
      <i>
        {label} <span className="panel-history-colored-span">{formatDateTime(date)}</span>
        {author && (
          <>
            {" "}
            par <span className="panel-history-colored-span">{author}</span>
          </>
        )}
      </i>
    </small>
  );
}

function authorName(entry: ProjectHistoryEntry): string | null {
  const name = [entry.author?.name, entry.author?.lastname].filter(Boolean).join(" ");
  return name || null;
}

function FormField({
  col,
  fields,
  entity,
  canEdit,
  organizationId,
  inactiveFieldIds,
  onSave,
}: {
  col: FormLayoutCol;
  fields: Record<string, FieldResource>;
  entity: ProjectDetail;
  canEdit: boolean;
  organizationId?: number;
  inactiveFieldIds: Set<string>;
  onSave: (field: FieldResource, value: unknown) => Promise<void>;
}) {
  // The identifier column is marked readOnly + "d-none" in the real layout (ActionUnitDetailsForm)
  // because JSF edits it outside this grid, via the panel header's own affordance — same reason
  // ProjectDetailHeader's identifier control exists separately rather than as a form field here.
  if (col.fieldId == null || col.className?.includes("d-none")) return null;

  const fieldId = String(col.fieldId);
  const field = fields[fieldId];
  if (!field) return null;

  const stored = resolveValueBinding(field).read(entity);
  if (inactiveFieldIds.has(fieldId) && isEmptyValue(stored)) return null;

  return (
    <div className={`project-fiche-tab-col ${col.className ?? ""}`.trim()}>
      <AutosavingField
        // Remounting on a value change from elsewhere (another panel, a header edit) is deliberate:
        // it reseeds the baseline this field's "did it actually change?" check compares against.
        key={JSON.stringify(stored ?? null)}
        field={field}
        stored={stored}
        readOnly={!canEdit || col.isReadOnly}
        required={col.isRequired}
        organizationId={organizationId}
        onSave={onSave}
      />
    </div>
  );
}

/**
 * One form field that persists itself — the fiche's equivalent of the list's CellEditOverlay,
 * without the overlay: the widget is always on screen (disabled outside write mode), so there is
 * nothing to open or close, only a commit boundary to pick. Those boundaries come from
 * fields/commit.ts, shared with that editor.
 *
 * <p>Escape reverts to the stored value. A failed save keeps the edit on screen with the server's
 * own message (in particular the 409 on a duplicate identifier and the 400 on a bad number),
 * rather than silently discarding it.</p>
 */
function AutosavingField({
  field,
  stored,
  readOnly,
  required,
  organizationId,
  onSave,
}: {
  field: FieldResource;
  stored: unknown;
  readOnly: boolean;
  required: boolean;
  organizationId?: number;
  onSave: (field: FieldResource, value: unknown) => Promise<void>;
}) {
  const [draft, setDraft] = useState<unknown>(stored);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const draftRef = useRef<unknown>(stored);
  draftRef.current = draft;
  // The last value successfully persisted, which is what "unchanged" compares against — not
  // `stored`, which only catches up on the next refetch.
  const baselineRef = useRef<unknown>(stored);
  const savingRef = useRef(false);

  // A change from elsewhere (the panel refetching after another field's save) moves the baseline
  // with it, so this field doesn't then re-send a value it never owned.
  useEffect(() => {
    if (!savingRef.current && sameValue(draftRef.current, baselineRef.current)) {
      baselineRef.current = stored;
      setDraft(stored);
    }
  }, [stored]);

  const commit = useCallback(
    async (value: unknown) => {
      if (sameValue(value, baselineRef.current)) return;
      if (savingRef.current) return;
      // p:outputLabel indicateRequired + required="#{col.required}" is what stops this in JSF, on
      // the ajax submit that leaves the field. Clearing a required field has no valid target
      // value, so it is refused here rather than sent for the server to reject.
      if (required && isEmptyValue(value)) {
        setError("Ce champ est obligatoire");
        return;
      }
      savingRef.current = true;
      setSaving(true);
      setError(null);
      try {
        await onSave(field, value);
        baselineRef.current = value;
      } catch (e) {
        setError(e instanceof ApiError ? e.message : "La modification a échoué.");
      } finally {
        savingRef.current = false;
        setSaving(false);
      }
    },
    [field, onSave, required],
  );

  const immediate = commitsImmediately(field);

  function onChange(value: unknown) {
    setDraft(value);
    // A picker or a date yields one final value per interaction, so there is no "still typing"
    // state to wait through. A text or number input does — it commits on blur or Enter below.
    if (immediate) void commit(value);
  }

  function onBlur() {
    if (!immediate) void commit(draftRef.current);
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      setDraft(baselineRef.current);
      setError(null);
    } else if (e.key === "Enter" && !immediate) {
      e.preventDefault();
      void commit(draftRef.current);
    }
  }

  const Renderer = getFieldRenderer(field.answerType);
  const dirty = !sameValue(draft, stored);

  return (
    // panelField.xhtml's field-value-group (+ ui-fluid, which it drops only for the spatial tree
    // picker) wrapping a label row and the widget below it.
    <div
      className={`field-value-group${field.answerType === SPATIAL_CONTEXT_ANSWER_TYPE ? "" : " ui-fluid"}${
        dirty ? " sia-modified-field" : ""
      }${saving ? " sia-field-saving" : ""}`}
      onBlur={onBlur}
      onKeyDown={onKeyDown}
    >
      <FieldLabel field={field} required={required} />
      <div>
        <Renderer
          field={field}
          value={draft}
          readOnly={readOnly || saving}
          required={required}
          organizationId={organizationId}
          onChange={onChange}
        />
      </div>
      {error && <div className="field-value-error">{error}</div>}
    </div>
  );
}

/**
 * panelField.xhtml's header-toggle-container: the flat label button — the field's own icon
 * (CustomField.getIcon(), per type: "bi bi-alphabet" for TEXT, "bi bi-calendar" for DATETIME,
 * "bi bi-geo-alt" for a place, "sia-icon-opentheso" for a vocabulary field) plus the title,
 * ellipsised and marked with a "*" when required — with a tooltip carrying the full title, and an
 * overlay menu whose one applicable item links to the field's concept in the thesaurus.
 *
 * <p>Two things here are less obvious than they look:</p>
 * <ul>
 *   <li><strong>p-button-text, not p-button-flat.</strong> "flat" is PrimeFaces' name for this
 *   variant (.ui-button-flat); PrimeReact calls it .p-button-text and has no .p-button-flat at all.
 *   Using the wrong one left the button with lara's solid blue fill under a grey label — an
 *   unreadable field header. The SIAMOIS flat look is then restyled on top, in main-panel.css.</li>
 *   <li><strong>appendTo={document.body}</strong> pins where the popup lands. It is also
 *   PrimeReact's default (Portal falls back to document.body when appendTo is unset), but that
 *   default is overridable app-wide through PrimeReact.appendTo, and this menu must not end up
 *   inside the fiche: the fiche sits in the panel's own overflow:auto box, which would clip it.
 *   JSF's own p:menu is overlay="true" for the same reason, and CellEditOverlay portals to the
 *   body for the same reason again.</li>
 * </ul>
 *
 * <p>NOTE on "the menu does not open": if that is what you see in the running app, check the API
 * before this component. The menu exists only when FieldResource carries a conceptUri, and both
 * that property and `icon` were added to FieldResource recently — a server build predating them
 * serves neither, which shows up as a label with no icon AND a click that does nothing. Verify
 * with GET /api/v1/organizations/{id}/project-types.</p>
 *
 * <p>The menu is rendered only when the field HAS a conceptUri: JSF always renders it, but its
 * sole non-conditional item is that link, so a menu without one would open onto nothing. The
 * button then presents itself as non-interactive (no aria-haspopup, default cursor) rather than
 * inviting a click that does nothing.</p>
 */
function FieldLabel({ field, required }: { field: FieldResource; required: boolean }) {
  const menuRef = useRef<Menu>(null);
  const buttonId = `sia-field-label-${field.id}`;
  const hasThesaurusEntry = Boolean(field.conceptUri);

  return (
    <div className="header-toggle-container">
      <Tooltip target={`#${buttonId}`} content={field.label} position="left" />
      <Button
        id={buttonId}
        type="button"
        className={`p-button-text ellipsis-btn${required ? " required-btn" : ""}${
          hasThesaurusEntry ? " has-thesaurus-entry" : ""
        }`}
        icon={field.icon ?? undefined}
        label={field.label}
        onClick={(e) => menuRef.current?.toggle(e)}
        aria-haspopup={hasThesaurusEntry ? "menu" : undefined}
      />
      {hasThesaurusEntry && (
        <Menu
          ref={menuRef}
          popup
          appendTo={document.body}
          model={[
            {
              label: "Voir dans le thésaurus",
              icon: "bi bi-info-circle",
              url: field.conceptUri as string,
              target: "_blank",
            },
          ]}
        />
      )}
      {field.hint && <small className="field-hint">{field.hint}</small>}
    </div>
  );
}

export function isEmptyValue(value: unknown): boolean {
  if (value == null) return true;
  if (Array.isArray(value)) return value.length === 0;
  if (typeof value === "string") return value.trim().length === 0;
  return false;
}

/**
 * Turns one or more dirty field values into a ProjectPatchRequest body.
 *
 * <p>Everything goes through `answers` — the same by-field-id path the list's cell editor uses, so
 * the fiche needs no per-field knowledge of which properties happen to also have a flat alias. The
 * one exception is the spatial-context tree field, which the server refuses in `answers` and which
 * therefore uses its flat `spatialContextSpatialUnitIds` alias instead.</p>
 */
export function buildPatch(draft: Record<string, unknown>, fields: Record<string, FieldResource>): ProjectPatch {
  const answers: Record<string, AnswerInputBody> = {};
  const patch: ProjectPatch = {};

  for (const [fieldId, value] of Object.entries(draft)) {
    const field = fields[fieldId];
    if (!field) continue;
    if (field.answerType === SPATIAL_CONTEXT_ANSWER_TYPE) {
      patch.spatialContextSpatialUnitIds = Array.isArray(value) ? value.map(refId) : [];
      continue;
    }
    answers[fieldId] = toAnswerInput(field, value);
  }

  if (Object.keys(answers).length > 0) patch.answers = answers;
  return patch;
}

function refId(value: unknown): string {
  if (value != null && typeof value === "object") {
    const ref = value as { resourceId?: unknown; id?: unknown };
    if (ref.resourceId != null) return String(ref.resourceId);
    if (ref.id != null) return String(ref.id);
  }
  return String(value);
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString("fr-FR");
}
