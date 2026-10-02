import { TypeOnlyCreateForm } from "../../components/TypeOnlyCreateForm";
import type { CreateFormContext } from "../types";
import { createDocument } from "./api";
import { t } from "../../i18n";

// A document is created in a project with its category; everything else (title, file, links…) is
// edited afterwards on the fiche.
export function DocumentCreateForm(ctx: CreateFormContext) {
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="document"
      title={t("create.newDocument")}
      typesSegment="document-types"
      typeBinding="category"
      create={(projectId, typeId) => createDocument({ projectId, categoryId: typeId })}
    />
  );
}
