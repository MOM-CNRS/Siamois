import { TypeOnlyCreateForm } from "../../components/create/TypeOnlyCreateForm";
import type { CreateFormContext } from "../shared/types";
import { createStudy } from "./api";
import { t } from "../../i18n";

// A study is created in a project with nothing but its type: the server numbers it, and everything
// else (the linked recording units and finds, the treatment's columns…) is edited on the fiche.
export function StudyCreateForm(ctx: CreateFormContext) {
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="study"
      title={t("create.newStudy")}
      typesSegment="study-types"
      create={createStudy}
    />
  );
}
