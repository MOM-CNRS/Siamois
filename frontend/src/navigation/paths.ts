import { getAllEntityTypes, getEntityType } from "../entities/registry";

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
}

/**
 * The inverse of entityPath: `/action-unit/12` is the project 12, `/action-unit` the projects list.
 * The query string (the former ?tab=, still in old bookmarks) is ignored, as FocusViewBean does.
 */
export function resolveEntityPath(path: string): ResolvedPath | null {
  const [segment, id, ...rest] = path.split("?", 1)[0].replace(/^\//, "").split("/");
  if (!segment || rest.length > 0) return null;
  const config = getAllEntityTypes().find((c) => c.routes.list === `/${segment}`);
  if (!config) return null;
  return id ? { entityType: config.key, entityId: id } : { entityType: config.key };
}
