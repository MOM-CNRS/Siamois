import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { InputText } from "primereact/inputtext";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { CreateLinkField } from "../../components/CreateLinkField";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { createProject } from "./api";
import { getProjectTypes } from "./projectTypes";

// The list toolbar's "Créer" overlay (migration plan follow-up — see entities/types.ts's own
// CreateFormContext doc for why this is an overlay, not a JSF-style modal dialog). Deliberately
// simpler than newUnitDialog.xhtml's own project form: name, identifier and type only — no
// begin/end date, no main location, no spatial context. Every one of those is still editable right
// after creation, on the fiche this overlay navigates straight into, the same way every other
// field there is (click-to-edit) — this form's only job is to get a valid project INTO existence,
// not to front-load the whole fiche into a dialog the way JSF does.
//
// The concept picker shape is {resourceId, resourceType, label} (fields/renderers.tsx's own
// ResourceRefLike) — not entities/project/types.ts's ResolvedConcept ({id, resolvedLabel}) — a
// value never resolved off the wire here, so this keeps the SHAPE ResourceRefRenderer's own
// onChange emits rather than round-tripping it through the other one just to satisfy a type this
// form never actually needs.
interface ConceptPick {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export function ProjectCreateForm({ organizationId, prefill, onCreated, onCancel }: CreateFormContext) {
  const [name, setName] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [type, setType] = useState<ConceptPick | null>(null);
  const [error, setError] = useState<string | null>(null);

  const typesQuery = useQuery({
    queryKey: ["project-types", organizationId],
    queryFn: () => getProjectTypes(organizationId as number),
    enabled: organizationId != null,
  });

  // ActionUnitForm.ACTION_UNIT_TYPE_FIELD, located by its valueBinding like every other place
  // this app resolves the project type field (DetailHeader.tsx's own CategoryChip) — never
  // hardcoded, so a renumbering of the system-field catalog doesn't silently break this.
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [typesQuery.data],
  );

  const mutation = useMutation({
    mutationFn: () =>
      createProject({
        organizationId: String(organizationId),
        name: name.trim(),
        identifier: identifier.trim(),
        typeId: type!.resourceId,
        spatialContextSpatialUnitIds: prefill?.spatialContext ? [String(prefill.spatialContext.id)] : undefined,
      }),
    onSuccess: (created) => onCreated(created.id),
    onError: (err: unknown) => {
      setError(err instanceof ApiError ? err.message : "Échec de la création");
    },
  });

  const canSubmit =
    organizationId != null && name.trim() !== "" && identifier.trim() !== "" && type != null && !mutation.isPending;

  return (
    <form
      className="project-create-form"
      style={{ display: "flex", flexDirection: "column", gap: "0.75em", minWidth: "22em" }}
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) mutation.mutate();
      }}
    >
      <h4 style={{ margin: 0 }}>Nouveau projet</h4>

      {prefill?.spatialContext && <CreateLinkField label="Lieu" entityType="place" value={prefill.spatialContext} />}

      <label className="project-create-form-field">
        <span>Nom</span>
        <InputText value={name} onChange={(e) => setName(e.target.value)} autoFocus required />
      </label>

      <label className="project-create-form-field">
        <span>Identifiant</span>
        <InputText value={identifier} onChange={(e) => setIdentifier(e.target.value)} required />
      </label>

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
