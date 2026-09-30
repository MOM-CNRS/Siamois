import { TypeOnlyCreateForm } from "../../components/TypeOnlyCreateForm";
import type { CreateFormContext } from "../types";
import { createPhase } from "./api";
import { t } from "../../i18n";

// PhaseNewUnitForm only requires a type; the title is edited afterwards on the fiche.
export function PhaseCreateForm(ctx: CreateFormContext) {
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="phase"
      title={t("create.newPhase")}
      typesSegment="phase-types"
      create={(projectId, typeId) => createPhase({ projectId, typeId })}
    />
  );
}
