// The places an entity's answers name: pure, read from the answer envelopes the API sends
// ({ answerType, value } around a { resourceType: "places", id } reference, or a list of them).

const SINGLE_PLACE = "SELECT_ONE_SPATIAL_UNIT";
const PLACE_ANSWER_TYPES = new Set([SINGLE_PLACE, "SELECT_MULTIPLE_SPATIAL_UNIT", "SELECT_MULTIPLE_SPATIAL_UNIT_TREE"]);

interface PlaceAnswer {
  fieldId: number;
  answerType: string;
  value: unknown;
}

/**
 * The ids of the places the answers name, each once: the single-place fields first (the entity's own
 * « lieu »), the system fields (negative ids) before the added ones. Not the object's key order, which
 * puts positive integer keys before every other.
 */
export function placeRefsOf(answers: Record<string, unknown> | null | undefined): string[] {
  const ids: string[] = [];
  for (const answer of placeAnswersOf(answers).sort(byPriority)) {
    for (const ref of refsIn(answer.value)) {
      if (!ids.includes(ref)) ids.push(ref);
    }
  }
  return ids;
}

function placeAnswersOf(answers: Record<string, unknown> | null | undefined): PlaceAnswer[] {
  return Object.entries(answers ?? {}).flatMap(([key, answer]) => {
    if (answer == null || typeof answer !== "object") return [];
    const envelope = answer as { answerType?: string; value?: unknown; values?: unknown };
    if (!envelope.answerType || !PLACE_ANSWER_TYPES.has(envelope.answerType)) return [];
    return [{ fieldId: Number(key), answerType: envelope.answerType, value: envelope.value ?? envelope.values }];
  });
}

function byPriority(a: PlaceAnswer, b: PlaceAnswer): number {
  const single = Number(b.answerType === SINGLE_PLACE) - Number(a.answerType === SINGLE_PLACE);
  return single !== 0 ? single : a.fieldId - b.fieldId;
}

function refsIn(value: unknown): string[] {
  // A multiple answer is a list, or { values: [...] } when the server sends it paged (MultiValueAnswer).
  const items = Array.isArray(value)
    ? value
    : value != null && typeof value === "object" && Array.isArray((value as { values?: unknown }).values)
      ? (value as { values: unknown[] }).values
      : [value];
  return items
    .filter((item): item is { id: string | number; resourceType?: string } => item != null && typeof item === "object" && "id" in item)
    .filter((item) => item.resourceType == null || item.resourceType === "places")
    .map((item) => String(item.id));
}
