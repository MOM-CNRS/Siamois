import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { InputText } from "primereact/inputtext";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { useCanEdit } from "../../panels/writeMode";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import { patchProject } from "./api";
import { getProjectTypes } from "./projectTypes";
import type { ProjectDetail } from "./types";

// actionUnitPanelHeader.xhtml's content (identifier chip + pencil/apply/cancel, the editable
// category chip, then name/location chips) — lives in EntityDetailPanel's own PrimeReact <Panel>
// `header`, alongside the toolbar, exactly matching the real markup's single sideview-titlebar div
// that holds both the toolbar form and this header include side by side.
//
// The pencils follow headerEditControls.xhtml's own two-part gate exactly: the app's global
// read/write switch (FlowBean.isWriteMode) AND the user's right on this project
// (_permissions.canEdit, the same permission canUserEditUnit() checks — bug #448). Out of write
// mode both chips are plain read-only chips, as they are in JSF.
//
// prev/next ("fiche précédente/suivante") is NOT rendered here — it's generic, portable to any
// entity's fiche, so it's rendered by EntityDetailPanel itself (in its title, before
// config.detail.header?.()) via config.api.siblings, not by this entity-specific header. See
// EntityDetailPanel.tsx / entities/project/api.ts#getProjectSiblings.
//
// Deliberately NOT built here either, for want of a REST equivalent rather than by oversight:
// - the "Modifications non enregistré" chip (panelModel.hasUnsavedModifications): the fiche's
//   pending edits live in ProjectFicheTab's own draft, which this header cannot see and which
//   already shows its own "N champs modifiés" counter next to Enregistrer.
export interface ProjectDetailHeaderProps {
  entity: ProjectDetail;
  onSaved: () => void;
}

export function ProjectDetailHeader({ entity, onSaved }: ProjectDetailHeaderProps) {
  const canEdit = useCanEdit(entity);
  return (
    <div
      className="project-detail-header"
      style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}
    >
      <IdentifierChip entity={entity} onSaved={onSaved} canEdit={canEdit} />
      <CategoryChip entity={entity} onSaved={onSaved} canEdit={canEdit} />
      <Chip label={entity.name} className="mr-2 action-unit-type-chip" />
      {entity.mainLocation?.name && (
        <Chip label={entity.mainLocation.name} icon="bi bi-geo-alt" className="mr-2 action-unit-type-chip" />
      )}
    </div>
  );
}

/** pages/shared/chip/editableIdentifierChip.xhtml: a p:chip that swaps to an input on the pencil. */
function IdentifierChip({ entity, onSaved, canEdit }: ProjectDetailHeaderProps & { canEdit: boolean }) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(entity.fullIdentifier || entity.identifier);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (identifier: string) => patchProject(entity.id, { identifier }),
    onSuccess: () => {
      setEditing(false);
      setError(null);
      onSaved();
    },
    onError: (err: unknown) => {
      setError(err instanceof ApiError ? err.message : "Échec de l'enregistrement");
    },
  });

  function save() {
    const trimmed = draft.trim();
    if (!trimmed) {
      setError("L'identifiant est obligatoire");
      return;
    }
    mutation.mutate(trimmed);
  }

  return (
    <div className="project-fiche-tab-identifier" style={{ display: "flex", alignItems: "center" }}>
      {editing ? (
        <>
          <InputText value={draft} onChange={(e) => setDraft(e.target.value)} />
          <Button icon="pi pi-check" onClick={save} loading={mutation.isPending} aria-label="Enregistrer l'identifiant" />
          <Button
            icon="pi pi-times"
            className="p-button-text"
            aria-label="Annuler"
            onClick={() => {
              setEditing(false);
              setError(null);
              setDraft(entity.fullIdentifier || entity.identifier);
            }}
          />
        </>
      ) : (
        <>
          <Chip label={entity.fullIdentifier || entity.identifier} className="entity-nav-chip" />
          {canEdit && (
            <Button
              icon="pi pi-pencil"
              className="p-button-text"
              aria-label="Modifier l'identifiant"
              onClick={() => setEditing(true)}
            />
          )}
        </>
      )}
      {error && <Message severity="error" text={error} />}
    </div>
  );
}

/**
 * pages/shared/chip/editableCategoryChip.xhtml: the project's type, shown as a chip and edited
 * in place with the same concept autocomplete the fiche and the table's cell editor use
 * (SelectOneConceptRenderer). The write goes through ProjectPatchRequest's flat `typeId`, which
 * is the alias the server documents for this field.
 *
 * <p>The field's metadata (its fieldCode, which is what the autocomplete queries) comes from the
 * same org catalog the fiche loads — same query key, so React Query serves both from one fetch.</p>
 */
function CategoryChip({ entity, onSaved, canEdit }: ProjectDetailHeaderProps & { canEdit: boolean }) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const typesQuery = useQuery({
    queryKey: ["project-types", organizationIdRaw],
    queryFn: () => getProjectTypes(organizationIdRaw as string),
    enabled: organizationIdRaw != null,
  });

  // ActionUnitForm.ACTION_UNIT_TYPE_FIELD — located by its valueBinding rather than by its
  // hardcoded id (-101), so a renumbering of the system-field catalog doesn't silently break this.
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );

  const mutation = useMutation({
    mutationFn: (typeId: string | null) => patchProject(entity.id, { typeId }),
    onSuccess: () => {
      setEditing(false);
      setError(null);
      onSaved();
    },
    onError: (err: unknown) => {
      setError(err instanceof ApiError ? err.message : "Échec de l'enregistrement");
    },
  });

  const label = entity.type?.resolvedLabel;
  const editable = canEdit && typeField != null;

  if (editing && editable) {
    return (
      <span className="project-detail-header-category" style={{ display: "inline-flex", alignItems: "center" }}>
        <SelectOneConceptRenderer
          field={typeField}
          value={entity.type}
          readOnly={mutation.isPending}
          required={false}
          organizationId={organizationId}
          onChange={(v) => mutation.mutate(conceptId(v))}
        />
        <Button icon="pi pi-times" className="p-button-text" aria-label="Annuler" onClick={() => setEditing(false)} />
        {error && <Message severity="error" text={error} />}
      </span>
    );
  }

  if (!label && !editable) return null;

  return (
    <span className="project-detail-header-category" style={{ display: "inline-flex", alignItems: "center" }}>
      <Chip label={label ?? "Sans type"} className="action-unit-type-chip" />
      {editable && (
        <Button
          icon="pi pi-pencil"
          className="p-button-text"
          aria-label="Modifier le type"
          onClick={() => setEditing(true)}
        />
      )}
    </span>
  );
}

function conceptId(value: unknown): string | null {
  if (value != null && typeof value === "object") {
    const ref = value as { resourceId?: unknown; id?: unknown };
    if (ref.resourceId != null) return String(ref.resourceId);
    if (ref.id != null) return String(ref.id);
  }
  return null;
}
