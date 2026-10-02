import { relationTab } from "../../panels/relationTab";
import { t } from "../../i18n";
import type { DetailTabDef, DocumentLinkField, EntityRef } from "../types";
import { AssociateDocument } from "./AssociateDocument";
import { unlinkDocument } from "./api";

export interface DocumentsTabSpec<TDetail extends { id: string | number }> {
  // The fiche's own registry key ("phase", "recordingUnit"…): what the list's scope and the create form's link name.
  scopeEntityType: string;
  // The entity's REST segment, under which its documents are listed, linked and unlinked.
  segment: "recording-units" | "finds" | "places" | "phases" | "containers";
  // The DocumentCreateRequest list a new document is linked through.
  linkField: DocumentLinkField;
  badge: (entity: TDetail) => number | string | undefined;
  // The fiche's project (a place has none: its documents are the organization's).
  projectId?: (entity: TDetail) => string | number | null | undefined;
  entityRef: (entity: TDetail) => EntityRef;
}

/**
 * The « Documents » tab of a fiche: the documents linked to it, with « Créer » (the new document is
 * linked from the start), « Associer » (an existing one) and, on each row, « Retirer le lien » (the
 * document stays, only the link goes). The same tab on every entity that documents can be linked to.
 */
export function documentsTab<TDetail extends { id: string | number }>(spec: DocumentsTabSpec<TDetail>): DetailTabDef<TDetail> {
  return relationTab<TDetail>({
    key: "documents",
    label: t("entity.document.plural"),
    target: "document",
    scopeEntityType: spec.scopeEntityType,
    badge: spec.badge,
    projectId: spec.projectId,
    createPrefill: (entity) => ({ document: { field: spec.linkField, entityType: spec.scopeEntityType, ref: spec.entityRef(entity) } }),
    toolbarExtra: (entity, helpers) => (
      <AssociateDocument
        segment={spec.segment}
        entityId={entity.id}
        organizationId={helpers.organizationId}
        projectId={spec.projectId?.(entity)}
      />
    ),
    extraRowActions: (entity) => [
      {
        key: "unlink",
        icon: "bi bi-link-45deg",
        tooltip: t("docs.unlink"),
        run: (row, ctx) => {
          const documentId = (row as { id?: string | number }).id;
          if (documentId == null) return;
          ctx.confirm(t("docs.unlinkConfirm"), () => {
            unlinkDocument(spec.segment, entity.id, documentId)
              .then(ctx.refresh)
              .catch((e: unknown) => ctx.fail(e, t("docs.unlinkFailed")));
          });
        },
      },
    ],
  });
}
