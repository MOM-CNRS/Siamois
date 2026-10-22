import { t } from "../../../i18n";
import type { RelationKind, ZoneKey } from "./types";

// The title of each graph zone.
export function zoneTitle(zone: ZoneKey): string {
  switch (zone) {
    case "parents": return t("recordingUnit.graph.parents");
    case "children": return t("recordingUnit.graph.children");
    case "anterior": return t("recordingUnit.graph.anterior");
    case "synchronous": return t("recordingUnit.graph.synchronous");
    case "posterior": return t("recordingUnit.graph.posterior");
    default: return t("recordingUnit.graph.others");
  }
}

// What a relation of that kind is called in the list: the other unit is the unit's parent, child…
export function kindLabel(kind: RelationKind): string {
  return t(`recordingUnit.relations.kind.${kind}`);
}

export const RELATION_KINDS: RelationKind[] = ["parents", "children", "anterior", "posterior", "synchronous"];
