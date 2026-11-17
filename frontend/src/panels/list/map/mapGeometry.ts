import type {FieldResource} from "../../../fields/types";
import type {FilterValue} from "../tableState";

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

/**
 * The filter a switch to the map view adds on the geometry column it draws: « not empty », so the list
 * holds the rows the map can place. None when that column already has a filter (the user's own stands) or
 * the server cannot filter it. Once taken off, the map shows what it always did (its notices about the
 * rows it leaves out).
 */
export function mapGeometryFilter(
  filters: Record<string, FilterValue>,
  geometry: MapGeometryOption | undefined,
  fields: Record<string, FieldResource> | undefined,
): FilterValue | undefined {
  if (!geometry || filters[geometry.value] != null) return undefined;
  if (fields?.[geometry.value]?.query?.filterOp !== "presence") return undefined;
  return { op: "notEmpty" };
}

/**
 * The filters once the map draws `to` instead of `from`: the « not empty » filter of the column it drew moves
 * to the new one. Undefined when nothing moves — that filter was taken off, or the column carries another
 * filter (the user's own, left where it is).
 */
export function filtersForGeometryChange(
  filters: Record<string, FilterValue>,
  from: MapGeometryOption | undefined,
  to: MapGeometryOption | undefined,
  fields: Record<string, FieldResource> | undefined,
): Record<string, FilterValue> | undefined {
  if (!from || !to || from.value === to.value || filters[from.value]?.op !== "notEmpty") return undefined;
  const rest = Object.fromEntries(Object.entries(filters).filter(([key]) => key !== from.value));
  const added = mapGeometryFilter(rest, to, fields);
  return added ? { ...rest, [to.value]: added } : rest;
}
