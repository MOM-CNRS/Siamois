import {getAllEntityTypes, getEntityType} from "../entities/registry";

/** The resource path of an entity (or, without an id, of its list): what the server calls it. */
export function entityPath(entityType: string, id?: string | number): string | undefined {
  const routes = getEntityType(entityType)?.routes;
  if (!routes) return undefined;
  return id != null ? routes.detail(id) : routes.list;
}

export interface ResolvedPath {
  entityType: string;
  // Absent for a list.
  entityId?: string;
  // The saved view a list's path names (`/recording-unit?viewId=12`).
  viewId?: number;
}

/** The saved view a path names (`?viewId=12`); undefined when none, or when it isn't a number. */
export function viewIdOfPath(path: string): number | undefined {
  const query = path.split("?")[1];
  const raw = query ? new URLSearchParams(query).get("viewId") : null;
  const viewId = raw == null ? NaN : Number(raw);
  return Number.isInteger(viewId) && viewId > 0 ? viewId : undefined;
}

/** The path of an entity type's list, on a saved view when one is given. */
export function listPath(entityType: string, viewId?: number): string | undefined {
  const path = entityPath(entityType);
  return path != null && viewId != null ? `${path}?viewId=${viewId}` : path;
}

/**
 * The inverse of entityPath: `/action-unit/12` is the project 12, `/action-unit` the projects list.
 * Of the query string only a list's ?viewId= is read (the former ?tab=, still in old bookmarks, is ignored,
 * as FocusViewBean does).
 */
export function resolveEntityPath(path: string): ResolvedPath | null {
  const [segment, id, ...rest] = path.split("?", 1)[0].replace(/^\//, "").split("/");
  if (!segment || rest.length > 0) return null;
  const config = getAllEntityTypes().find((c) => c.routes.list === `/${segment}`);
  if (!config) return null;
  if (id) return { entityType: config.key, entityId: id };
  const viewId = viewIdOfPath(path);
  return viewId != null ? { entityType: config.key, viewId } : { entityType: config.key };
}
