import {CreateLinkField} from "../../components/create/CreateLinkField";
import {TypeOnlyCreateForm} from "../../components/create/TypeOnlyCreateForm";
import type {CreateFormContext} from "../shared/types";
import {createRecordingUnit} from "./api";
import {t} from "../../i18n";

// A recording unit needs only its type. Its identifier is suggested by the server (a peek at the
// counter, not a consumption), shown pre-filled and modifiable — an edited one is sent, an
// untouched suggestion lets the server generate it, as in JSF. Created from another UE's row
// action, it is linked to it in the same transaction: as its child (`parent`) or its parent (`child`).
// Its parent is part of its identifier (NUM_PARENT…), so the suggestion is asked for under it.
export function RecordingUnitCreateForm(ctx: CreateFormContext) {
  const { prefill } = ctx;
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="recordingUnit"
      title={t("create.newRecordingUnit")}
      typesSegment="recording-unit-types"
      parentRecordingUnitId={prefill?.parent?.id}
      create={(body) =>
        createRecordingUnit({
          ...body,
          parentRecordingUnitId: prefill?.parent?.id,
          childRecordingUnitId: prefill?.child?.id,
          phaseId: prefill?.phase?.id,
        })
      }
    >
      {prefill?.parent && <CreateLinkField label={t("create.containedInF")} entityType="recordingUnit" value={prefill.parent} />}
      {prefill?.phase && <CreateLinkField label={t("create.inPhase")} entityType="phase" value={prefill.phase} />}
      {prefill?.child && <CreateLinkField label={t("create.contains")} entityType="recordingUnit" value={prefill.child} />}
    </TypeOnlyCreateForm>
  );
}
