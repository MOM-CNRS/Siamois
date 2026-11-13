import {useState} from "react";
import {AutoComplete, type AutoCompleteCompleteEvent} from "primereact/autocomplete";
import {listDocuments} from "../../../entities/document/api";
import type {DocumentSummary} from "../../../entities/document/types";
import type {BulkLinkMode} from "../../../entities/shared/bulkApi";
import {t} from "../../../i18n";
import {type BulkEditorCommonProps, BulkEditorFooter, BulkLinkModeButton} from "./BulkEditorParts";

export interface BulkDocumentEditorProps extends BulkEditorCommonProps {
  organizationId?: number;
  // The selection's one project, whose documents are offered; none (places): the organization's.
  projectId?: string;
  onApply: (documentId: string | number, mode: BulkLinkMode) => void;
}

/**
 * What a document is called in the picker: its identifier, then its title when it differs.
 */
function labelOf(doc: DocumentSummary): string {
  return [doc.identifier, doc.label && doc.label !== doc.identifier ? doc.label : null].filter(Boolean).join(" — ");
}

/**
 * The « Documents » relation of the selection: adds the link to one existing document to every
 * selected entity, or removes it from each (the document itself stays). The bulk counterpart of a
 * Documents tab's « Associer » and « Retirer le lien ».
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
  const [mode, setMode] = useState<BulkLinkMode>("LINK");
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
      <BulkLinkModeButton mode={mode} onChange={setMode} disabled={running} ariaLabel={t("entity.document.plural")} />
      <small className="bulk-field-editor-hint">
        {t(mode === "LINK" ? "bulk.documentAddHint" : "bulk.documentRemoveHint")}
      </small>
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
      <BulkEditorFooter
        editableCount={editableCount}
        skippedCount={skippedCount}
        running={running}
        canApply={picked?.id != null}
        onApply={() => picked?.id != null && onApply(picked.id as string | number, mode)}
        onCancel={onCancel}
      />
    </div>
  );
}
