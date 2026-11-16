import type {FieldResource} from "../../../fields/types";

/**
 * A column a list's map can place its rows by: a field of type geometry — the entity's own geometry
 * (a system field) or an additional one. `value` is the field id the map endpoint takes (`geometry=`).
 */
export interface MapGeometryOption {
  value: string;
  label: string;
  icon: string;
}

const GEOMETRY = "GEOMETRY";
const ICON = "bi bi-bounding-box-circles";

/** The geometry columns of a list's field catalog: the system one first (the default), then the additional ones. */
export function mapGeometryOptions(fields: Record<string, FieldResource> | undefined): MapGeometryOption[] {
  return Object.values(fields ?? {})
    .filter((f) => f.answerType === GEOMETRY)
    .sort((a, b) => Number(b.isSystemField === true) - Number(a.isSystemField === true))
    .map((f) => ({ value: String(f.id), label: f.label, icon: ICON }));
}

/** The column to use: the one chosen when it is still offered, else the first (the entity's own geometry). */
export function resolveMapGeometry(chosen: string | undefined, options: readonly MapGeometryOption[]): MapGeometryOption | undefined {
  return options.find((o) => o.value === chosen) ?? options[0];
}
