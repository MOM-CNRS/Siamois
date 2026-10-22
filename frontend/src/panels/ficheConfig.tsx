import { useQuery } from "@tanstack/react-query";
import { getFicheConfig } from "../api/ficheConfigs";
import { queryKeys } from "../api/queryKeys";
import { ListTabCount } from "./TabCount";
import { getEntityType } from "../entities/registry";
import { PROJECT_KEY } from "../entities/keys";
import type { DetailTabDef, FilterValue } from "../entities/types";
import { EntityListPanel } from "./EntityListPanel";
import { isFilterValue } from "./listState";

/**
 * The tabs a project configured for a fiche (GET /api/v1/fiche-configs), on top of those the code declares:
 *
 *   { v: 1, tabs: [ { key: "fiche" }, { key: "recording-units", label: "UE" }, { key: "documents", hidden: true },
 *                   { key: "custom:3f2a", kind: "list", target: "recordingUnit", label: "US",
 *                     baseFilters: { type: { op: "in", v: ["412"] } } } ] }
 *
 * The order is the array's. A built-in tab the configuration does not name is shown after the others (a tab
 * added in a later version appears for everybody); a name the code no longer knows, a list tab whose target
 * is gone and a malformed entry are skipped, never an error.
 */
export interface ConfiguredTab {
  key: string;
  label?: string;
  hidden?: boolean;
  // A list of `target`s related to the fiche, defined by its base filters; absent for a built-in tab.
  kind?: "list";
  target?: string;
  baseFilters?: Record<string, FilterValue>;
}

/** Tolerant read of the saved shape: whatever is valid, in order, each key once. */
export function parseFicheTabs(raw: unknown): ConfiguredTab[] {
  const list = raw != null && typeof raw === "object" ? (raw as { tabs?: unknown }).tabs : undefined;
  if (!Array.isArray(list)) return [];
  const seen = new Set<string>();
  const tabs: ConfiguredTab[] = [];
  for (const item of list) {
    if (item == null || typeof item !== "object") continue;
    const entry = item as Record<string, unknown>;
    if (typeof entry.key !== "string" || !entry.key || seen.has(entry.key)) continue;
    seen.add(entry.key);
    const tab: ConfiguredTab = { key: entry.key };
    if (typeof entry.label === "string" && entry.label.trim()) tab.label = entry.label.trim();
    if (entry.hidden === true) tab.hidden = true;
    if (entry.kind === "list" && typeof entry.target === "string") {
      tab.kind = "list";
      tab.target = entry.target;
      const baseFilters: Record<string, FilterValue> = {};
      if (entry.baseFilters != null && typeof entry.baseFilters === "object" && !Array.isArray(entry.baseFilters)) {
        for (const [key, value] of Object.entries(entry.baseFilters)) {
          if (isFilterValue(value)) baseFilters[key] = value;
        }
      }
      tab.baseFilters = baseFilters;
    }
    tabs.push(tab);
  }
  return tabs;
}

/** What identifies a fiche to its project's configuration: the project it belongs to and its sub-type (type of unit…). */
export interface FicheScope {
  projectId?: number;
  typeId?: number;
}

function toNumber(value: unknown): number | undefined {
  const n = typeof value === "string" || typeof value === "number" ? Number(value) : NaN;
  return Number.isInteger(n) && n > 0 ? n : undefined;
}

/** A project is its own project; any other fiche carries `projectId`; a fiche's `type` is its sub-type. */
export function ficheScopeOf(entityType: string, entity: unknown): FicheScope {
  const e = (entity ?? {}) as { id?: unknown; projectId?: unknown; type?: { id?: unknown } | null };
  return {
    projectId: toNumber(entityType === PROJECT_KEY ? e.id : e.projectId),
    typeId: toNumber(e.type?.id),
  };
}

/**
 * The fiche's tabs once configured. Never empty: a configuration that hides everything leaves the code's tabs.
 * `saved` is the `tabs` of the configuration (absent when nobody configured the fiche).
 */
export function resolveTabs<TDetail extends { id: string | number }>(
  code: DetailTabDef<TDetail>[],
  saved: unknown,
  entityType: string,
): DetailTabDef<TDetail>[] {
  const configured = parseFicheTabs(saved);
  if (configured.length === 0) return code;
  const byKey = new Map(code.map((tab) => [tab.key, tab]));
  const named = new Set<string>();
  const result: DetailTabDef<TDetail>[] = [];
  for (const tab of configured) {
    named.add(tab.key);
    if (tab.hidden) continue;
    if (tab.kind === "list") {
      const listed = listTab<TDetail>(tab, entityType, byKey);
      if (listed) result.push(listed);
      continue;
    }
    const builtin = byKey.get(tab.key);
    if (builtin) result.push(tab.label ? { ...builtin, label: tab.label } : builtin);
  }
  for (const tab of code) {
    if (!named.has(tab.key)) result.push(tab);
  }
  return result.length > 0 ? result : code;
}

function listTab<TDetail extends { id: string | number }>(
  tab: ConfiguredTab,
  entityType: string,
  code: Map<string, DetailTabDef<TDetail>>,
): DetailTabDef<TDetail> | undefined {
  const target = tab.target!;
  if (!getEntityType(target)) return undefined;
  // The fiche's own tab on that target says how to create there (what the new entity is linked to).
  const relation = [...code.values()].find((c) => c.relation?.target === target)?.relation;
  return {
    key: tab.key,
    label: tab.label ?? target,
    icon: getEntityType(target)?.icon,
    badge: (entity, helpers) => (
      <ListTabCount
        target={target}
        scope={{ entityType, id: entity.id, projectId: ficheScopeOf(entityType, entity).projectId }}
        filters={tab.baseFilters}
        organizationId={helpers?.organizationId}
      />
    ),
    render: (entity, helpers) => (
      <EntityListPanel
        embedded
        entityType={target}
        scope={{ entityType, id: entity.id, projectId: ficheScopeOf(entityType, entity).projectId }}
        fixedFilters={tab.baseFilters}
        stateKey={tab.key}
        // Created from here, an entity is linked to the fiche as in the fiche's own tab on that target — and
        // typed as the tab's filter says, when it fixes one. Without such a tab a create would not be linked.
        creatable={relation != null && relation.creatable !== false}
        createPrefill={relation?.createPrefill?.(entity)}
        organizationId={helpers.organizationId}
        onNavigate={helpers.onNavigate}
        onOpenOverview={helpers.onOpenOverview}
        overviewEntityId={helpers.overviewEntityId}
      />
    ),
  };
}

export interface FicheConfigState {
  // False until the answer is in (or it failed, or there is nothing to ask): the fiche holds its tabs back until then.
  settled: boolean;
  // The configured `tabs`, absent when nobody configured this fiche.
  tabs: Record<string, unknown> | undefined;
  // The sub-type the configuration was saved for; absent when it is for every sub-type (or there is none).
  typeId: number | undefined;
  // The current user may configure the tabs of this fiche.
  canEdit: boolean;
}

/**
 * The tabs configured for the fiche of `entity`, by its project and sub-type. Asked once the entity is known
 * (they come from it); a failure reads as « not configured » so the fiche keeps its own tabs.
 */
export function useFicheConfig(entityType: string, entity: unknown, organizationId: number | undefined): FicheConfigState {
  const scope = entity != null ? ficheScopeOf(entityType, entity) : undefined;
  const enabled = scope != null && organizationId != null;
  const query = useQuery({
    queryKey: queryKeys.ficheConfig(organizationId, entityType, scope?.projectId, scope?.typeId),
    queryFn: () => getFicheConfig({ organizationId: organizationId!, resourceType: entityType, ...scope }),
    enabled,
    staleTime: Infinity,
  });
  return {
    settled: organizationId == null || (entity != null && (query.isSuccess || query.isError)),
    tabs: query.data?.tabs ?? undefined,
    typeId: query.data?.typeId ?? undefined,
    canEdit: query.data?.canEdit ?? false,
  };
}
