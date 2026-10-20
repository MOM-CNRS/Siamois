import { useCallback, useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getViewState, putViewState, deleteViewState, type ViewStateBody, type ViewStateResponse } from "../api/views";
import { queryKeys } from "../api/queryKeys";

// How long a burst of changes (typing a filter, dragging a column) waits before it is sent.
export const SAVE_DELAY_MS = 600;

export interface UserViewState {
  // False until the server answered (or failed, or there is nothing to ask): the caller holds what
  // depends on the saved state until then, so it doesn't render once without it and again with it.
  settled: boolean;
  // What the user last saved on this view; undefined when nothing yet.
  saved: ViewStateBody | undefined;
  // Remembers `state` now (the cache) and sends it once the changes stop.
  save: (state: ViewStateBody) => void;
  // `save` of what `change` makes of the latest state (the cache, so also what was saved a moment ago
  // and not sent yet): for several parts that each keep their own piece of one state.
  update: (change: (current: ViewStateBody) => ViewStateBody) => void;
  // Forgets the user's changes: the view is back to its own state.
  reset: () => void;
}

interface Pending {
  timer: ReturnType<typeof setTimeout>;
  send: () => void;
}

// Outside the hook so a pending save survives its component unmounting (focus mode moves a fiche
// between two panes) and so a page leaving still sends it.
const pending = new Map<string, Pending>();

function flush(key: string): void {
  const entry = pending.get(key);
  if (!entry) return;
  clearTimeout(entry.timer);
  pending.delete(key);
  entry.send();
}

if (typeof window !== "undefined") {
  window.addEventListener("pagehide", () => {
    for (const key of [...pending.keys()]) flush(key);
  });
}

/**
 * The current user's own state on one view, persisted server-side (GET/PUT /api/v1/view-states).
 * Disabled — settled at once, nothing saved — without a key or an organization (a list rendered on its
 * own). Failures never block the list: an unreadable state is "nothing saved", an unsent one is lost
 * quietly, as the browser-local preferences it replaces would have been.
 */
export function useUserViewState(viewKey: string | undefined, organizationId: number | undefined): UserViewState {
  const queryClient = useQueryClient();
  const enabled = viewKey != null && organizationId != null;
  const queryKey = queryKeys.viewState(organizationId, viewKey);

  const query = useQuery({
    queryKey,
    queryFn: () => getViewState(organizationId!, viewKey!),
    enabled,
    // Written through on every save: what is cached is what the server will hold.
    staleTime: Infinity,
  });

  const pendingKey = `${organizationId}|${viewKey}`;

  const save = useCallback(
    (state: ViewStateBody) => {
      if (!enabled) return;
      queryClient.setQueryData(queryKey, { viewKey: viewKey!, state });
      const previous = pending.get(pendingKey);
      if (previous) clearTimeout(previous.timer);
      pending.set(pendingKey, {
        timer: setTimeout(() => flush(pendingKey), SAVE_DELAY_MS),
        send: () => {
          putViewState(organizationId!, viewKey!, state).catch(() => {
            // Not retried: the next change sends the whole state again.
          });
        },
      });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [enabled, pendingKey, queryClient],
  );

  const update = useCallback(
    (change: (current: ViewStateBody) => ViewStateBody) => {
      if (!enabled) return;
      const current = queryClient.getQueryData<ViewStateResponse>(queryKey)?.state ?? {};
      save(change(current));
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [enabled, pendingKey, queryClient, save],
  );

  const reset = useCallback(() => {
    if (!enabled) return;
    const previous = pending.get(pendingKey);
    if (previous) {
      clearTimeout(previous.timer);
      pending.delete(pendingKey);
    }
    queryClient.setQueryData(queryKey, { viewKey: viewKey!, state: null });
    deleteViewState(organizationId!, viewKey!).catch(() => {
      // The state comes back on the next load; nothing to undo here.
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, pendingKey, queryClient]);

  // Leaving the list sends what is waiting: the user's last change must not die with the component.
  const keyRef = useRef(pendingKey);
  keyRef.current = pendingKey;
  useEffect(() => () => flush(keyRef.current), []);

  return {
    settled: !enabled || query.isSuccess || query.isError,
    saved: query.data?.state ?? undefined,
    save,
    update,
    reset,
  };
}
