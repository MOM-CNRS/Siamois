import { apiFetch } from "../api/client";
import type { MultiValueAnswer } from "./types";

// The API's page size ceiling (ProjectApiService.MAX_PAGE_SIZE): the fewest round trips.
const PAGE_SIZE = 200;

interface FieldValuesPage {
  data: unknown[];
  meta: { total: number };
}

/**
 * Every value of a multi-valued answer: the answer itself when it is complete, otherwise every page
 * of GET /api/v1/{collection}/{id}/fields/{fieldId}/values — its `_links.values` — in order.
 */
export async function fetchAllValues(answer: MultiValueAnswer): Promise<unknown[]> {
  const href = answer._links?.values;
  if (answer.complete || !href) return answer.values;
  const all: unknown[] = [];
  for (let offset = 0; ; offset += PAGE_SIZE) {
    const separator = href.includes("?") ? "&" : "?";
    const page = await apiFetch<FieldValuesPage>(`${href}${separator}offset=${offset}&limit=${PAGE_SIZE}`);
    all.push(...page.data);
    if (page.data.length < PAGE_SIZE || all.length >= page.meta.total) return all;
  }
}
