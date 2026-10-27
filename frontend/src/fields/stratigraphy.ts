import type {StratigraphyZone} from "../components/relationLinks/api";
import type {FieldResource} from "./types";

// The answer type of the three fields that hold a unit's stratigraphic relationships. Their values are
// not answers to patch: they are added, edited and removed by the unit's own endpoints.
export const STRATIGRAPHY_ANSWER_TYPE = "SELECT_MULTIPLE_STRATIGRAPHY";

// Each side of a unit's stratigraphy is a field of its own, bound to where its units stand.
const POSITION_BY_BINDING: Record<string, StratigraphyZone> = {
  anteriorRelationships: "anterior",
  posteriorRelationships: "posterior",
  synchronousRelationships: "synchronous",
};

/** Where the units of a stratigraphic field stand relative to the unit, or undefined for any other field. */
export function stratigraphyPositionOf(field: FieldResource): StratigraphyZone | undefined {
  return field.answerType === STRATIGRAPHY_ANSWER_TYPE ? POSITION_BY_BINDING[field.valueBinding ?? ""] : undefined;
}
