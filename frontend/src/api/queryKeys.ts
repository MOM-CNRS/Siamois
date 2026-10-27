import type {ListParams, ListScope} from "../entities/shared/types";

/**
 * Every query key of the app, so that a key and the invalidations that target it are written in
 * one place. Keys are hierarchical: invalidating a prefix (entityList(type)) reaches every key
 * below it (each page, each supplement, the unfiltered total of that type's lists).
 */
export const queryKeys = {
  entityLists: () => ["entity-list"] as const,
  entityList: (entityType: string) => ["entity-list", entityType] as const,
  entityListPage: (entityType: string, params: ListParams) => ["entity-list", entityType, params] as const,
  // The size of the whole collection, or of what a saved view's own filters keep (`filters`).
  entityListTotal: (
    entityType: string,
    organizationId: number | undefined,
    scope: ListScope | undefined,
    filters?: Record<string, unknown>,
  ) => ["entity-list", entityType, "unfiltered-total", organizationId, scope?.entityType, scope?.id, filters ?? null] as const,
  entityListSchema: (entityType: string, organizationId: number | undefined, scope: ListScope | undefined) =>
    ["entity-list-schema", entityType, organizationId, scope?.entityType, scope?.id] as const,

  entityDetails: () => ["entity-detail"] as const,
  entityDetailsOf: (entityType: string) => ["entity-detail", entityType] as const,
  entityDetail: (entityType: string, id: string | number) => ["entity-detail", entityType, id] as const,
  entitySiblings: (entityType: string, id: string | number, organizationId: number | undefined, context: unknown) =>
    ["entity-siblings", entityType, id, organizationId, context ?? null] as const,

  // The relations of a recording unit, grouped by graph zone.
  relationGraph: (id: string | number) => ["relation-graph", id] as const,
  // Every stratigraphic relationship of a unit (a field's values endpoint), for the fiche's editor.
  stratigraphyValues: (id: string | number, fieldId?: string) => (fieldId === undefined ? ["stratigraphy-values", id] : ["stratigraphy-values", id, fieldId]) as readonly unknown[],
  // The relations of a recording unit as a paged list (one entry per set of list parameters).
  relationList: (id: string | number, params?: unknown) =>
    (params === undefined ? ["relation-list", id] : ["relation-list", id, params]) as readonly unknown[],
  // The types of one stratigraphic field: its list depends on the project and on the unit's type.
  stratigraphyTypes: (organizationId: number | undefined, fieldId: string, projectId?: string | null, valueConceptId?: string) =>
    ["stratigraphy-types", organizationId, fieldId, projectId ?? null, valueConceptId ?? null] as const,

  entityBreadcrumbs: (entityType: string, id: string | number, via?: string) =>
    via ? (["entity-breadcrumbs", entityType, id, via] as const) : (["entity-breadcrumbs", entityType, id] as const),

  bookmarks: () => ["bookmark-status"] as const,
  bookmark: (resourceUri: string | undefined, organizationId: number | undefined) =>
    ["bookmark-status", resourceUri, organizationId] as const,

  // The data-access cards of the home page.
  homeAccesses: (organizationId: number | undefined) => ["home-accesses", organizationId] as const,
  // The types one can pick for a table (the options of its type field).
  tableTypeOptions: (organizationId: number | undefined, entityType: string) =>
    ["table-type-options", organizationId, entityType] as const,
  // The pages the user opened lately, on the home page.
  homeHistory: (organizationId: number | undefined) => ["home-history", organizationId] as const,

  // What one user changed on one view (GET /api/v1/view-states): per organization and view key.
  viewState: (organizationId: number | undefined, viewKey: string | undefined) =>
    ["view-state", organizationId, viewKey] as const,

  // How a project arranges the tabs of a record type's fiche (GET /api/v1/fiche-configs).
  ficheConfig: (organizationId: number | undefined, resourceType: string, projectId?: number, typeId?: number) =>
    ["fiche-config", organizationId, resourceType, projectId ?? null, typeId ?? null] as const,

  // The saved views of an organization: the list of a type's (under views(), so one invalidation reaches
  // every search), and one view.
  views: (organizationId: number | undefined) => ["views", organizationId] as const,
  viewList: (organizationId: number | undefined, resourceType: string, q: string) =>
    ["views", organizationId, "list", resourceType, q] as const,
  view: (organizationId: number | undefined, id: number | undefined) => ["views", organizationId, "one", id] as const,

  creatableProjects: (kind: string | undefined, organizationId: number | undefined) =>
    ["creatable-projects", kind, organizationId] as const,
  organizationCounts: (organizationId: number | undefined) => ["organization-counts", organizationId] as const,
  organizationPermissions: (organizationId: number | undefined) => ["organization-permissions", organizationId] as const,
  duplicationStructure: (entityType: string, id: string | number) => ["duplication-structure", entityType, id] as const,

  // The raw types catalog at `path` (GET …/<x>-types): fetched once per path, whoever reads it.
  typesCatalog: (path: string) => ["types-catalog", path] as const,
  // What a consumer derives from a catalog — cached apart only so each keeps its own `select`.
  effectiveForm: (segment: string, projectId: string | number | null | undefined, typeId: string | null | undefined) =>
    ["effective-form", segment, projectId, typeId ?? null] as const,
  typeRules: (segment: string | undefined, projectId: string) => ["type-rules", segment, projectId] as const,
  recordingUnitTypes: (projectId: string | number | null | undefined) => ["recording-unit-types", projectId] as const,
  // The identifier the server would generate for a new entity of a type in a project (a peek, not a
  // consumption): shown pre-filled in the creation form. Not cached — every form opening asks again.
  identifierSuggestion: (entityType: string, projectId: string | number | null | undefined, typeId: string | number | null | undefined) =>
    ["identifier-suggestion", entityType, projectId ?? null, typeId ?? null] as const,
  projectTemplates: (organizationId: string | number | null | undefined) => ["project-templates", organizationId] as const,
  nextProjectIdentifier: (organizationId: string | number | null | undefined) => ["next-project-identifier", organizationId] as const,
  projectTypes: (organizationId: string | number | null | undefined) => ["project-types", organizationId] as const,

  projectHistory: (projectId: string | number | undefined) => ["project-history", projectId] as const,
  projectHomeRecent: (organizationId: number | undefined) => ["project-home-recent", organizationId] as const,
};
