import { useCallback, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { Panel } from "primereact/panel";
import { Toolbar } from "primereact/toolbar";
import { FieldEditCell } from "../../fields/FieldEditCell";
import { FieldLabel, isEmptyValue } from "../../fields/FieldLabel";
import { resolveValueBinding, toAnswerInput, type AnswerInputBody } from "../../fields/types";
import type { FieldResource } from "../../fields/types";
import { useCanEdit } from "../../panels/writeMode";
import { getProjectHistory, type ProjectHistoryEntry } from "./history";
import { parseLayout, panelLabel, toPrimeFlexClass, type FormLayoutCol } from "./form";
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
// - HOW a field is edited is now identical to the list's own table cells (plan follow-up: "fiche
//   fields edit on click, like the table"): a field is a plain value until clicked, and
//   FieldEditCell then opens CellEditOverlay — the exact same component the list uses, not a
//   fiche-specific reimplementation — on top of it. The commit boundaries are shared with that
//   editor (fields/commit.ts): a picker or date saves the moment it changes, a text/number input
//   saves on blur or Enter, and Escape reverts the field without saving.
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

  // CellEditOverlay always hands back an AnswerInput-shaped map, never the raw value — but for
  // every answerType buildPatch's own toId/toAnswerInput round-trip is idempotent on an
  // already-converted value (a scalar or an array of plain ids), so reading the one raw value back
  // out of the map and handing it to buildPatch reuses that function unchanged, spatial-context
  // special case included, rather than re-deriving the same branch here a second time.
  //
  // No onSaved() call here: CellEditOverlay calls its own onSaved prop itself once onSave resolves
  // — this only builds the patch and lets the failure propagate for the overlay to show inline.
  const save = useCallback(
    (id: string | number, answers: Record<string, AnswerInputBody>) => {
      if (!fields) return Promise.resolve();
      const [fieldId, input] = Object.entries(answers)[0];
      const rawValue = "values" in input ? input.values : input.value;
      return patchProject(id, buildPatch({ [fieldId]: rawValue }, fields));
    },
    [fields],
  );

  return (
    <div className="project-fiche-tab sia-fiche-tab">
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
              <div key={rowIndex} className="project-fiche-tab-row grid">
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
                    onSaved={onSaved}
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
  onSaved,
}: {
  col: FormLayoutCol;
  fields: Record<string, FieldResource>;
  entity: ProjectDetail;
  canEdit: boolean;
  organizationId?: number;
  inactiveFieldIds: Set<string>;
  onSave: (id: string | number, answers: Record<string, AnswerInputBody>) => Promise<unknown>;
  onSaved: () => void;
}) {
  // The identifier column is marked readOnly + hidden in the real layout (ActionUnitDetailsForm)
  // because JSF edits it outside this grid, via the panel header's own affordance — same reason
  // ProjectDetailHeader's identifier control exists separately rather than as a form field here.
  if (col.fieldId == null || col.hidden) return null;

  const fieldId = String(col.fieldId);
  const field = fields[fieldId];
  if (!field) return null;

  const stored = resolveValueBinding(field).read(entity);
  if (inactiveFieldIds.has(fieldId) && isEmptyValue(stored)) return null;

  return (
    <div className={`project-fiche-tab-col ${toPrimeFlexClass(col.width)}`} data-field-id={fieldId}>
      <div className="field-value-group">
        <FieldLabel field={field} required={col.isRequired} />
        <FieldEditCell
          key={JSON.stringify(stored ?? null)}
          field={field}
          row={entity}
          stored={stored}
          readOnly={!canEdit || col.isReadOnly}
          required={col.isRequired}
          organizationId={organizationId}
          onSave={onSave}
          onSaved={onSaved}
        />
      </div>
    </div>
  );
}

export { isEmptyValue } from "../../fields/FieldLabel";


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
