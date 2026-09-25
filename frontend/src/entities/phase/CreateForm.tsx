import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { createPhase } from "./api";
import { getPhaseEffectiveForm } from "./phaseTypes";
import { useCreateProject } from "../../components/useCreateProject";

// The "Phases" relation tab's own "Créer" overlay (migration plan follow-up, lot 2 — see
// entities/project/CreateForm.tsx for the overlay-not-dialog rationale, and
// entities/recordingUnit/CreateForm.tsx for the closest sibling: same shape, `projectId` from
// `scope.id`, a single required field). PhaseNewUnitForm only requires a type; title (its only
// other field) is left for the fiche afterward, like every other field.
interface ConceptPick {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export function PhaseCreateForm({ organizationId, scope, onCreated, onCancel }: CreateFormContext) {
  // The list's own project, or — on an organization-wide list — the one picked first in the form.
  const { projectId, picker: projectPicker } = useCreateProject({ scope, organizationId, kind: "phase" });
  const [type, setType] = useState<ConceptPick | null>(null);
  // Types are per project: a pick from the previous project's catalog no longer applies.
  useEffect(() => {
    setType(null);
  }, [projectId]);
  const [error, setError] = useState<string | null>(null);

  const typesQuery = useQuery({
    queryKey: ["phase-effective-form", projectId, null],
    queryFn: () => getPhaseEffectiveForm(projectId as string, null),
    enabled: projectId != null,
  });

  // PhaseForm.typeField, located by its valueBinding like every other place this app resolves a
  // type field (DetailHeader.tsx's own CategoryChip, RecordingUnitCreateForm's own typeField).
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );

  const mutation = useMutation({
    mutationFn: () => createPhase({ projectId: projectId as string, typeId: type!.resourceId }),
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
      <h4 style={{ margin: 0 }}>Nouvelle phase</h4>

      {projectPicker}
      {projectId == null && !projectPicker && <Message severity="warn" text="Projet inconnu : création impossible" />}

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
          <span className="create-form-hint">{projectId == null ? "Choisissez d'abord un projet" : "Chargement…"}</span>
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
