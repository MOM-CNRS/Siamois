import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { InputText } from "primereact/inputtext";
import { queryKeys } from "../../api/queryKeys";
import { getEffectiveForm } from "../../entities/shared/typeCatalog";
import type { CreatableKind, CreateFormContext } from "../../entities/shared/types";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import { referenceTargetOf } from "../../fields/optionSources";
import { useDeclaredTypesState } from "../../entities/shared/useDeclaredTypes";
import { NoTypeHint } from "./NoTypeHint";
import type { FieldResource } from "../../fields/types";
import { CreateFormField, CreateFormShell } from "./CreateFormShell";
import { useCreateProject } from "./useCreateProject";
import { messageForError } from "../../api/errors";
import { t } from "../../i18n";
import { useToastOnChange } from "../../notify/NotifyProvider";

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
  // The identifier is the entity's auto-generated one, shown as a modifiable suggestion when
  // `suggestIdentifier` is set: sent only when the user typed another one.
  create: (projectId: string, typeId: string, identifier?: string) => Promise<{ id: string | number }>;
  // The identifier the server would generate for a new entity of this type in this project, without
  // consuming it. When set, an editable identifier field appears under the type, pre-filled with it.
  suggestIdentifier?: (projectId: string, typeId: string) => Promise<string>;
  // Read-only lines under the type: what the creation is linked to (a prefilled parent…).
  children?: ReactNode;
}

/**
 * The creation overlay of an entity created in a project with nothing but its type (phase,
 * container, recording unit): the server generates the identifier — shown as a modifiable
 * suggestion when `suggestIdentifier` is set — and every other field is edited afterwards on the
 * fiche the overlay opens. The project is the list's own, or picked first in the form on an
 * organization-wide list.
 */
export function TypeOnlyCreateForm({
  entityType,
  title,
  typesSegment,
  create,
  suggestIdentifier,
  children,
  organizationId,
  scope,
  prefill,
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
  // The identifier field, like entities/project/CreateForm.tsx's: the suggestion is shown until
  // the user types, then their value is — an edited-to-empty one means "let the server generate".
  const [identifier, setIdentifier] = useState("");
  const [identifierEdited, setIdentifierEdited] = useState(false);
  // The suggestion depends on the project AND the type: a change of either is a new suggestion,
  // and an edit made for the previous pair no longer applies.
  const typeId = type?.resourceId ?? null;
  useEffect(() => {
    setIdentifier("");
    setIdentifierEdited(false);
  }, [projectId, typeId]);

  const suggestionQuery = useQuery({
    queryKey: queryKeys.identifierSuggestion(entityType, projectId, typeId),
    queryFn: () => suggestIdentifier!(projectId as string, typeId as string),
    enabled: suggestIdentifier != null && projectId != null && typeId != null,
    staleTime: 0,
    gcTime: 0,
  });
  const shownIdentifier = identifierEdited ? identifier : (suggestionQuery.data ?? "");

  const formQuery = useQuery({
    queryKey: queryKeys.effectiveForm(typesSegment, projectId, null),
    queryFn: () => getEffectiveForm(typesSegment, projectId as string, null),
    enabled: projectId != null,
  });
  const typeField = useMemo<FieldResource | undefined>(
    () => Object.values(formQuery.data?.fields ?? {}).find((f) => f.valueBinding === "type"),
    [formQuery.data],
  );

  const { options: declaredTypes, loaded: typesLoaded } = useDeclaredTypesState(typesSegment, projectId);

  // Created from a list tab filtered on one type: that type is picked already (still changeable).
  const prefilledType = prefill?.typeId;
  const appliedPrefill = useRef<string | null>(null);
  useEffect(() => {
    appliedPrefill.current = null;
  }, [projectId]);
  useEffect(() => {
    if (prefilledType == null || appliedPrefill.current === prefilledType || !typeField || !typesLoaded) return;
    const option = declaredTypes.find((o) => o.id === prefilledType);
    appliedPrefill.current = prefilledType;
    if (option) setType({ resourceId: option.id, resourceType: referenceTargetOf(typeField).resourceType, label: option.label });
  }, [prefilledType, typeField, typesLoaded, declaredTypes]);

  const mutation = useMutation({
    mutationFn: () =>
      create(projectId as string, type!.resourceId, identifierEdited && identifier.trim() !== "" ? identifier.trim() : undefined),
    onSuccess: (created) => onCreated(created.id),
    onError: (err: unknown) => setError(messageForError(err, t("create.failed"))),
  });

  const canSubmit = projectId != null && type != null && !mutation.isPending;

  useToastOnChange(projectId == null && !projectPicker ? t("create.unknownProject") : null, "warn");
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

      <CreateFormField label={t("common.type")} required>
        {projectId != null && typesLoaded && declaredTypes.length === 0 ? (
          <NoTypeHint kind={entityType} projectId={projectId} />
        ) : typeField ? (
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

      {suggestIdentifier != null && (
        <CreateFormField label={t("common.identifier")}>
          <InputText
            value={shownIdentifier}
            placeholder={t("create.identifier.auto")}
            onChange={(e) => {
              setIdentifier(e.target.value);
              setIdentifierEdited(true);
            }}
          />
        </CreateFormField>
      )}

      {children}
    </CreateFormShell>
  );
}
