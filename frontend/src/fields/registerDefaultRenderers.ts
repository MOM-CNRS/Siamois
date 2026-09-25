import { registerFallbackFieldRenderer, registerFieldRenderer } from "./registry";
import {
  DateRenderer,
  DecimalRenderer,
  FallbackRenderer,
  IntegerRenderer,
  MeasurementRenderer,
  SelectManyRefRenderer,
  SelectOneRefRenderer,
  TextRenderer,
} from "./renderers";

// Called once at app startup (from mount.ts) to populate the base renderer set: every answerType a
// form can hold, except the two that stay read-only (FallbackRenderer) — SELECT_ONE_ACTION_CODE
// (read-only in JSF too) and SELECT_ADDRESS (waits for the GéoPlateforme lookup). Every reference
// answerType shares one picker; fields/optionSources.ts is where each one's option source lives.
let registered = false;

const SINGLE_REFERENCES = [
  "SELECT_ONE_FROM_FIELD_CODE",
  "SELECT_ONE",
  "SELECT_ONE_SPATIAL_UNIT",
  "SELECT_ONE_PERSON",
  "SELECT_ONE_ACTION_UNIT",
  "SELECT_ONE_RECORDING_UNIT",
];

const MULTIPLE_REFERENCES = [
  "SELECT_MULTIPLE_FROM_FIELD_CODE",
  "SELECT_MULTIPLE",
  "SELECT_MULTIPLE_SPATIAL_UNIT_TREE",
  "SELECT_MULTIPLE_PERSON",
  "SELECT_MULTIPLE_RECORDING_UNIT",
  "SELECT_MULTIPLE_SPECIMEN",
  "SELECT_MULTIPLE_PHASE",
  "SELECT_MULTIPLE_CONTAINER",
];

export function registerDefaultFieldRenderers(): void {
  if (registered) return;
  registered = true;

  registerFieldRenderer("TEXT", TextRenderer);
  registerFieldRenderer("INTEGER", IntegerRenderer);
  registerFieldRenderer("DECIMAL", DecimalRenderer);
  registerFieldRenderer("DATETIME", DateRenderer);
  registerFieldRenderer("MEASUREMENT", MeasurementRenderer);
  for (const answerType of SINGLE_REFERENCES) registerFieldRenderer(answerType, SelectOneRefRenderer);
  for (const answerType of MULTIPLE_REFERENCES) registerFieldRenderer(answerType, SelectManyRefRenderer);
  registerFallbackFieldRenderer(FallbackRenderer);
}
