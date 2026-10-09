import { useState } from "react";
import { CreateFormField } from "../../components/CreateFormShell";
import { CreateLinkField } from "../../components/CreateLinkField";
import { TypeOnlyCreateForm } from "../../components/TypeOnlyCreateForm";
import type { CreateFormContext } from "../types";
import { createDocument, uploadDocumentFile } from "./api";
import { t } from "../../i18n";

// A document is created in a project with its type, and optionally its file (sent once the
// document exists); everything else (title, links, URL…) is edited afterwards on the fiche.
export function DocumentCreateForm(ctx: CreateFormContext) {
  const [file, setFile] = useState<File | null>(null);
  // Created from an entity's Documents tab: linked to it from the start.
  const link = ctx.prefill?.document;
  return (
    <TypeOnlyCreateForm
      {...ctx}
      entityType="document"
      title={t("create.newDocument")}
      typesSegment="document-types"
      create={async (projectId, typeId) => {
        const created = await createDocument({
          projectId,
          typeId,
          ...(link ? { [link.field]: [link.ref.id] } : {}),
        });
        if (file) await uploadDocumentFile(created.id, file);
        return created;
      }}
    >
      {link && <CreateLinkField label={t("create.linkedTo")} entityType={link.entityType} value={link.ref} />}
      <CreateFormField label={t("file.upload")}>
        <input type="file" data-testid="create-file-input" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
      </CreateFormField>
    </TypeOnlyCreateForm>
  );
}
