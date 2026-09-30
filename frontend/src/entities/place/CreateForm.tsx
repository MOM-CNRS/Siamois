import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { InputText } from "primereact/inputtext";
import { CreateFormField, CreateFormShell } from "../../components/CreateFormShell";
import { CreateLinkField } from "../../components/CreateLinkField";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { createPlace } from "./api";
import { messageForError } from "../../api/errors";

// The list toolbar's "Créer" overlay for places — name and type only (PlaceCreateRequest's
// required pair), like the other create overlays. Unlike them, places belong to the organization,
// not a project, so this is the one organization-wide list whose creation is allowed (JSF's
// SpatialUnitListPanel checks ORGANIZATION_MANAGE_PLACES; the POST enforces it).
//
// No endpoint serves SpatialUnit.DETAILS_FORM's field catalog outside a place's own detail
// (SpatialUnit has no ConfigurableTable entry, hence no types catalog), so the type field is
// described here, mirroring SpatialUnit.SPATIAL_UNIT_TYPE_FIELD — the concept picker only needs
// its fieldCode to query GET /organizations/{id}/concepts.
const PLACE_TYPE_FIELD: FieldResource = {
  id: "-201",
  resourceType: "fields",
  label: "Type",
  answerType: "SELECT_ONE_FROM_FIELD_CODE",
  isSystemField: true,
  valueBinding: "category",
  fieldCode: "SIASU.TYPE",
};

interface ConceptPick {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export function PlaceCreateForm({ organizationId, prefill, onCreated, onCancel }: CreateFormContext) {
  const [name, setName] = useState("");
  const [type, setType] = useState<ConceptPick | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      createPlace({
        organizationId: organizationId as number,
        name: name.trim(),
        typeConceptId: type!.resourceId,
        parentPlaceId: prefill?.parent?.id,
        childPlaceId: prefill?.child?.id,
      }),
    onSuccess: (created) => onCreated(created.id),
    onError: (err: unknown) => {
      setError(messageForError(err, "Échec de la création"));
    },
  });

  const canSubmit = organizationId != null && name.trim() !== "" && type != null && !mutation.isPending;

  return (
    <CreateFormShell
      entityType="place"
      title="Nouveau lieu"
      canSubmit={canSubmit}
      pending={mutation.isPending}
      error={error}
      onSubmit={() => mutation.mutate()}
      onCancel={onCancel}
    >
      {prefill?.parent && <CreateLinkField label="Contenu dans" entityType="place" value={prefill.parent} />}
      {prefill?.child && <CreateLinkField label="Contient" entityType="place" value={prefill.child} />}

      <CreateFormField label="Nom" required>
        <InputText value={name} onChange={(e) => setName(e.target.value)} required />
      </CreateFormField>

      <CreateFormField label="Type" required>
        <SelectOneConceptRenderer
          field={PLACE_TYPE_FIELD}
          value={type}
          readOnly={false}
          required
          organizationId={organizationId}
          onChange={(v) => setType(v as ConceptPick | null)}
        />
      </CreateFormField>

    </CreateFormShell>
  );
}
