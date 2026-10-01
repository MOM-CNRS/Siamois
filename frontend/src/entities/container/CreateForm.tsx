import { TypeOnlyCreateForm } from "../../components/TypeOnlyCreateForm";
import type { CreateFormContext } from "../types";
import { createContainer } from "./api";
import { t } from "../../i18n";

// ContainerNewUnitForm only requires a type; spatial unit, dimensions and weight are edited
// afterwards on the fiche.
export function ContainerCreateForm(ctx: CreateFormContext) {
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="container"
      title={t("create.newContainer")}
      typesSegment="container-types"
      create={(projectId, typeId) => createContainer({ projectId, typeId })}
    />
  );
}
