import {createContext, type Dispatch, type ReactNode, type SetStateAction, useContext, useEffect, useRef} from "react";
import {usePaneState} from "./paneState";

// The state of a fiche is more than its active tab: the folded sections of a tab, the graph/list mode of
// the relations, the filters of the relation list… all are kept per user, per type of fiche (never per
// record: what a user arranged on a unit applies to every unit), server-side (useUserViewState).
//
// EntityDetailPanel owns the saved state and offers it to the tab being rendered through
// PersistedTabState. A component inside a tab keeps a piece of its state with usePersistedPaneState: the
// same as usePaneState, plus that piece is read from and written to the fiche's state under the tab's own
// key — so changing tab never loses the state of the others, as each tab only writes its own pieces.

export interface PersistedTabStateValue {
  // What was saved under (this tab, name); undefined when nothing yet.
  read: (name: string) => unknown;
  // Merges `value` into the saved state under (this tab, name).
  write: (name: string, value: unknown) => void;
}

const PersistedTabContext = createContext<PersistedTabStateValue | null>(null);

export function PersistedTabState({ value, children }: Readonly<{ value: PersistedTabStateValue; children: ReactNode }>) {
  return <PersistedTabContext.Provider value={value}>{children}</PersistedTabContext.Provider>;
}

export interface PersistOptions<T, S> {
  // What of the state is kept (default: all of it) …
  toSaved?: (value: T) => S;
  // … and how it reads back over the initial value (default: the saved value as is).
  fromSaved?: (saved: S, initial: T) => T;
  // Whether what was saved still makes sense to this component (default: yes). The definition of a form,
  // or the set of modes, may have changed since: a saved value that fails is dropped for the initial one.
  isValid?: (saved: unknown) => boolean;
}

/**
 * `usePaneState` whose value is also kept for the user when it lives inside a fiche's tab
 * (PersistedTabState). Outside one it is plain `usePaneState`. Only the user's changes are written, not
 * the value the component opened with.
 */
export function usePersistedPaneState<T, S = T>(
  name: string,
  initial: T,
  options: PersistOptions<T, S> = {},
): [T, Dispatch<SetStateAction<T>>] {
  const persisted = useContext(PersistedTabContext);
  const { toSaved, fromSaved, isValid } = options;

  const [value, setValue] = usePaneState<T>(name, () => {
    const saved = persisted?.read(name);
    if (saved === undefined || (isValid && !isValid(saved))) return initial;
    return fromSaved ? fromSaved(saved as S, initial) : (saved as T);
  });

  const encode = (v: T) => (toSaved ? toSaved(v) : v);
  const lastKept = useRef<string | undefined>(undefined);
  const write = persisted?.write;
  useEffect(() => {
    if (!write) return;
    const snapshot = JSON.stringify(encode(value));
    if (lastKept.current === undefined) {
      lastKept.current = snapshot;
      return;
    }
    if (snapshot !== lastKept.current) {
      lastKept.current = snapshot;
      write(name, encode(value));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, write, name]);

  return [value, setValue];
}
