import type { EntityTypeConfig } from "./types";

// One module per entity registers itself here (entities/project/config.tsx is the first, phase
// 4). This registry is a deliberate generics-erasure boundary: EntityTypeConfig<TSummary,
// TDetail> is concretely typed within each entity's own config.tsx, but the map holding several
// different entities' configs together can't stay generic over all of them at once — `any` here
// is the erasure point, not a shortcut around type-checking. EntityListPanel/EntityDetailPanel
// already treat rows/entities as unknown beyond this boundary (they only ever call back into
// the config's own render functions, never assume a concrete shape themselves).
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const registry = new Map<string, EntityTypeConfig<any, any>>();

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function registerEntityType(config: EntityTypeConfig<any, any>): void {
  registry.set(config.key, config);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function getEntityType(key: string): EntityTypeConfig<any, any> | undefined {
  return registry.get(key);
}

// Lets Home assemble its widget list by asking every registered entity for its own (plan §8
// phase 7) instead of App.tsx importing entities/project/homeWidgets.tsx directly — the same
// "registry, not a switch" principle List/Detail already follow. A future entity that also
// registers a `home.widgets` factory needs no change here or in App.tsx.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function getAllEntityTypes(): EntityTypeConfig<any, any>[] {
  return Array.from(registry.values());
}
