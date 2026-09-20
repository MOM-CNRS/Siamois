import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { getFieldRenderer, hasFieldRenderer } from "../../fields/registry";
import { resolveValueBinding } from "../../fields/types";
import type { FieldResource } from "../../fields/types";
import { getProjectHistory } from "./history";
import { parseLayout, panelLabel, type FormLayoutCol } from "./form";
import { getProjectTypes } from "./projectTypes";
import { patchProject } from "./api";
import type { ProjectDetail } from "./types";

// The real fiche, schema-driven off GET /api/v1/organizations/{id}/project-types (plan §5/§6,
// phase 6) — replaces phase 4's four-field placeholder. Renders every panel/row/field
// ActionUnit.DETAILS_FORM's layout defines, same as JSF's "Détails" tab does.
//
// Not everything in that layout has anywhere to live on the wire, though: ProjectResource only
// carries name/identifier/type/beginDate/endDate/mainLocation/spatialContext. The other ~24
// fields (oaCode, status, scientificManager, hostStructure, periods, subjects, ...) are real,
// configured, org-visible fields with no REST equivalent at all — the same gap already flagged
// for the list view's admin columns (columns.tsx), just larger here because the fiche is the
// *whole* form, not seven chosen columns. Rendering them as "Non disponible" (PROJECT_RESOURCE_
// BINDINGS below) keeps this honestly schema-driven instead of silently trimming the org's
// configured layout down to whatever's convenient.
//
// Two more JSF fiche-header features are deliberately NOT built here, for the same reason —
// no REST backing exists yet, not an oversight:
// - "Validate" (INCOMPLETE → COMPLETE → VALIDATED cycle, ActionUnitService.toggleValidated) has
//   no field anywhere on ProjectResource/ProjectPatchRequest.
// - Prev/next (ActionUnitService.findNextByInstitution/findPreviousByInstitution, ordered by
//   creation time within the institution) has no REST endpoint; it's institution/JSF-session
//   shaped, not something the current list's sort/filter state can derive client-side.
// Both need new backend work before a React equivalent is possible.
const PROJECT_RESOURCE_BINDINGS = new Set([
  "name",
  "identifier",
  "type",
  "beginDate",
  "endDate",
  "mainLocation",
  "spatialContext",
]);

// Of those, only fields with a real (non-fallback) renderer are actually editable this phase —
// type/mainLocation/spatialContext resolve to FallbackRenderer (SELECT_* renderers aren't built
// yet, fields/renderers.tsx) and stay read-only regardless of edit mode. Keyed by valueBinding,
// which for these three system fields is also the exact ProjectPatchRequest property name.
const EDITABLE_BINDINGS = new Set(["name", "beginDate", "endDate"]);

interface ProjectFicheTabProps {
  entity: ProjectDetail;
  onSaved: () => void;
}

export function ProjectFicheTab({ entity, onSaved }: ProjectFicheTabProps) {
  const organizationId = entity.organization?.id;

  const typesQuery = useQuery({
    queryKey: ["project-types", organizationId],
    queryFn: () => getProjectTypes(organizationId as string),
    enabled: organizationId != null,
  });

  const historyQuery = useQuery({
    queryKey: ["project-history", entity.id],
    queryFn: () => getProjectHistory(entity.id),
  });

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<ProjectDetail>(entity);
  useEffect(() => {
    if (!editing) setDraft(entity);
  }, [entity, editing]);

  const [identifierEditing, setIdentifierEditing] = useState(false);
  const [identifierDraft, setIdentifierDraft] = useState(entity.fullIdentifier || entity.identifier);
  const [identifierError, setIdentifierError] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: () =>
      patchProject(entity.id, {
        name: draft.name,
        beginDate: toOffsetDateTime(draft.beginDate),
        endDate: toOffsetDateTime(draft.endDate),
      }),
    onSuccess: () => {
      setEditing(false);
      onSaved();
    },
  });

  const identifierMutation = useMutation({
    mutationFn: (identifier: string) => patchProject(entity.id, { identifier }),
    onSuccess: () => {
      setIdentifierEditing(false);
      setIdentifierError(null);
      onSaved();
    },
    onError: (err: unknown) => {
      setIdentifierError(err instanceof ApiError ? err.message : "Échec de l'enregistrement");
    },
  });

  function startEditing() {
    setDraft(entity);
    setEditing(true);
  }

  function cancelEditing() {
    setDraft(entity);
    setEditing(false);
  }

  function saveIdentifier() {
    const trimmed = identifierDraft.trim();
    if (!trimmed) {
      setIdentifierError("L'identifiant est obligatoire");
      return;
    }
    identifierMutation.mutate(trimmed);
  }

  const latestRevision = historyQuery.data?.[0];

  return (
    <div className="project-fiche-tab">
      <div className="project-fiche-tab-header">
        <div className="project-fiche-tab-identifier">
          {identifierEditing ? (
            <>
              <InputText value={identifierDraft} onChange={(e) => setIdentifierDraft(e.target.value)} />
              <Button icon="pi pi-check" onClick={saveIdentifier} loading={identifierMutation.isPending} />
              <Button
                icon="pi pi-times"
                className="p-button-text"
                onClick={() => {
                  setIdentifierEditing(false);
                  setIdentifierError(null);
                  setIdentifierDraft(entity.fullIdentifier || entity.identifier);
                }}
              />
            </>
          ) : (
            <>
              <span>{entity.fullIdentifier || entity.identifier}</span>
              <Button icon="pi pi-pencil" className="p-button-text" onClick={() => setIdentifierEditing(true)} />
            </>
          )}
        </div>
        {identifierError && <Message severity="error" text={identifierError} />}
        {latestRevision && (
          <div className="project-fiche-tab-last-modified">
            Dernière modification : {formatDateTime(latestRevision.revisionDate)}
            {latestRevision.author &&
              ` par ${[latestRevision.author.name, latestRevision.author.lastname].filter(Boolean).join(" ")}`}
          </div>
        )}
        <div className="project-fiche-tab-actions">
          {editing ? (
            <>
              <Button label="Enregistrer" onClick={() => saveMutation.mutate()} loading={saveMutation.isPending} />
              <Button label="Annuler" className="p-button-text" onClick={cancelEditing} />
            </>
          ) : (
            <Button label="Modifier" icon="pi pi-pencil" onClick={startEditing} />
          )}
        </div>
        {saveMutation.isError && (
          <Message
            severity="error"
            text={saveMutation.error instanceof ApiError ? saveMutation.error.message : "Échec de l'enregistrement"}
          />
        )}
      </div>

      {organizationId == null && (
        <Message severity="warn" text="Organisation inconnue : impossible de charger le formulaire" />
      )}
      {typesQuery.isLoading && <div>Chargement du formulaire…</div>}
      {typesQuery.error && <Message severity="error" text="Impossible de charger la configuration du formulaire" />}
      {typesQuery.data && (
        <FormLayout
          layoutJson={typesQuery.data.layoutJson}
          fields={typesQuery.data.fields}
          source={editing ? draft : entity}
          editing={editing}
          onEntityChange={setDraft}
        />
      )}
    </div>
  );
}

function FormLayout({
  layoutJson,
  fields,
  source,
  editing,
  onEntityChange,
}: {
  layoutJson: string;
  fields: Record<string, FieldResource>;
  source: ProjectDetail;
  editing: boolean;
  onEntityChange: (entity: ProjectDetail) => void;
}) {
  const panels = useMemo(() => parseLayout(layoutJson), [layoutJson]);

  return (
    <>
      {panels.map((panel, panelIndex) => (
        <fieldset key={panelIndex} className={panel.className ?? undefined}>
          <legend>{panelLabel(panel.name)}</legend>
          {panel.rows.map((row, rowIndex) => (
            <div key={rowIndex} className="project-fiche-tab-row">
              {row.columns.map((col, colIndex) => (
                <FormField
                  key={colIndex}
                  col={col}
                  fields={fields}
                  source={source}
                  editing={editing}
                  onEntityChange={onEntityChange}
                />
              ))}
            </div>
          ))}
        </fieldset>
      ))}
    </>
  );
}

function FormField({
  col,
  fields,
  source,
  editing,
  onEntityChange,
}: {
  col: FormLayoutCol;
  fields: Record<string, FieldResource>;
  source: ProjectDetail;
  editing: boolean;
  onEntityChange: (entity: ProjectDetail) => void;
}) {
  // The identifier column is marked readOnly + "d-none" in the real layout (ActionUnitDetailsForm)
  // because JSF also edits it outside this grid, via its own header affordance — same reason the
  // header's identifier control above exists separately rather than as a form field here.
  if (col.fieldId == null || col.className?.includes("d-none")) return null;

  const field = fields[String(col.fieldId)];
  if (!field) return null;

  const binding = field.valueBinding ?? field.id;

  if (!PROJECT_RESOURCE_BINDINGS.has(binding)) {
    return (
      <div className={col.className ?? undefined}>
        <label>{field.label}</label>
        <div className="field-renderer-unavailable" title="Ce champ n'est pas encore exposé par l'API projet">
          Non disponible
        </div>
      </div>
    );
  }

  const resolved = resolveValueBinding(field);
  const value = resolved.read(source);
  const canEdit = editing && !col.isReadOnly && EDITABLE_BINDINGS.has(binding) && hasFieldRenderer(field.answerType);
  const Renderer = getFieldRenderer(field.answerType);

  return (
    <div className={col.className ?? undefined}>
      <label>{field.label}</label>
      <Renderer
        field={field}
        value={value}
        readOnly={!canEdit}
        required={col.isRequired}
        onChange={(v) => onEntityChange(resolved.write(source, v))}
      />
    </div>
  );
}

// DateRenderer hands back a bare "YYYY-MM-DD" (fields/renderers.tsx) but ProjectPatchRequest's
// beginDate/endDate are OffsetDateTime — Jackson rejects a date without an offset.
function toOffsetDateTime(value?: string | null): string | null | undefined {
  if (!value) return value;
  return value.length === 10 ? `${value}T00:00:00Z` : value;
}

function formatDateTime(value: string): string {
  return new Date(value).toLocaleString("fr-FR");
}
