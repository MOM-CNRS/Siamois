import { useState } from "react";
import { CreateFormField } from "../../components/CreateFormShell";
import { TypeOnlyCreateForm } from "../../components/TypeOnlyCreateForm";
import type { CreateFormContext } from "../types";
import { createDocument, uploadDocumentFile } from "./api";
import { t } from "../../i18n";

// A document is created in a project with its category, and optionally its file (sent once the
// document exists); everything else (title, links, URL…) is edited afterwards on the fiche.
export function DocumentCreateForm(ctx: CreateFormContext) {
  const [file, setFile] = useState<File | null>(null);
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="document"
      title={t("create.newDocument")}
      typesSegment="document-types"
      typeBinding="category"
      create={async (projectId, typeId) => {
        const created = await createDocument({ projectId, categoryId: typeId });
        if (file) await uploadDocumentFile(created.id, file);
        return created;
      }}
    >
      <CreateFormField label={t("file.upload")}>
        <input type="file" data-testid="create-file-input" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      </CreateFormField>
    </TypeOnlyCreateForm>
  );
}
