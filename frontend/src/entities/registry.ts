import type { ContainerDetail, ContainerSummary } from "./container/types";
import type { DocumentDetail, DocumentSummary } from "./document/types";
import type { FindDetail, FindSummary } from "./find/types";
import type { EntityKey } from "./keys";
import type { PhaseDetail, PhaseSummary } from "./phase/types";
import type { PlaceDetail, PlaceSummary } from "./place/types";
import type { ProjectDetail, ProjectSummary } from "./project/types";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./recordingUnit/types";
import type { EntityTypeConfig } from "./shared/types";
import type { StudyDetail, StudySummary } from "./study/types";

// One module per entity registers itself here (entities/project/config.tsx is the first, phase
// 4). EntityTypeConfig<TSummary, TDetail> is concretely typed within each entity's own config.tsx;
// the map holding all of them can't stay generic over every pair at once, so it erases the generics
// at ONE place: AnyEntityTypeConfig. The row and detail types are `any` only there.
//
// Callers that know the entity at compile time (`getEntityType("recordingUnit")`) get the precise
// config back through EntityTypeMap; callers that only have a string (a URL segment, the JSF bridge,
// a tab's relation target) get AnyEntityTypeConfig. The panels take EntityTypeConfig<unknown, unknown>
// and never assume a concrete row shape themselves.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type AnyEntityTypeConfig = EntityTypeConfig<any, any>;

interface DeclaredEntityTypes {
  project: EntityTypeConfig<ProjectSummary, ProjectDetail>;
  recordingUnit: EntityTypeConfig<RecordingUnitSummary, RecordingUnitDetail>;
  find: EntityTypeConfig<FindSummary, FindDetail>;
  phase: EntityTypeConfig<PhaseSummary, PhaseDetail>;
  container: EntityTypeConfig<ContainerSummary, ContainerDetail>;
  place: EntityTypeConfig<PlaceSummary, PlaceDetail>;
  document: EntityTypeConfig<DocumentSummary, DocumentDetail>;
  study: EntityTypeConfig<StudySummary, StudyDetail>;
}

// Mapped over EntityKey, so adding a key to ENTITY_KEYS without declaring its types above fails to compile.
export type EntityTypeMap = { [K in EntityKey]: DeclaredEntityTypes[K] };

const registry = new Map<string, AnyEntityTypeConfig>();

export function registerEntityType(config: AnyEntityTypeConfig): void {
  registry.set(config.key, config);
}

export function getEntityType<K extends EntityKey>(key: K): EntityTypeMap[K] | undefined;
export function getEntityType(key: string): AnyEntityTypeConfig | undefined;
export function getEntityType(key: string): AnyEntityTypeConfig | undefined {
  return registry.get(key);
}

// Lets Home assemble its widget list by asking every registered entity for its own (plan §8
// phase 7) instead of App.tsx importing entities/project/homeWidgets.tsx directly — the same
// "registry, not a switch" principle List/Detail already follow. A future entity that also
// registers a `home.widgets` factory needs no change here or in App.tsx.
export function getAllEntityTypes(): AnyEntityTypeConfig[] {
  return Array.from(registry.values());
}
