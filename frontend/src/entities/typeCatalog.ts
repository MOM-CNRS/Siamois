import { apiFetch } from "../api/client";
import { queryClient } from "../api/queryClient";
import { queryKeys } from "../api/queryKeys";
import type { FieldResource } from "../fields/types";
import type { ProjectTableColumnDefault } from "./common";
import { scopeProjectId } from "./scope";
import type { FieldCatalog, ListScope } from "./types";

// One type's form in a types catalog (GET …/<x>-types): its layout and the fields it places.
export interface TypeFormBody {
  formBundle?: { resourceType?: string; layoutJson: string } | null;
  fields?: Record<string, FieldResource>;
}

/**
 * The body of every types catalog — projects/{id}/<x>-types and organizations/{id}/<x>-types: one
 * form per configured type (`data`), the form of an untyped entity (`_default`, with the list's
 * default columns where the entity has some), and for some catalogs the union of every type's
 * fields (`fields`). An organization's catalog has no per-type entries.
 */
export interface TypesCatalogBody {
  data?: (TypeFormBody & { id: string })[];
  _default?: TypeFormBody & { tableColumns?: ProjectTableColumnDefault[] };
  fields?: Record<string, FieldResource>;
}

/**
 * A types catalog, fetched once per path for the whole session whoever asks — the list's columns,
 * its cell rules, the fiche's form and the create form all read the same one. Types and forms are
 * configured in the settings pages, outside this app: they don't change under an open page.
 */
export function fetchTypesCatalog(path: string): Promise<TypesCatalogBody> {
  return queryClient.fetchQuery({
    queryKey: queryKeys.typesCatalog(path),
    queryFn: () => apiFetch<TypesCatalogBody>(path),
    staleTime: Infinity,
  });
}

export interface EffectiveForm {
  layoutJson: string;
  fields: Record<string, FieldResource>;
}

/** The form of an entity of type `typeId` — its type's own, else the untyped (`_default`) one. */
function effectiveFormOf(body: TypesCatalogBody, typeId: string | null | undefined): EffectiveForm {
  const match = typeId != null ? body.data?.find((t) => t.id === typeId) : undefined;
  const effective = match ?? body._default;
  return { layoutJson: effective?.formBundle?.layoutJson ?? "", fields: effective?.fields ?? {} };
}

/** The form of an entity of type `typeId` in its project, from the project's `segment` catalog. */
export async function getEffectiveForm(
  segment: string,
  projectId: string | number,
  typeId: string | null | undefined,
): Promise<EffectiveForm> {
  return effectiveFormOf(await fetchTypesCatalog(`/api/v1/projects/${projectId}/${segment}`), typeId);
}

/**
 * Where a list's column catalog lives: its project's per-type forms (GET /api/v1/projects/{id}/
 * <segment>), or — for an organization-wide list — the organization's aggregate of every project's
 * forms (GET /api/v1/organizations/{id}/<segment>). A scoped list with no project (none today for
 * these entities) has no catalog.
 */
function typeCatalogPath(ctx: { organizationId?: number; scope?: ListScope }, segment: string): string | undefined {
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
  const body = await fetchTypesCatalog(path);
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
