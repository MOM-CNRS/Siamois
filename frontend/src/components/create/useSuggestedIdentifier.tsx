import {type ReactNode, useEffect, useState} from "react";
import {useQuery} from "@tanstack/react-query";
import {InputText} from "primereact/inputtext";
import {apiFetch} from "../../api/client";
import {queryKeys} from "../../api/queryKeys";
import type {CreatableKind} from "../../entities/shared/types";
import {CreateFormField} from "./CreateFormShell";
import {t} from "../../i18n";

// Every creatable kind has a generated identifier, suggested by the one endpoint for all of them,
// named by the kind's collection.
const COLLECTIONS: Record<CreatableKind, string> = {
  recordingUnit: "recording-units",
  find: "finds",
  phase: "phases",
  container: "containers",
  document: "documents",
  study: "studies",
};

// GET /api/v1/identifier-suggestions — the identifier the server would generate for a new entity of
// this kind and type, without consuming the counter. Created in a project, except a find, created on
// a recording unit. Null when the project configures no format (the server then names it at creation).
// A recording unit created under another one (`parentRecordingUnitId`) has its parent's tokens rendered —
// nothing is linked by the suggestion, the form may still be cancelled.
export async function suggestIdentifier(
  kind: CreatableKind,
  scopeId: string | number,
  typeId: string | number,
  parentRecordingUnitId?: string | number | null,
): Promise<string | null> {
  const params = new URLSearchParams({
    entityType: COLLECTIONS[kind],
    [kind === "find" ? "recordingUnitId" : "projectId"]: String(scopeId),
    typeId: String(typeId),
  });
  if (parentRecordingUnitId != null) params.set("parentRecordingUnitId", String(parentRecordingUnitId));
  const body = await apiFetch<{ suggestedIdentifier: string | null }>(`/api/v1/identifier-suggestions?${params}`);
  return body.suggestedIdentifier ?? null;
}

/**
 * The identifier field of a creation form (like entities/project/CreateForm.tsx's): the suggestion is
 * shown until the user types, then their value is — an edited-to-empty one means "let the server
 * generate". `chosen` is what to spread into the creation body: the typed identifier, or nothing.
 *
 * @param scopeId the project the entity is created in (the recording unit, for a find)
 * @param parentRecordingUnitId the recording unit a new one is created under, if any
 */
export function useSuggestedIdentifier(
  kind: CreatableKind,
  scopeId: string | number | null | undefined,
  typeId: string | null | undefined,
  parentRecordingUnitId?: string | number | null,
): { field: ReactNode; chosen: { identifier?: string } } {
  const [identifier, setIdentifier] = useState("");
  const [edited, setEdited] = useState(false);
  // A change of scope, type or parent is a new suggestion: an edit made for the previous one no longer applies.
  useEffect(() => {
    setIdentifier("");
    setEdited(false);
  }, [scopeId, typeId, parentRecordingUnitId]);

  const suggestion = useQuery({
    queryKey: queryKeys.identifierSuggestion(kind, scopeId, typeId, parentRecordingUnitId),
    queryFn: () => suggestIdentifier(kind, scopeId!, typeId!, parentRecordingUnitId),
    enabled: scopeId != null && typeId != null,
    // Not cached: every form opening asks again, the counter may have moved.
    staleTime: 0,
    gcTime: 0,
  });

  const field = (
    <CreateFormField label={t("common.identifier")}>
      <InputText
        value={edited ? identifier : (suggestion.data ?? "")}
        placeholder={t("create.identifier.auto")}
        onChange={(e) => {
          setIdentifier(e.target.value);
          setEdited(true);
        }}
      />
    </CreateFormField>
  );
  return { field, chosen: edited && identifier.trim() !== "" ? { identifier: identifier.trim() } : {} };
}
