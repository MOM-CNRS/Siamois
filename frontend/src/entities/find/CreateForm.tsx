import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { Button } from "primereact/button";
import { Message } from "primereact/message";
import { ApiError } from "../../api/client";
import { CreateLinkField } from "../../components/CreateLinkField";
import { SelectOneConceptRenderer } from "../../fields/renderers";
import type { FieldResource } from "../../fields/types";
import type { CreateFormContext } from "../types";
import { listRecordingUnits } from "../recordingUnit/api";
import type { RecordingUnitSummary } from "../recordingUnit/types";
import { createFind } from "./api";
import { getFindEffectiveForm } from "./findTypes";
import { useCreateProject } from "../../components/useCreateProject";

// The "Mobilier" relation tab's own "Créer" overlay (migration plan follow-up — see
// entities/project/CreateForm.tsx for the overlay-not-dialog rationale). Unlike Project's and
// RecordingUnit's own create forms, this one needs TWO pickers, not one: FindCreateRequest is
// created ON a recording unit (`recordingUnitId`), but this tab — like the rest of the project
// fiche — is scoped by PROJECT, not by any one UE. So the form itself has to let the user find
// the UE within this project first, via a plain search-as-you-type over
// GET /api/v1/projects/{id}/recording-units (entities/recordingUnit/api.ts's own listRecordingUnits,
// scoped the same way this tab's own list already is) — there is no dedicated
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

export function FindCreateForm({ organizationId, scope, prefill, onCreated, onCancel }: CreateFormContext) {
  // The list's own project, or — on an organization-wide list — the one picked first in the form.
  const { projectId, picker: projectPicker } = useCreateProject({ scope, organizationId, kind: "find" });
  // Created from a recording unit (its row action, its "Mobilier" tab): that UE, no picker.
  const fixedRecordingUnit = prefill?.recordingUnit;
  const [recordingUnit, setRecordingUnit] = useState<RecordingUnitSummary | null>(null);
  const recordingUnitId = fixedRecordingUnit?.id ?? recordingUnit?.id;
  const [ruQuery, setRuQuery] = useState("");
  const [ruSuggestions, setRuSuggestions] = useState<RecordingUnitSummary[]>([]);
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
    queryKey: ["find-effective-form", projectId, null],
    queryFn: () => getFindEffectiveForm(projectId as string, null),
    enabled: projectId != null,
  });

  const categoryField = useMemo<FieldResource | undefined>(
    () => Object.values(typesQuery.data?.fields ?? {}).find((f) => f.valueBinding === "category"),
    [typesQuery.data],
  );

  async function searchRecordingUnits(e: AutoCompleteCompleteEvent) {
    if (projectId == null) return;
    const result = await listRecordingUnits({
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
      setError(err instanceof ApiError ? err.message : "Échec de la création");
    },
  });

  const canSubmit = projectId != null && recordingUnitId != null && category != null && !mutation.isPending;

  return (
    <form
      className="project-create-form"
      style={{ display: "flex", flexDirection: "column", gap: "0.75em", minWidth: "22em" }}
      onSubmit={(e) => {
        e.preventDefault();
        if (canSubmit) mutation.mutate();
      }}
    >
      <h4 style={{ margin: 0 }}>Nouveau mobilier</h4>

      {projectPicker}
      {projectId == null && !projectPicker && <Message severity="warn" text="Projet inconnu : création impossible" />}

      {fixedRecordingUnit ? (
        <CreateLinkField label="Unité d'enregistrement" entityType="recordingUnit" value={fixedRecordingUnit} />
      ) : (
        <label className="project-create-form-field">
          <span>Unité d'enregistrement</span>
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
                setRecordingUnit(e.value as RecordingUnitSummary);
                setRuQuery((e.value as RecordingUnitSummary).fullIdentifier ?? "");
              }
            }}
            completeMethod={searchRecordingUnits}
            onFocus={(e) => autoCompleteRef.current?.search(e, e.currentTarget.value ?? "", "dropdown")}
            placeholder={projectId == null ? "Choisissez d'abord un projet" : "Rechercher une UE…"}
            disabled={projectId == null}
          />
        </label>
      )}

      <label className="project-create-form-field">
        <span>Catégorie</span>
        {categoryField ? (
          <SelectOneConceptRenderer
            field={categoryField}
            value={category}
            readOnly={false}
            required
            organizationId={organizationId}
            onChange={(v) => setCategory(v as ConceptPick | null)}
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
