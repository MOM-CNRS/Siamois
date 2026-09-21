import { registerFallbackFieldRenderer, registerFieldRenderer } from "./registry";
import {
  DateRenderer,
  DecimalRenderer,
  FallbackRenderer,
  IntegerRenderer,
  SelectManyConceptRenderer,
  SelectOneConceptRenderer,
  SelectOneSpatialUnitRenderer,
  TextRenderer,
} from "./renderers";

// Called once at app startup (from mount.ts) to populate the base renderer set — the scalar
// answerTypes that need no backend lookup, plus the SELECT_* answerTypes ActionUnitForm actually
// uses that DO need one (org-scoped concepts / places autocomplete, fields/optionSources.ts).
// Still unregistered: SELECT_MULTIPLE_SPATIAL_UNIT_TREE (a tree picker) and the rarer SELECT_*
// variants (person/action-unit/recording-unit/...) — no option source client for those yet, so
// they fall through to FallbackRenderer (read-only) rather than crashing.
let registered = false;

export function registerDefaultFieldRenderers(): void {
  if (registered) return;
  registered = true;

  registerFieldRenderer("TEXT", TextRenderer);
  registerFieldRenderer("INTEGER", IntegerRenderer);
  registerFieldRenderer("DECIMAL", DecimalRenderer);
  registerFieldRenderer("DATETIME", DateRenderer);
  registerFieldRenderer("SELECT_ONE_FROM_FIELD_CODE", SelectOneConceptRenderer);
  registerFieldRenderer("SELECT_MULTIPLE_FROM_FIELD_CODE", SelectManyConceptRenderer);
  registerFieldRenderer("SELECT_ONE_SPATIAL_UNIT", SelectOneSpatialUnitRenderer);
  registerFallbackFieldRenderer(FallbackRenderer);
}
