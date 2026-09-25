import { describe, expect, it } from "vitest";
import { registerDefaultFieldRenderers } from "./registerDefaultRenderers";
import { hasFieldRenderer } from "./registry";

registerDefaultFieldRenderers();

// Every answerType ActionUnit.DETAILS_FORM's 33 fields use, as the org catalog reports them
// (RecordingUnitOpenApiService.answerTypeDiscriminator). This list is the Project fiche's parity
// contract: a field whose answerType is missing here falls through to FallbackRenderer and is
// read-only, which for the fiche means a field the user simply cannot fill in.
const PROJECT_FICHE_ANSWER_TYPES = [
  "TEXT", // name, oaCode, scientificManager, hostStructure, developer, comments, …
  "INTEGER", // volumeCount, pageCount, figureCount, appendixCount
  "DECIMAL", // zmin, zmax, excavatedArea, accessibleArea, openingRate, prescribedArea
  "DATETIME", // beginDate, endDate, prescriptionOrderDate, designationOrderDate
  "SELECT_ONE_FROM_FIELD_CODE", // type, status, fieldStatus, system, developmentNature
  "SELECT_MULTIPLE_FROM_FIELD_CODE", // periods, subjects
  "SELECT_ONE_SPATIAL_UNIT", // mainLocation
  "SELECT_MULTIPLE_SPATIAL_UNIT_TREE", // spatialContext
];

describe("registerDefaultFieldRenderers", () => {
  it.each(PROJECT_FICHE_ANSWER_TYPES)("registers a real renderer for %s", (answerType) => {
    expect(hasFieldRenderer(answerType)).toBe(true);
  });

  it.each([
    "SELECT_ONE_PERSON",
    "SELECT_MULTIPLE_PERSON",
    "SELECT_ONE_ACTION_UNIT",
    "SELECT_ONE_RECORDING_UNIT",
    "SELECT_MULTIPLE_RECORDING_UNIT",
    "SELECT_MULTIPLE_SPECIMEN",
    "SELECT_MULTIPLE_PHASE",
    "SELECT_MULTIPLE_CONTAINER",
    "SELECT_ONE",
    "SELECT_MULTIPLE",
    "MEASUREMENT",
  ])("registers the reference/measurement editor for %s", (answerType) => {
    expect(hasFieldRenderer(answerType)).toBe(true);
  });

  it("leaves action codes and addresses read-only", () => {
    expect(hasFieldRenderer("SELECT_ONE_ACTION_CODE")).toBe(false);
    expect(hasFieldRenderer("SELECT_ADDRESS")).toBe(false);
  });

  it("is idempotent — a second call does not re-register or throw", () => {
    expect(() => registerDefaultFieldRenderers()).not.toThrow();
    expect(hasFieldRenderer("TEXT")).toBe(true);
  });
});
