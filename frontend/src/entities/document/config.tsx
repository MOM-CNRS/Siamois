import type {EntityTypeConfig} from "../shared/types";
import {deleteEntity} from "../shared/deleteApi";
import {loadTypeCatalog} from "../shared/typeCatalog";
import {bookmarkChrome} from "../shared/chrome";
import {fetchSiblings} from "../shared/siblingsApi";
import {fetchBreadcrumbs} from "../shared/breadcrumbsApi";
import {getDocument, listDocuments, patchDocument, patchDocumentAnswers} from "./api";
import {documentColumns} from "./columns";
import {BulkImportDocument} from "./BulkImportDocument";
import {DocumentCreateForm} from "./CreateForm";
import {SchemaEntityHeader} from "../../components/detail/SchemaEntityHeader";
import {SchemaFicheTab} from "../../components/detail/SchemaFicheTab";
import {jsfRoutes} from "../shared/routes";
import {countCardWidgets} from "../shared/countCard";
import type {DocumentDetail, DocumentSummary} from "./types";
import {t} from "../../i18n";

// The document's own EntityTypeConfig: an independent card on the home, an organization-wide list, a
// project list, and a fiche composed from the project's configuration of the document table (its
// "type" is the document type). Its dynamic column catalog comes from the project's forms
// (entities/shared/typeCatalog.ts).
export const documentEntityConfig: EntityTypeConfig<DocumentSummary, DocumentDetail> = {
  key: "document",
  labels: { singular: t("entity.document.singular"), plural: t("entity.document.plural"), all: t("entity.document.all") },
  collectionPath: "documents",
  // Matches DocumentPanel's own icon.
  icon: "bi bi-file-earmark-text",
  panelClass: "document-panel",
  cardClassName: "sia-document",
  countChipClassName: "document-count-chip",
  api: {
    delete: (id) => deleteEntity("documents", id),
    siblings: (id) => fetchSiblings("documents", id),
    breadcrumbs: (id, via) => fetchBreadcrumbs("documents", id, via),
    get: getDocument,
    list: listDocuments,
    patchAnswers: (id, answers) => patchDocumentAnswers(id, answers),
  },
  list: {
    // Dynamic columns: every field of the project's document forms, additional ones included.
    schema: { load: (ctx) => loadTypeCatalog(ctx, "document-types") },
    columns: documentColumns,
    typesSegment: "document-types",
    // Every list: newest first — the order the fiche's prev/next arrows walk (↓ = the row below).
    defaultSort: "creationTime:desc",
    searchable: true,
    createForm: (ctx) => <DocumentCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "document",
    // Same Import massif as on a fiche Documents tab, but no project pre-selected.
    toolbarExtra: <BulkImportDocument />,
    // Documents are looked at before they are read: their lists start as cards.
    defaultView: "cards",
    // An image document is its own picture.
    card: {
      image: (d) => (d.mimeType?.startsWith("image/") ? { documentId: d.id } : null),
      subtitle: (d) => d.type?.resolvedLabel,
    },
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: t("common.details"),
        render: (entity, helpers) => (
          <SchemaFicheTab
            entity={entity}
            entityType="document"
            typesSegment="document-types"
            save={patchDocumentAnswers}
            onSaved={helpers.refetch}
          />
        ),
      },
    ],
    header: (entity, helpers) => (
      <SchemaEntityHeader
        entityType="document"
        chipPrefix="document"
        entity={entity}
        identifier={entity.identifier ?? ""}
        title={{ value: entity.title }}
        typesSegment="document-types"
        patch={patchDocument}
        onSaved={helpers.refetch}
      />
    ),
    chrome: (entity) => bookmarkChrome(entity, entity.identifier ?? entity.label),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: jsfRoutes("document"),
  home: {
    widgets: countCardWidgets({
      entityType: "document",
      count: "documents",
      icon: "bi bi-file-earmark-text",
      label: t("entity.document.plural"),
      description: t("home.document.description"),
      className: "sia-welcome-card sia-document",
      chipClassName: "document-count-chip",
      order: 55,
    }),
  },
};
