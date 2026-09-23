import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { useCanEdit } from "../../panels/writeMode";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import { toAnswerInput } from "../../fields/types";
import type { FieldResource } from "../../fields/types";
import { patchRecordingUnitAnswers } from "./api";
import { getRecordingUnitTypes } from "./recordingUnitTypes";
import type { RecordingUnitDetail } from "./types";

// recordingUnitPanelHeader.xhtml's content (identifier chip, then the editable category chip) —
// lives in EntityDetailPanel's own PrimeReact <Panel> `header`, matching Project's own
// DetailHeader.tsx exactly in structure. No mainLocation-style third chip: the real header has
// none for a recording unit (unlike actionUnitPanelHeader.xhtml's mainLocation chip).
//
// Deliberately NOT built here, for want of a REST equivalent rather than by oversight:
// - identifier editing (editableIdentifierChip.xhtml binds it to `unit.fullIdentifier`, persisted
//   via RecordingUnitPanel.persistIdentifierEdit): RecordingUnitPatchRequest has no flat alias
//   for `fullIdentifier` at all (unlike ProjectPatchRequest.identifier) — the chip below is a
//   plain read-only Chip, not a pencil-toggled control.
// - the "Modifications non enregistré" chip (panelModel.hasUnsavedModifications): there is no
//   pending-edit state to report — each field commits itself the moment its click-to-edit overlay
//   closes, the same as the list's own table cells.
export interface RecordingUnitDetailHeaderProps {
  entity: RecordingUnitDetail;
  onSaved: () => void;
}

export function RecordingUnitDetailHeader({ entity, onSaved }: RecordingUnitDetailHeaderProps) {
  const canEdit = useCanEdit(entity);
  return (
    <div
      className="recording-unit-detail-header"
      style={{ display: "flex", alignItems: "center", gap: "0.5em", flexWrap: "wrap" }}
    >
      <Chip label={entity.fullIdentifier} className="entity-nav-chip" />
      <CategoryChip entity={entity} onSaved={onSaved} canEdit={canEdit} />
    </div>
  );
}

/**
 * pages/shared/chip/editableCategoryChip.xhtml: the RU's type, shown as a chip and edited in
 * place with the same concept autocomplete the fiche and the table's cell editor use
 * (SelectOneConceptRenderer). Unlike Project's own CategoryChip, the write has no flat alias to
 * go through — RecordingUnitPatchRequest carries only `answers`/`geom`/`expectedRevision` — so
 * this routes through patchRecordingUnitAnswers, keyed by the type field's own id
 * (RecordingUnitForm.RECORDING_UNIT_TYPE_FIELD, resolved by `valueBinding` like Project's own,
 * not hardcoded, so a renumbering of the system-field catalog doesn't silently break this).
 */
function CategoryChip({ entity, onSaved, canEdit }: RecordingUnitDetailHeaderProps & { canEdit: boolean }) {
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const typesQuery = useQuery({
    queryKey: ["recording-unit-types", entity.projectId],
    queryFn: () => getRecordingUnitTypes(entity.projectId as string),
    enabled: entity.projectId != null,
  });

  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );

  const organizationIdRaw = entity.organization?.id;
  const organizationId = organizationIdRaw != null ? Number(organizationIdRaw) : undefined;

  const mutation = useMutation({
    mutationFn: (value: unknown) => {
      // Only ever invoked from the picker below, which is only rendered once `typeField` is
      // resolved (`editable` gates it) — this branch is unreachable in practice.
      if (!typeField) return Promise.reject(new Error("Type de champ inconnu"));
      return patchRecordingUnitAnswers(entity.id, { [typeField.id]: toAnswerInput(typeField, value) });
    },
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
      <span className="recording-unit-detail-header-category" style={{ display: "inline-flex", alignItems: "center" }}>
        <SelectOneConceptRenderer
          field={typeField}
          value={entity.type}
          readOnly={mutation.isPending}
          required={false}
          organizationId={organizationId}
          onChange={(v) => mutation.mutate(v)}
        />
        <Button icon="pi pi-times" className="p-button-text" aria-label="Annuler" onClick={() => setEditing(false)} />
        {error && <Message severity="error" text={error} />}
      </span>
    );
  }

  if (!label && !editable) return null;

  return (
    <span className="recording-unit-detail-header-category" style={{ display: "inline-flex", alignItems: "center" }}>
      <Chip label={label ?? "Sans type"} className="recording-unit-chip-alt" />
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
