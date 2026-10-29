import {useQuery} from "@tanstack/react-query";
import {apiFetch} from "../api/client";
import {queryKeys} from "../api/queryKeys";
import type {FormLayoutPanel} from "./layout";
import type {FieldResource} from "./types";

// The fields a user adds to ONE record's fiche ("Ajouter une mesure"): server-side custom fields that
// belong to that record and sit in one of its panels. The type catalog's form is shared by every
// record of the type, so they come apart (GET …/on-the-fly-fields) and are merged into it here.

/** The URL segment of every kind of record that can hold such fields. */
const SEGMENTS: Record<string, string> = {
  recordingUnit: "recording-units",
  find: "finds",
  phase: "phases",
  container: "containers",
  document: "documents",
  study: "studies",
};

export const onTheFlySegment = (entityType: string): string | undefined => SEGMENTS[entityType];

export interface OnTheFlyFields {
  fields: Record<string, FieldResource>;
  placements: { fieldId: string; panel?: string | null }[];
}

export interface ConceptOption {
  id: number;
  label: string;
}

export interface UnitOption {
  id: number;
  label: string;
  symbol: string;
  dimension?: string | null;
  systemBase?: boolean;
}

export interface ExistingMeasure {
  fieldId: number;
  label: string;
  unit?: string | null;
}

export type MeasurementRequest = { panel: string } & (
  // A measure already used on another record of the table, taken as it is.
  | { copyFieldId: number }
  | {
      // What is measured / its nature: an existing concept, or the name of a new local one.
      typeConceptId?: number | null;
      newTypeLabel?: string | null;
      natureConceptId?: number | null;
      newNatureLabel?: string | null;
      defaultUnitId: number;
      allowedUnitIds: number[];
    }
);

const base = (segment: string, id: string | number) => `/api/v1/${segment}/${id}/on-the-fly-fields`;

export const getOnTheFlyFields = (segment: string, id: string | number) => apiFetch<OnTheFlyFields>(base(segment, id));

export const addMeasurement = (segment: string, id: string | number, body: MeasurementRequest) =>
  apiFetch<OnTheFlyFields>(base(segment, id), { method: "POST", body });

export const removeOnTheFlyField = (segment: string, id: string | number, fieldId: string) =>
  apiFetch<OnTheFlyFields>(`${base(segment, id)}/${fieldId}`, { method: "DELETE" });

export const searchMeasurementConcepts = (segment: string, id: string | number, kind: "TYPE" | "NATURE", q: string) =>
  apiFetch<ConceptOption[]>(`${base(segment, id)}/measurement-concepts?kind=${kind}&q=${encodeURIComponent(q)}`);

export const searchExistingMeasurements = (segment: string, id: string | number, q: string) =>
  apiFetch<ExistingMeasure[]>(`${base(segment, id)}/existing-measurements?q=${encodeURIComponent(q)}`);

export const getMeasurementUnits = (segment: string, id: string | number) =>
  apiFetch<UnitOption[]>(`${base(segment, id)}/measurement-units`);

/** The record's on-the-fly fields; disabled (and empty) for a kind of record that has none or a record not yet saved. */
export function useOnTheFlyFields(entityType: string, id: string | number | undefined) {
  const segment = onTheFlySegment(entityType);
  const query = useQuery({
    queryKey: queryKeys.onTheFlyFields(segment, id),
    queryFn: () => getOnTheFlyFields(segment!, id!),
    enabled: segment != null && id != null,
  });
  return { segment, ...query };
}

/**
 * The form with the record's own fields added to the panels they were added to (the last panel for a
 * field without one, or whose panel is gone) — a fresh layout and catalog, never the shared ones.
 */
export function withOnTheFlyFields(
  panels: FormLayoutPanel[],
  fields: Record<string, FieldResource>,
  extra: OnTheFlyFields | undefined,
): { panels: FormLayoutPanel[]; fields: Record<string, FieldResource>; onTheFlyIds: Set<string> } {
  const placements = extra?.placements ?? [];
  if (placements.length === 0 || panels.length === 0) return { panels, fields, onTheFlyIds: new Set() };

  const names = new Set(panels.map((p) => p.name));
  const lastName = panels[panels.length - 1].name;
  const byPanel = new Map<string, string[]>();
  for (const { fieldId, panel } of placements) {
    const name = panel != null && names.has(panel) ? panel : lastName;
    byPanel.set(name, [...(byPanel.get(name) ?? []), fieldId]);
  }
  return {
    panels: panels.map((panel) => {
      const ids = byPanel.get(panel.name);
      if (!ids) return panel;
      const columns = ids.map((fieldId) => ({ width: { span: 12, md: 6, lg: 3 }, isRequired: false, isReadOnly: false, fieldId }));
      return { ...panel, rows: [...panel.rows, { columns }] };
    }),
    fields: { ...fields, ...extra!.fields },
    onTheFlyIds: new Set(placements.map((p) => p.fieldId)),
  };
}
