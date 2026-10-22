// A deletion is announced here so that whatever is showing the deleted entity (the main pane's
// fiche, the overview) can let go of it — wherever the delete was started from (a list row in
// either pane, a fiche's titlebar, a relation tab embedded in a fiche).
type Listener = (entityType: string, id: string | number) => void;

const listeners = new Set<Listener>();

export function onEntityDeleted(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function announceEntityDeleted(entityType: string, id: string | number): void {
  listeners.forEach((listener) => listener(entityType, id));
}
