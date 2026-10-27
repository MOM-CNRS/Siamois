import {
    createContext,
    type Dispatch,
    type ReactNode,
    type SetStateAction,
    useCallback,
    useContext,
    useEffect,
    useState,
} from "react";

// A panel's UI state (active tab, filters, sort, page, selection, which form sections are folded…)
// lives in React state, so it dies with the component. But entering or leaving focus mode moves a
// fiche between the overview pane and the main pane — two different places in the tree, so React
// unmounts one and mounts the other. The store below outlives those mounts: the state is written
// to it as it changes and read back when a panel of the same identity mounts again.
//
// Identity is the path of scopes above the hook: the pane (kind, entity type, entity id), then
// whatever a panel adds for its parts (a fiche's tab…). One store per App instance, kept to the
// panes that are open or stacked in focus mode (see `retain`), so it stays small.

export class PaneStateStore {
  private readonly entries = new Map<string, unknown>();

  has(key: string): boolean {
    return this.entries.has(key);
  }

  get(key: string): unknown {
    return this.entries.get(key);
  }

  set(key: string, value: unknown): void {
    this.entries.set(key, value);
  }

  /**
   * Drops the state of every pane not named in `paneScopes`: the store only holds what the panes
   * still on screen, or waiting in the focus stack, can come back to.
   */
  retain(paneScopes: readonly string[]): void {
    for (const key of [...this.entries.keys()]) {
      if (!paneScopes.some((scope) => key.startsWith(`/${scope}#`) || key.startsWith(`/${scope}/`))) {
        this.entries.delete(key);
      }
    }
  }
}

// No store (a panel rendered on its own, in a test): the hook is plain local state.
const StoreContext = createContext<PaneStateStore | null>(null);
const ScopeContext = createContext("");

export function PaneStateProvider({ store, children }: Readonly<{ store: PaneStateStore; children: ReactNode }>) {
  return <StoreContext.Provider value={store}>{children}</StoreContext.Provider>;
}

/** Narrows the identity of every `usePaneState` below: `name` is appended to the enclosing scope. */
export function PaneStateScope({ name, children }: Readonly<{ name: string; children: ReactNode }>) {
  const parent = useContext(ScopeContext);
  const scope = `${parent}/${name}`;
  return <ScopeContext.Provider value={scope}>{children}</ScopeContext.Provider>;
}

function resolveInitial<T>(initial: T | (() => T)): T {
  return typeof initial === "function" ? (initial as () => T)() : initial;
}

interface Slot<T> {
  key: string;
  value: T;
}

/**
 * `useState` whose value is remembered in the pane-state store under (enclosing scope, `name`), and
 * restored when a panel of the same identity mounts again.
 *
 * When the enclosing scope changes while mounted (a sibling jump retargets the fiche to another
 * entity), the value of the new identity is used if it has one, otherwise the current value is
 * carried over: the fiche stays on the tab you were looking at.
 */
export function usePaneState<T>(name: string, initial: T | (() => T)): [T, Dispatch<SetStateAction<T>>] {
  const store = useContext(StoreContext);
  const key = `${useContext(ScopeContext)}#${name}`;

  const [slot, setSlot] = useState<Slot<T>>(() => ({
    key,
    value: store?.has(key) ? (store.get(key) as T) : resolveInitial(initial),
  }));

  let current = slot;
  if (slot.key !== key) {
    // Adjusted during render, not in an effect: an effect would commit the old identity's value
    // for one paint.
    current = { key, value: store?.has(key) ? (store.get(key) as T) : slot.value };
    setSlot(current);
  }

  // After commit, so a panel mounting in the same commit as this one unmounts reads what this one
  // last showed.
  useEffect(() => {
    store?.set(current.key, current.value);
  }, [store, current.key, current.value]);

  const setValue = useCallback<Dispatch<SetStateAction<T>>>((next) => {
    setSlot((prev) => ({
      key: prev.key,
      value: typeof next === "function" ? (next as (v: T) => T)(prev.value) : next,
    }));
  }, []);

  return [current.value, setValue];
}

/** A store for the lifetime of the calling component (App). */
export function usePaneStateStore(): PaneStateStore {
  const [store] = useState(() => new PaneStateStore());
  return store;
}
