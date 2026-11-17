/**
 * Whether an element is the one the overview pane shows — the table's "overview-open" row, the
 * selected card, the highlighted feature on the map. Ids are compared as strings: the API sends
 * them as strings, the overview may hold a number.
 */
export function isOpenInOverview(overviewEntityId: string | number | null | undefined, id: string | number | null | undefined): boolean {
  return overviewEntityId != null && id != null && String(id) === String(overviewEntityId);
}
