import type { CreatePrefill, DetailTabDef, DetailTabHelpers } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";

// Builds a DetailTabDef whose content is another entity type's own list, scoped to this detail's
// entity — "the recording units of this project", and (per plan) the next relation after it
// (a project's documents, a recording unit's finds/children) with no new component. Lives in
// panels/, not entities/, to avoid a cycle: entities/registry (which entities/*/config.tsx
// imports) is itself imported by panels/EntityListPanel.
//
// Deliberately generic over TDetail only through the `id`/`badge` it's given — it never assumes
// anything about the parent entity's shape beyond having an `id`, so a project's config and (once
// one exists) a recording unit's config can both build tabs with this same helper.
export function relationTab<TDetail extends { id: string | number }>(spec: {
  key: string;
  label: string;
  // The related entity's own registry key (e.g. "recordingUnit") — resolved through the registry
  // like every other cross-entity reference in this codebase (routes, icon, labels), not
  // hardcoded here.
  target: string;
  // The parent's own registry key — used only to build ListScope.entityType, which
  // entities/listApi.ts resolves back to a collectionPath itself.
  scopeEntityType: string;
  // Overrides the child REST segment when it differs from the target entity's own
  // collectionPath (e.g. an action-unit's finds live at "/mobiliers", not "/finds").
  path?: string;
  badge?: (entity: TDetail) => number | string | undefined;
  // The parent's project, when the parent isn't itself the project (see ListScope.projectId).
  projectId?: (entity: TDetail) => string | number | null | undefined;
  // false hides the list's "Créer": a plain create there wouldn't be linked to the parent (a new
  // RU in a "contained RUs" tab would not be a child), so it'd never show up in the tab.
  creatable?: boolean;
  // Links the tab's own "Créer" to the parent (the new UE is its child, the new find is on it…),
  // so what gets created shows up in this tab.
  createPrefill?: (entity: TDetail) => CreatePrefill;
}): DetailTabDef<TDetail> {
  return {
    key: spec.key,
    label: spec.label,
    badge: spec.badge,
    render: (entity: TDetail, helpers: DetailTabHelpers) => (
      <EntityListPanel
        embedded
        entityType={spec.target}
        scope={{ entityType: spec.scopeEntityType, id: entity.id, path: spec.path, projectId: spec.projectId?.(entity) ?? undefined }}
        creatable={spec.creatable}
        createPrefill={spec.createPrefill?.(entity)}
        organizationId={helpers.organizationId}
        onNavigate={helpers.onNavigate}
        onOpenOverview={helpers.onOpenOverview}
        overviewEntityId={helpers.overviewEntityId}
      />
    ),
  };
}
