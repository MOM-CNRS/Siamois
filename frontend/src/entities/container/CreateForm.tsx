import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { createContainer } from "./api";
import { getContainerEffectiveForm } from "./containerTypes";
import { scopeProjectId } from "../scope";

// The "Contenants" relation tab's own "Créer" overlay (migration plan follow-up, lot 3 — see
// entities/project/CreateForm.tsx for the overlay-not-dialog rationale, and
// entities/phase/CreateForm.tsx for the closest sibling: same shape, `projectId` from
// `scope.id`, a single required field). ContainerNewUnitForm only requires a type; every other
// field (spatial unit, dimensions, weight) is left for the fiche afterward.
interface ConceptPick {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export function ContainerCreateForm({ organizationId, scope, onCreated, onCancel }: CreateFormContext) {
  const projectId = scopeProjectId(scope);
  const [type, setType] = useState<ConceptPick | null>(null);
  const [error, setError] = useState<string | null>(null);

  const typesQuery = useQuery({
    queryKey: ["container-effective-form", projectId, null],
    queryFn: () => getContainerEffectiveForm(projectId as string, null),
    enabled: projectId != null,
  });

  // ContainerForm.typeField, located by its valueBinding like every other place this app
  // resolves a type field.
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );

  const mutation = useMutation({
    mutationFn: () => createContainer({ projectId: projectId as string, typeId: type!.resourceId }),
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
      <h4 style={{ margin: 0 }}>Nouveau contenant</h4>

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
