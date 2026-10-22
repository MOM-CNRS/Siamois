// Compile-time check that the hand-written DTOs still describe the API: every field a DTO declares must exist
// in the matching OpenAPI schema, so a field renamed or dropped on the Java side fails `tsc` here instead of
// showing up as a blank cell at runtime. Nothing is emitted: this file only holds types. It lives in entities/
// (not api/) because it has to see every entity folder, which the generic layers may not.
//
// Fields the spec has and the DTO leaves out are fine (the screens simply don't use them). When this file fails
// after `npm run api:pull`, update the hand-written type named in the error.
import type { ContainerResource } from "./container/types";
import type { DocumentResource } from "./document/types";
import type { FindResource } from "./find/types";
import type { PhaseResource } from "./phase/types";
import type { PlaceResource } from "./place/types";
import type { ProjectCounts, ProjectPermissions, ProjectResource, PlaceLight, ResourceRef } from "./project/types";
import type { RecordingUnitCounts, RecordingUnitResource } from "./recordingUnit/types";
import type { StudyResource } from "./study/types";
import type { FieldResource } from "../fields/types";
import type { Schemas } from "../api/generated";

type UnknownFields<Hand, Spec> = Exclude<keyof Hand, keyof Spec>;

// Resolves to `true` when the DTO only declares fields of the schema, otherwise to an object naming the strays.
type Conforms<Hand, Spec> = [UnknownFields<Hand, Spec>] extends [never]
  ? true
  : { fieldsMissingFromTheApi: UnknownFields<Hand, Spec> };

// `T extends true` is what makes a stray field a compile error rather than a silently wrong type.
type Assert<T extends true> = T;

export interface DtoConformance {
  project: Assert<Conforms<ProjectResource, Schemas["ProjectResource"]>>;
  projectCounts: Assert<Conforms<ProjectCounts, Schemas["ProjectResourceCounts"]>>;
  projectPermissions: Assert<Conforms<ProjectPermissions, Schemas["ProjectResourcePermissions"]>>;
  placeLight: Assert<Conforms<PlaceLight, Schemas["PlaceLightResource"]>>;
  resourceRef: Assert<Conforms<ResourceRef, Schemas["ResourceRef"]>>;
  recordingUnit: Assert<Conforms<RecordingUnitResource, Schemas["RecordingUnitResource"]>>;
  recordingUnitCounts: Assert<Conforms<RecordingUnitCounts, Schemas["RecordingUnitResourceCounts"]>>;
  find: Assert<Conforms<FindResource, Schemas["FindResource"]>>;
  phase: Assert<Conforms<PhaseResource, Schemas["PhaseResource"]>>;
  container: Assert<Conforms<ContainerResource, Schemas["ContainerResource"]>>;
  place: Assert<Conforms<PlaceResource, Schemas["PlaceResource"]>>;
  document: Assert<Conforms<DocumentResource, Schemas["DocumentResource"]>>;
  study: Assert<Conforms<StudyResource, Schemas["StudyResource"]>>;
  field: Assert<Conforms<FieldResource, Schemas["FieldResource"]>>;
}
