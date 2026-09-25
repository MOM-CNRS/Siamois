import { apiFetch } from "../api/client";
import type { FieldResource } from "../fields/types";
import { scopeProjectId } from "./scope";
import type { FieldCatalog, ListScope } from "./types";

interface TypesResponseBody {
  data?: { fields?: Record<string, FieldResource> }[];
  _default?: { fields?: Record<string, FieldResource> };
}

/**
 * Where a list's column catalog lives: its project's per-type forms (GET /api/v1/projects/{id}/
 * <segment>), or — for an organization-wide list — the organization's aggregate of every project's
 * forms (GET /api/v1/organizations/{id}/<segment>). A scoped list with no project (none today for
 * these entities) has no catalog.
 */
export function typeCatalogPath(ctx: { organizationId?: number; scope?: ListScope }, segment: string): string | undefined {
  const projectId = scopeProjectId(ctx.scope);
  if (projectId != null) return `/api/v1/projects/${projectId}/${segment}`;
  if (!ctx.scope && ctx.organizationId != null) return `/api/v1/organizations/${ctx.organizationId}/${segment}`;
  return undefined;
}

/**
 * A list's column catalog built from per-type forms (phase-types, container-types, find-types —
 * see typeCatalogPath): every field any of its types shows — system fields and each type's
 * additional ones — as a toggleable column, hidden until picked. Each field carries what the list
 * accepts on it (FieldResource.query).
 */
export async function loadTypeCatalog(
  ctx: { organizationId?: number; scope?: ListScope },
  segment: string,
): Promise<FieldCatalog> {
  const path = typeCatalogPath(ctx, segment);
  if (path == null) return { fields: {}, columns: [] };
  const body = await apiFetch<TypesResponseBody>(path);
  const fields: Record<string, FieldResource> = {};
  // System fields first, then each type's additional ones. JSON objects put integer-like keys (the
  // additional fields' positive ids) ahead of the rest whatever the server's order, hence the list
  // and the explicit (stable) sort.
  const order: string[] = [];
  for (const source of [body._default?.fields, ...(body.data ?? []).map((t) => t.fields)]) {
    for (const field of Object.values(source ?? {})) {
      if (!(field.id in fields)) order.push(field.id);
      fields[field.id] = field;
    }
  }
  order.sort((a, b) => Number(fields[b].isSystemField) - Number(fields[a].isSystemField));
  return {
    fields,
    columns: order.map((fieldId, index) => ({ fieldId, columnId: fieldId, visible: false, order: index })),
  };
}
