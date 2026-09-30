import type { ListParams, ListScope } from "../entities/types";

/**
 * Every query key of the app, so that a key and the invalidations that target it are written in
 * one place. Keys are hierarchical: invalidating a prefix (entityList(type)) reaches every key
 * below it (each page, each supplement, the unfiltered total of that type's lists).
 */
export const queryKeys = {
  entityLists: () => ["entity-list"] as const,
  entityList: (entityType: string) => ["entity-list", entityType] as const,
  entityListPage: (entityType: string, params: ListParams) => ["entity-list", entityType, params] as const,
  entityListTotal: (entityType: string, organizationId: number | undefined, scope: ListScope | undefined) =>
    ["entity-list", entityType, "unfiltered-total", organizationId, scope?.entityType, scope?.id] as const,
  entityListSchema: (entityType: string, organizationId: number | undefined, scope: ListScope | undefined) =>
    ["entity-list-schema", entityType, organizationId, scope?.entityType, scope?.id] as const,

  entityDetails: () => ["entity-detail"] as const,
  entityDetailsOf: (entityType: string) => ["entity-detail", entityType] as const,
  entityDetail: (entityType: string, id: string | number) => ["entity-detail", entityType, id] as const,
  entitySiblings: (entityType: string, id: string | number, organizationId: number | undefined, context: unknown) =>
    ["entity-siblings", entityType, id, organizationId, context ?? null] as const,

  bookmarks: () => ["bookmark-status"] as const,
  bookmark: (resourceUri: string | undefined, organizationId: number | undefined) =>
    ["bookmark-status", resourceUri, organizationId] as const,

  creatableProjects: (kind: string | undefined, organizationId: number | undefined) =>
    ["creatable-projects", kind, organizationId] as const,
  organizationCounts: (organizationId: number | undefined) => ["organization-counts", organizationId] as const,
  duplicationStructure: (entityType: string, id: string | number) => ["duplication-structure", entityType, id] as const,

  // The raw types catalog at `path` (GET …/<x>-types): fetched once per path, whoever reads it.
  typesCatalog: (path: string) => ["types-catalog", path] as const,
  // What a consumer derives from a catalog — cached apart only so each keeps its own `select`.
  effectiveForm: (segment: string, projectId: string | number | null | undefined, typeId: string | null | undefined) =>
    ["effective-form", segment, projectId, typeId ?? null] as const,
  typeRules: (segment: string | undefined, projectId: string) => ["type-rules", segment, projectId] as const,
  recordingUnitTypes: (projectId: string | number | null | undefined) => ["recording-unit-types", projectId] as const,
  projectTypes: (organizationId: string | number | null | undefined) => ["project-types", organizationId] as const,

  projectHistory: (projectId: string | number | undefined) => ["project-history", projectId] as const,
  projectHomeRecent: (organizationId: number | undefined) => ["project-home-recent", organizationId] as const,
};
