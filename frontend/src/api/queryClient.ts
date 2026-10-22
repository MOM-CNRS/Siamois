import { QueryClient } from "@tanstack/react-query";

/**
 * Retries a failed request at most twice, and only when retrying can help: a server error or a
 * network failure. A 4xx answer (an ApiError status) (bad request, forbidden, not found, conflict) won't change.
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  const status = (error as { status?: unknown } | null)?.status;
  if (typeof status === "number" && status < 500) return false;
  return failureCount < 2;
}

// The one cache of the app: every mount (main pane, overview) and the catalog loaders share it, so
// a catalog fetched for a list is the one its fiches and create forms read.
export const queryClient = new QueryClient({
  defaultOptions: {
    // No refetch when the tab regains focus: the data that changes under the user's feet is rare here, and
    // every refetch re-renders big tables. A query that wants fresh data on mount says so with its own staleTime.
    queries: { retry: shouldRetry, refetchOnWindowFocus: false },
  },
});
