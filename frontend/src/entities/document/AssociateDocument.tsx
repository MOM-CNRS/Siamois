import { useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { AutoComplete, type AutoCompleteCompleteEvent } from "primereact/autocomplete";
import { Button } from "primereact/button";
import { OverlayPanel } from "primereact/overlaypanel";
import { messageForError } from "../../api/errors";
import { queryKeys } from "../../api/queryKeys";
import { useNotify } from "../../notify/NotifyProvider";
import { useWriteMode } from "../../panels/writeMode";
import { t } from "../../i18n";
import { linkDocument, listDocuments } from "./api";
import type { DocumentSummary } from "./types";

export interface AssociateDocumentProps {
  // The entity's REST segment ("phases", "recording-units"…) and its id: PUT /{segment}/{id}/documents/{documentId}.
  segment: string;
  entityId: string | number;
  organizationId?: number;
  // The documents of this project are the ones that can be linked (a place has no project: any of the organization's).
  projectId?: string | number | null;
}

const labelOf = (doc: DocumentSummary) => [doc.identifier, doc.label && doc.label !== doc.identifier ? doc.label : null].filter(Boolean).join(" — ");

/**
 * « Associer » of a Documents tab: picks a document that already exists and links it to the fiche's
 * entity. The picker searches the project's documents (the API refuses another project's anyway);
 * the tab and its badge are re-fetched once linked.
 */
export function AssociateDocument({ segment, entityId, organizationId, projectId }: AssociateDocumentProps) {
  const writeMode = useWriteMode();
  const notify = useNotify();
  const queryClient = useQueryClient();
  const overlay = useRef<OverlayPanel>(null);
  const [query, setQuery] = useState<string | DocumentSummary>("");
  const [suggestions, setSuggestions] = useState<DocumentSummary[]>([]);
  const [searched, setSearched] = useState(false);
  const [busy, setBusy] = useState(false);

  if (!writeMode) return null;

  async function search(e: AutoCompleteCompleteEvent) {
    const result = await listDocuments({
      offset: 0,
      limit: 10,
      search: e.query || undefined,
      organizationId,
      scope: projectId != null ? { entityType: "project", id: projectId } : undefined,
    });
    setSuggestions(result.data);
    setSearched(true);
  }

  async function pick(doc: DocumentSummary) {
    setBusy(true);
    try {
      await linkDocument(segment, entityId, doc.id as string | number);
      overlay.current?.hide();
      setQuery("");
      void queryClient.invalidateQueries({ queryKey: queryKeys.entityList("document") });
      void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetails() });
    } catch (err) {
      notify.error(messageForError(err, t("docs.linkFailed")));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <Button label={t("docs.associate")} icon="bi bi-link-45deg" outlined disabled={busy} onClick={(e) => overlay.current?.toggle(e)} />
      <OverlayPanel ref={overlay} className="associate-document-overlay">
        <div className="associate-document">
          <strong>{t("docs.associateTitle")}</strong>
          <AutoComplete
            value={query}
            suggestions={suggestions}
            completeMethod={search}
            minLength={0}
            dropdown
            field="identifier"
            itemTemplate={labelOf}
            placeholder={t("docs.associatePlaceholder")}
            emptyMessage={searched ? t("docs.associateNone") : undefined}
            onChange={(e) => setQuery(e.value)}
            onSelect={(e) => void pick(e.value as DocumentSummary)}
          />
        </div>
      </OverlayPanel>
    </>
  );
}
