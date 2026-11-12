import {useState} from "react";
import {AutoComplete, type AutoCompleteCompleteEvent} from "primereact/autocomplete";
import {Button} from "primereact/button";
import {SelectButton} from "primereact/selectbutton";
import {listDocuments} from "../../../entities/document/api";
import type {DocumentSummary} from "../../../entities/document/types";
import {t, tn} from "../../../i18n";
import type {DocumentLinkMode} from "./useBulkActions";

export interface BulkDocumentEditorProps {
  organizationId?: number;
  // The selection's one project, whose documents are offered; none (places): the organization's.
  projectId?: string;
  editableCount: number;
  skippedCount: number;
  running: boolean;
  onApply: (documentId: string | number, mode: DocumentLinkMode) => void;
  onCancel: () => void;
}

/**
 * What a document is called in the picker: its identifier, then its title when it differs.
 */
function labelOf(doc: DocumentSummary): string {
  return [doc.identifier, doc.label && doc.label !== doc.identifier ? doc.label : null].filter(Boolean).join(" — ");
}

/**
 * Links one existing document to every selected entity, or removes that link from each (the
 * document itself stays). The bulk counterpart of a Documents tab's « Associer » and « Retirer le lien ».
 */
export function BulkDocumentEditor({
  organizationId,
  projectId,
  editableCount,
  skippedCount,
  running,
  onApply,
  onCancel,
}: BulkDocumentEditorProps) {
  const [mode, setMode] = useState<DocumentLinkMode>("link");
  const [query, setQuery] = useState<string | DocumentSummary>("");
  const [suggestions, setSuggestions] = useState<DocumentSummary[]>([]);
  const picked = typeof query === "object" ? query : null;

  /**
   * Searches the documents that can be linked: the project's, else the organization's.
   */
  async function search(e: AutoCompleteCompleteEvent) {
    const result = await listDocuments({
      offset: 0,
      limit: 10,
      search: e.query || undefined,
      organizationId,
      scope: projectId != null ? { entityType: "project", id: projectId } : undefined,
    });
    setSuggestions(result.data);
  }

  return (
    <div className="bulk-field-editor">
      <SelectButton
        value={mode}
        options={[
          { label: t("bulk.documentLink"), value: "link" },
          { label: t("bulk.documentUnlink"), value: "unlink" },
        ]}
        allowEmpty={false}
        disabled={running}
        onChange={(e) => setMode(e.value as DocumentLinkMode)}
        aria-label={t("bulk.documentsTitle")}
      />
      <div className="p-fluid bulk-field-editor-input">
        <AutoComplete
          value={query}
          suggestions={suggestions}
          completeMethod={search}
          minLength={0}
          dropdown
          field="identifier"
          itemTemplate={labelOf}
          placeholder={t("docs.associatePlaceholder")}
          emptyMessage={t("docs.associateNone")}
          disabled={running}
          onChange={(e) => setQuery(e.value as string | DocumentSummary)}
        />
      </div>
      {skippedCount > 0 && (
        <small className="bulk-field-editor-skipped" role="note">
          <i className="bi bi-info-circle" aria-hidden="true" /> {tn("bulk.skipped", skippedCount)}
        </small>
      )}
      <div className="bulk-field-editor-actions">
        <Button type="button" label={t("common.cancel")} text disabled={running} onClick={onCancel} />
        <Button
          type="button"
          label={tn("bulk.apply", editableCount)}
          icon="bi bi-check2-all"
          loading={running}
          disabled={running || picked?.id == null || editableCount === 0}
          onClick={() => picked?.id != null && onApply(picked.id as string | number, mode)}
        />
      </div>
    </div>
  );
}
