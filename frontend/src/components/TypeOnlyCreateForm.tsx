import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Message } from "primereact/message";
import { queryKeys } from "../api/queryKeys";
import { getEffectiveForm } from "../entities/typeCatalog";
import type { CreatableKind, CreateFormContext } from "../entities/types";
import { SelectOneConceptRenderer } from "../fields/renderers";
import { useDeclaredTypes } from "../entities/useDeclaredTypes";
import type { FieldResource } from "../fields/types";
import { CreateFormField, CreateFormShell } from "./CreateFormShell";
import { useCreateProject } from "./useCreateProject";
import { messageForError } from "../api/errors";
import { t } from "../i18n";

interface ConceptPick {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

export interface TypeOnlyCreateFormProps extends CreateFormContext {
  entityType: CreatableKind;
  title: string;
  // The project catalog whose untyped form carries the type field (valueBinding "type").
  typesSegment: string;
  // The valueBinding of that type field: "type" unless the entity names its type differently
  // (a document's is its "category").
  typeBinding?: string;
  create: (projectId: string, typeId: string) => Promise<{ id: string | number }>;
  // Read-only lines under the type: what the creation is linked to (a prefilled parent…).
  children?: ReactNode;
}

/**
 * The creation overlay of an entity created in a project with nothing but its type (phase,
 * container, recording unit): the server generates the identifier, and every other field is edited
 * afterwards on the fiche the overlay opens. The project is the list's own, or picked first in the
 * form on an organization-wide list.
 */
export function TypeOnlyCreateForm({
  entityType,
  title,
  typesSegment,
  typeBinding = "type",
  create,
  children,
  organizationId,
  scope,
  onCreated,
  onCancel,
}: TypeOnlyCreateFormProps) {
  const { projectId, picker: projectPicker } = useCreateProject({ scope, organizationId, kind: entityType });
  const [type, setType] = useState<ConceptPick | null>(null);
  // Types are per project: a pick from the previous project's catalog no longer applies.
  useEffect(() => {
    setType(null);
  }, [projectId]);
  const [error, setError] = useState<string | null>(null);

  const formQuery = useQuery({
    queryKey: queryKeys.effectiveForm(typesSegment, projectId, null),
    queryFn: () => getEffectiveForm(typesSegment, projectId as string, null),
    enabled: projectId != null,
  });
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(formQuery.data?.fields ?? {}).find((f) => f.valueBinding === typeBinding),
    [formQuery.data, typeBinding],
  );

  const declaredTypes = useDeclaredTypes(typesSegment, projectId);

  const mutation = useMutation({
    mutationFn: () => create(projectId as string, type!.resourceId),
    onSuccess: (created) => onCreated(created.id),
    onError: (err: unknown) => setError(messageForError(err, t("create.failed"))),
  });

  const canSubmit = projectId != null && type != null && !mutation.isPending;

  return (
    <CreateFormShell
      entityType={entityType}
      title={title}
      canSubmit={canSubmit}
      pending={mutation.isPending}
      error={error}
      onSubmit={() => mutation.mutate()}
      onCancel={onCancel}
    >
      {projectPicker}
      {projectId == null && !projectPicker && <Message severity="warn" text={t("create.unknownProject")} />}

      <CreateFormField label={t("common.type")} required>
        {typeField ? (
          <SelectOneConceptRenderer
            field={typeField}
            value={type}
            readOnly={false}
            required
            organizationId={organizationId}
            declaredOptions={declaredTypes}
            onChange={(v) => setType(v as ConceptPick | null)}
          />
        ) : (
          <span className="sia-create-form-hint">{projectId == null ? t("create.chooseProjectFirst") : t("common.loading")}</span>
        )}
      </CreateFormField>

      {children}
    </CreateFormShell>
  );
}
