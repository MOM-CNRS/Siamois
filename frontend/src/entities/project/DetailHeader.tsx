import { useEffect, useMemo, useState, type KeyboardEvent } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { InputText } from "primereact/inputtext";
import { Message } from "primereact/message";
import { useCanEdit } from "../../panels/writeMode";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import { patchProject, type ProjectPatch } from "./api";
import { getProjectTypes } from "./projectTypes";
import type { ProjectDetail } from "./types";
import { getEntityType } from "../registry";
import { queryKeys } from "../../api/queryKeys";
import { messageForError } from "../../api/errors";
import { t } from "../../i18n";

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
// - the "Modifications non enregistré" chip (panelModel.hasUnsavedModifications): there is no
//   pending-edit state to report — each field commits itself the moment its click-to-edit overlay
//   closes, the same as the list's own table cells.
export interface ProjectDetailHeaderProps {
  entity: ProjectDetail;
  onSaved: () => void;
}

// One pencil, at the far right of the header, switches the whole header (name, identifier, type)
// into edit mode — hidden until the header is hovered so a stray click can't start an edit. The
// name is the primary chip, the identifier and the type are secondary chips. In edit mode the
// pencil becomes the ONE validate button: it writes every changed field in a single patch (Enter
// does the same from an input, Escape drops the draft).
export function ProjectDetailHeader({ entity, onSaved }: ProjectDetailHeaderProps) {
  const canEdit = useCanEdit(entity);
  const { typeField, organizationId } = useProjectTypeField(entity);
  const identifier = entity.fullIdentifier || entity.identifier;
  const [editing, setEditing] = useState(false);
  const [nameDraft, setNameDraft] = useState(entity.name);
  const [identifierDraft, setIdentifierDraft] = useState(identifier);
  const [typeDraft, setTypeDraft] = useState<unknown>(entity.type);
  const [error, setError] = useState<string | null>(null);
  const isEditing = editing && canEdit;

  function resetDrafts() {
    setNameDraft(entity.name);
    setIdentifierDraft(identifier);
    setTypeDraft(entity.type);
    setError(null);
  }

  // A reloaded entity (after a save, or another fiche) drops whatever draft was in flight.
  useEffect(resetDrafts, [entity.name, identifier, entity.type]); // eslint-disable-line react-hooks/exhaustive-deps

  const mutation = useMutation({
    mutationFn: (patch: ProjectPatch) => patchProject(entity.id, patch),
    onSuccess: () => {
      setEditing(false);
      setError(null);
      onSaved();
    },
    onError: (err: unknown) => {
      setError(messageForError(err, t("header.saveFailed")));
    },
  });

  function validate() {
    const name = nameDraft.trim();
    const ident = identifierDraft.trim();
    if (!name) return setError(t("header.nameRequired"));
    if (!ident) return setError(t("header.identifierRequired"));
    const patch: ProjectPatch = {};
    if (name !== entity.name) patch.name = name;
    if (ident !== identifier) patch.identifier = ident;
    if (conceptId(typeDraft) !== conceptId(entity.type)) patch.typeId = conceptId(typeDraft);
    if (Object.keys(patch).length === 0) {
      setEditing(false);
      return;
    }
    mutation.mutate(patch);
  }

  function cancel() {
    setEditing(false);
    resetDrafts();
  }

  function onKeyDown(e: KeyboardEvent<HTMLElement>) {
    if (!isEditing) return;
    if (e.key === "Escape") cancel();
    if (e.key === "Enter" && (e.target as HTMLElement).tagName === "INPUT") validate();
  }

  const typeLabel = entity.type?.resolvedLabel;
  const typeEditable = isEditing && typeField != null;

  return (
    <div className="project-detail-header sia-hstack" onKeyDown={onKeyDown}>
      {isEditing ? (
        <InputText
          className="project-detail-header-name-input"
          value={nameDraft}
          aria-label={t("common.name")}
          onChange={(e) => setNameDraft(e.target.value)}
        />
      ) : (
        <Chip label={entity.name} className="action-unit-chip-alt entity-nav-chip" icon={getEntityType("project")?.icon} />
      )}
      {isEditing ? (
        <InputText
          className="project-detail-header-identifier-input"
          value={identifierDraft}
          aria-label={t("common.identifier")}
          onChange={(e) => setIdentifierDraft(e.target.value)}
        />
      ) : (
        <Chip label={identifier} className="mr-2 action-unit-type-chip" />
      )}
      {typeEditable ? (
        <span className="project-detail-header-category sia-inline-center">
          <SelectOneConceptRenderer
            field={typeField}
            value={typeDraft as ProjectDetail["type"]}
            readOnly={mutation.isPending}
            required={false}
            organizationId={organizationId}
            onChange={setTypeDraft}
          />
        </span>
      ) : (
        (typeLabel || (canEdit && typeField != null)) && (
          <span className="project-detail-header-category sia-inline-center">
            <Chip label={typeLabel ?? "Sans type"} className="mr-2 action-unit-type-chip" />
          </span>
        )
      )}
      {!isEditing && entity.mainLocation?.name && (
        <Chip label={entity.mainLocation.name} icon="bi bi-geo-alt" className="mr-2 action-unit-type-chip" />
      )}
      {error && <Message severity="error" text={error} />}
      {canEdit && (
        <Button
          icon={isEditing ? "pi pi-check" : "pi pi-pencil"}
          className="p-button-text project-detail-header-edit"
          aria-label={isEditing ? t("header.validate") : t("header.editHeader")}
          loading={mutation.isPending}
          onClick={() => (isEditing ? validate() : setEditing(true))}
        />
      )}
    </div>
  );
}

/**
 * The project type's field metadata, from the same org catalog the fiche loads (same query key, so
 * React Query serves both from one fetch). ActionUnitForm.ACTION_UNIT_TYPE_FIELD is located by its
 * valueBinding rather than by its hardcoded id (-101), so a renumbering of the system-field catalog
 * doesn't silently break the header. The write goes through ProjectPatchRequest's flat `typeId`.
 */
function useProjectTypeField(entity: ProjectDetail) {
  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;
  const typesQuery = useQuery({
    queryKey: queryKeys.projectTypes(organizationIdRaw),
    queryFn: () => getProjectTypes(organizationIdRaw as string),
    enabled: organizationIdRaw != null,
  });
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );
  return { typeField, organizationId };
}

function conceptId(value: unknown): string | null {
  if (value != null && typeof value === "object") {
    const ref = value as { resourceId?: unknown; id?: unknown };
    if (ref.resourceId != null) return String(ref.resourceId);
    if (ref.id != null) return String(ref.id);
  }
  return null;
}
