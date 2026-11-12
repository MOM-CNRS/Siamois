import {messageForError} from "../../../api/errors";

// How many requests a bulk action keeps in flight at once: enough to be quick on a few hundred rows,
// few enough not to flood the server (each request is its own transaction, checked on its own rights).
export const BULK_CONCURRENCY = 4;

/** One entity a bulk action could not change, and why (the server's message when it gave one). */
export interface BulkFailure {
  id: string | number;
  message: string;
}

/** What a bulk action did: the entities it changed, and those it could not. */
export interface BulkOutcome {
  succeeded: (string | number)[];
  failed: BulkFailure[];
}

/**
 * Runs `task` once per id, at most `concurrency` at a time, and waits for every one of them. A
 * failure never stops the others: each entity is changed (or refused) on its own, and the outcome
 * says which.
 */
export async function runBulk(
  ids: readonly (string | number)[],
  task: (id: string | number) => Promise<unknown>,
  fallbackMessage: string,
  concurrency: number = BULK_CONCURRENCY,
): Promise<BulkOutcome> {
  const outcome: BulkOutcome = { succeeded: [], failed: [] };
  let next = 0;

  /**
   * Takes the next id until none is left.
   */
  async function worker(): Promise<void> {
    while (next < ids.length) {
      const id = ids[next];
      next += 1;
      try {
        await task(id);
        outcome.succeeded.push(id);
      } catch (error) {
        outcome.failed.push({ id, message: messageForError(error, fallbackMessage) });
      }
    }
  }

  const workers = Array.from({ length: Math.max(1, Math.min(concurrency, ids.length)) }, () => worker());
  await Promise.all(workers);
  return outcome;
}
