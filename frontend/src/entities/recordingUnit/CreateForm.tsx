import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { createRecordingUnit } from "./api";
import { getRecordingUnitTypes } from "./recordingUnitTypes";

// The "Unités d'enregistrement" relation tab's own "Créer" overlay (migration plan follow-up —
// see entities/project/CreateForm.tsx's own doc for the overlay-not-dialog rationale, and
// entities/types.ts's CreateFormContext for why `scope` — the project this tab is scoped to — is
// what supplies `projectId` here, not a prop of this form's own). Reduced to a single field: type.
// RecordingUnitCreateRequest needs nothing else to create a valid UE (no client-supplied
// identifier — RecordingUnitIdentifierConfig generates one server-side, same as JSF); every other
// field is editable right after, on the fiche this overlay navigates straight into.
//
// The concept picker shape is {resourceId, resourceType, label} (fields/renderers.tsx's own
// ResourceRefLike), same convention as ProjectCreateForm's own type picker.
interface ConceptPick {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export function RecordingUnitCreateForm({ organizationId, scope, onCreated, onCancel }: CreateFormContext) {
  const projectId = scope?.entityType === "project" ? String(scope.id) : undefined;
  const [type, setType] = useState<ConceptPick | null>(null);
  const [error, setError] = useState<string | null>(null);

  const typesQuery = useQuery({
    queryKey: ["recording-unit-types", projectId],
    queryFn: () => getRecordingUnitTypes(projectId as string),
    enabled: projectId != null,
  });

  // RecordingUnitForm.RECORDING_UNIT_TYPE_FIELD, located by its valueBinding like every other
  // place this app resolves the RU type field (RecordingUnit/DetailHeader.tsx's own CategoryChip).
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );

  const mutation = useMutation({
    mutationFn: () => createRecordingUnit({ projectId: projectId as string, typeId: type!.resourceId }),
    onSuccess: (created) => onCreated(created.id),
    onError: (err: unknown) => {
      setError(err instanceof ApiError ? err.message : "Échec de la création");
    },
  });

  const canSubmit = projectId != null && type != null && !mutation.isPending;

  return (
    <form
      className="project-create-form"
      style={{ display: "flex", flexDirection: "column", gap: "0.75em", minWidth: "22em" }}
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) mutation.mutate();
      }}
    >
      <h4 style={{ margin: 0 }}>Nouvelle unité d'enregistrement</h4>

      {projectId == null && <Message severity="warn" text="Projet inconnu : création impossible" />}

      <label className="project-create-form-field">
        <span>Type</span>
        {typeField ? (
          <SelectOneConceptRenderer
            field={typeField}
            value={type}
            readOnly={false}
            required
            organizationId={organizationId}
            onChange={(v) => setType(v as ConceptPick | null)}
          />
        ) : (
          <span>Chargement…</span>
        )}
      </label>

      {error && <Message severity="error" text={error} />}

      <div style={{ display: "flex", gap: "0.5em", justifyContent: "flex-end" }}>
        <Button type="button" label="Annuler" text onClick={onCancel} />
        <Button type="submit" label="Créer" disabled={!canSubmit} loading={mutation.isPending} />
      </div>
    </form>
  );
}
