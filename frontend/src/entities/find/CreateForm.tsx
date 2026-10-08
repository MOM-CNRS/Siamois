import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { Message } from "primereact/message";
import { CreateFormField, CreateFormShell } from "../../components/CreateFormShell";
import { CreateLinkField } from "../../components/CreateLinkField";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import { useDeclaredTypes } from "../useDeclaredTypes";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { fetchList } from "../listApi";
import { createFind } from "./api";
import { getEffectiveForm } from "../typeCatalog";
import { queryKeys } from "../../api/queryKeys";
import { useCreateProject } from "../../components/useCreateProject";
import { messageForError } from "../../api/errors";
import { t } from "../../i18n";

// The "Mobilier" relation tab's own "Créer" overlay (migration plan follow-up — see
// entities/project/CreateForm.tsx for the overlay-not-dialog rationale). Unlike Project's and
// RecordingUnit's own create forms, this one needs TWO pickers, not one: FindCreateRequest is
// created ON a recording unit (`recordingUnitId`), but this tab — like the rest of the project
// fiche — is scoped by PROJECT, not by any one UE. So the form itself has to let the user find
// the UE within this project first, via a plain search-as-you-type over
// GET /api/v1/projects/{id}/recording-units (scoped the same way this tab's own list already is) — there is no dedicated
// "recording units of a project" autocomplete endpoint, and building the whole list page for 20
// rows would be overkill for what's a small, bounded set per project in practice.
//
// The category picker below is deliberately resolved by `valueBinding === "category"`, NOT
// "type": Specimen's own form has no field bound to "type" at all — FindCreateRequest.typeId
// actually writes SpecimenDTO.type, but the concept VOCABULARY it's drawn from (and the one
// GET /api/v1/projects/{id}/find-types enumerates configured types against) is
// Specimen.CAT_FIELD ("category" in the field catalog, ConfigurableTable.MOBILIER's own
// fieldCode) — a genuine naming quirk in the domain model itself, not a bug introduced here.
interface ConceptPick {
  resourceId: string;
  resourceType: string;
  label?: string | null;
}

// The fields of a recording unit row the picker needs.
interface RecordingUnitOption {
  id: string | number;
  fullIdentifier?: string | null;
}

export function FindCreateForm({ organizationId, scope, prefill, onCreated, onCancel }: CreateFormContext) {
  // The list's own project, or — on an organization-wide list — the one picked first in the form.
  const { projectId, picker: projectPicker } = useCreateProject({ scope, organizationId, kind: "find" });
  // Created from a recording unit (its row action, its "Mobilier" tab): that UE, no picker.
  const fixedRecordingUnit = prefill?.recordingUnit;
  const [recordingUnit, setRecordingUnit] = useState<RecordingUnitOption | null>(null);
  const recordingUnitId = fixedRecordingUnit?.id ?? recordingUnit?.id;
  const [ruQuery, setRuQuery] = useState("");
  const [ruSuggestions, setRuSuggestions] = useState<RecordingUnitOption[]>([]);
  const [category, setCategory] = useState<ConceptPick | null>(null);
  // Categories and recording units are per project: a pick from the previous project no longer applies.
  useEffect(() => {
    setCategory(null);
    setRecordingUnit(null);
    setRuQuery("");
  }, [projectId]);
  const [error, setError] = useState<string | null>(null);
  const autoCompleteRef = useRef<AutoComplete>(null);

  const typesQuery = useQuery({
    queryKey: queryKeys.effectiveForm("find-types", projectId, null),
    queryFn: () => getEffectiveForm("find-types", projectId as string, null),
    enabled: projectId != null,
  });

  const declaredCategories = useDeclaredTypes("find-types", projectId);

  const categoryField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "category"),
    [typesQuery.data],
  );

  async function searchRecordingUnits(e: AutoCompleteCompleteEvent) {
    if (projectId == null) return;
    const result = await fetchList<RecordingUnitOption>("recording-units", {
      offset: 0,
      limit: 20,
      search: e.query || undefined,
      scope: { entityType: "project", id: projectId },
    });
    setRuSuggestions(result.data);
  }

  const mutation = useMutation({
    mutationFn: () => createFind({ recordingUnitId: String(recordingUnitId), typeId: category!.resourceId }),
    onSuccess: (created) => onCreated(created.id),
    onError: (err: unknown) => {
      setError(messageForError(err, t("create.failed")));
    },
  });

  const canSubmit = projectId != null && recordingUnitId != null && category != null && !mutation.isPending;

  return (
    <CreateFormShell
      entityType="find"
      title={t("create.newFind")}
      canSubmit={canSubmit}
      pending={mutation.isPending}
      error={error}
      onSubmit={() => mutation.mutate()}
      onCancel={onCancel}
    >
      {projectPicker}
      {projectId == null && !projectPicker && <Message severity="warn" text={t("create.unknownProject")} />}

      {fixedRecordingUnit ? (
        <CreateLinkField label={t("entity.recordingUnit.singular")} entityType="recordingUnit" value={fixedRecordingUnit} />
      ) : (
        <CreateFormField label={t("entity.recordingUnit.singular")} required>
          <AutoComplete
            ref={autoCompleteRef}
            value={ruQuery}
            suggestions={ruSuggestions}
            field="fullIdentifier"
            onChange={(e) => {
              // A free-typed string clears the selection; picking a suggestion sets both.
              if (typeof e.value === "string") {
                setRuQuery(e.value);
                setRecordingUnit(null);
              } else {
                setRecordingUnit(e.value as RecordingUnitOption);
                setRuQuery((e.value as RecordingUnitOption).fullIdentifier ?? "");
              }
            }}
            completeMethod={searchRecordingUnits}
            onFocus={(e) => autoCompleteRef.current?.search(e, e.currentTarget.value ?? "", "dropdown")}
            placeholder={projectId == null ? t("create.chooseProjectFirst") : t("create.searchRecordingUnit")}
            disabled={projectId == null}
          />
        </CreateFormField>
      )}

      <CreateFormField label={t("common.category")} required>
        {categoryField ? (
          <SelectOneConceptRenderer
            field={categoryField}
            value={category}
            readOnly={false}
            required
            organizationId={organizationId}
            declaredOptions={declaredCategories}
            onChange={(v) => setCategory(v as ConceptPick | null)}
          />
        ) : (
          <span className="sia-create-form-hint">{projectId == null ? t("create.chooseProjectFirst") : t("common.loading")}</span>
        )}
      </CreateFormField>

    </CreateFormShell>
  );
}
