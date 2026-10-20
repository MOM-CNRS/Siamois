// A module-level channel between the main panel's React root (App) and the other React roots of the
// page that live outside it (the top search bar). They are separate roots mounted by separate JSF
// fragments, so they cannot share a context: App registers what it can do and publishes where the
// user is, the search bar reads both.

/** What the list opened in the overview starts with. `nonce` retargets a list that is already open. */
export interface ListSeed {
  search?: string;
  // The project the list is restricted to (its project-scoped sub-collection), when it has one.
  projectId?: string | number;
  nonce: number;
}

export interface ExternalNavigation {
  navigate: (entityType: string, id?: string | number) => void;
  openOverview: (entityType: string, id: string | number) => void;
  openList: (entityType: string, seed: Omit<ListSeed, "nonce">) => void;
}

/** Where the user is, as far as a search needs to know. */
export interface NavigationContext {
  organizationId?: number;
  // The project the main pane shows (a project's fiche) or belongs to (one of its entities' fiches).
  projectId?: string;
  projectLabel?: string;
}

// Kept on `globalThis`, not in module variables: JSF may run the bundle again when it re-renders the
// flow, and the search bar mounted by the first run must still reach the App mounted by the last.
interface BusState {
  navigation: ExternalNavigation | null;
  context: NavigationContext;
  listeners: Set<() => void>;
}
const holder = globalThis as { __siamoisSearchBus?: BusState };
const bus: BusState = (holder.__siamoisSearchBus ??= { navigation: null, context: {}, listeners: new Set() });

/** Called by App for as long as it is mounted; the returned function unregisters it. */
export function registerNavigation(next: ExternalNavigation): () => void {
  bus.navigation = next;
  return () => {
    if (bus.navigation === next) bus.navigation = null;
  };
}

export function getNavigation(): ExternalNavigation | null {
  return bus.navigation;
}

export function publishContext(next: NavigationContext): void {
  const { context } = bus;
  if (next.organizationId === context.organizationId && next.projectId === context.projectId && next.projectLabel === context.projectLabel) {
    return;
  }
  bus.context = next;
  bus.listeners.forEach((listener) => listener());
}

export function getContext(): NavigationContext {
  return bus.context;
}

export function subscribeContext(listener: () => void): () => void {
  bus.listeners.add(listener);
  return () => bus.listeners.delete(listener);
}
