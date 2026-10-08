import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { InputText } from "primereact/inputtext";
import { CreateFormField, CreateFormShell } from "../../components/CreateFormShell";
import { CreateLinkField } from "../../components/CreateLinkField";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { createProject } from "./api";
import { getProjectTypes } from "./projectTypes";
import { queryKeys } from "../../api/queryKeys";
import { messageForError } from "../../api/errors";
import { t } from "../../i18n";

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
    queryKey: queryKeys.projectTypes(organizationId),
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
      setError(messageForError(err, t("create.failed")));
    },
  });

  const canSubmit =
    organizationId != null && name.trim() !== "" && identifier.trim() !== "" && type != null && !mutation.isPending;

  return (
    <CreateFormShell
      entityType="project"
      title={t("create.newProject")}
      canSubmit={canSubmit}
      pending={mutation.isPending}
      error={error}
      onSubmit={() => mutation.mutate()}
      onCancel={onCancel}
    >
      {prefill?.spatialContext && <CreateLinkField label={t("common.place")} entityType="place" value={prefill.spatialContext} />}

      <CreateFormField label={t("common.name")} required>
        <InputText value={name} onChange={(e) => setName(e.target.value)} required />
      </CreateFormField>

      <CreateFormField label={t("common.identifier")} required>
        <InputText value={identifier} onChange={(e) => setIdentifier(e.target.value)} required />
      </CreateFormField>

      <CreateFormField label={t("common.type")} required>
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
          <span>{t("common.loading")}</span>
        )}
      </CreateFormField>

    </CreateFormShell>
  );
}
