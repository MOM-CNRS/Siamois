import { CreateLinkField } from "../../components/create/CreateLinkField";
import { TypeOnlyCreateForm } from "../../components/create/TypeOnlyCreateForm";
import type { CreateFormContext } from "../shared/types";
import { createRecordingUnit } from "./api";
import { t } from "../../i18n";

// A recording unit needs only its type (the identifier is generated server-side, as in JSF). Created
// from another UE's row action, it is linked to it in the same transaction: as its child (`parent`)
// or its parent (`child`).
export function RecordingUnitCreateForm(ctx: CreateFormContext) {
  const { prefill } = ctx;
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="recordingUnit"
      title={t("create.newRecordingUnit")}
      typesSegment="recording-unit-types"
      create={(projectId, typeId) =>
        createRecordingUnit({
          projectId,
          typeId,
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
