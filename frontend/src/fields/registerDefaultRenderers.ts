import { registerFallbackFieldRenderer, registerFieldRenderer } from "./registry";
import {
  DateRenderer,
  DecimalRenderer,
  FallbackRenderer,
  IntegerRenderer,
  SelectManyConceptRenderer,
  SelectManySpatialUnitRenderer,
  SelectOneConceptRenderer,
  SelectOneSpatialUnitRenderer,
  TextRenderer,
} from "./renderers";

// Called once at app startup (from mount.ts) to populate the base renderer set. Between them these
// seven cover every answerType ActionUnit.DETAILS_FORM's 33 fields use, which is what lets the
// Project fiche render its whole layout with real widgets instead of placeholders.
// Still unregistered: the rarer SELECT_* variants (person/action-unit/recording-unit/container/
// phase/specimen/address/measurement/action-code) that only RecordingUnit's form reaches for — no
// option source client for those yet, so they fall through to FallbackRenderer (read-only) rather
// than crashing.
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
  registerFieldRenderer("SELECT_MULTIPLE_SPATIAL_UNIT_TREE", SelectManySpatialUnitRenderer);
  registerFallbackFieldRenderer(FallbackRenderer);
}
