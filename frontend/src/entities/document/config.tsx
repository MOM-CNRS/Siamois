import type { EntityTypeConfig } from "../types";
import { loadTypeCatalog } from "../typeCatalog";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { getDocument, listDocuments, patchDocumentAnswers } from "./api";
import { documentColumns } from "./columns";
import { DocumentCreateForm } from "./CreateForm";
import { IdentifierTypeHeader } from "../../components/IdentifierTypeHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import type { DocumentDetail, DocumentSummary } from "./types";
import { t } from "../../i18n";

// The document's own EntityTypeConfig: an independent card on the home, an organization-wide list, a
// project list, and a fiche composed from the project's configuration of the document table (its
// "type" is the category). Its dynamic column catalog comes from the project's forms
// (entities/typeCatalog.ts).
export const documentEntityConfig: EntityTypeConfig<DocumentSummary, DocumentDetail> = {
  key: "document",
  labels: { singular: t("entity.document.singular"), plural: t("entity.document.plural"), all: t("entity.document.all") },
  collectionPath: "documents",
  // Matches DocumentPanel's own icon.
  icon: "bi bi-file-earmark-text",
  panelClass: "document-panel",
  api: {
    siblings: (id) => fetchSiblings("documents", id),
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
    header: (entity) => (
      <IdentifierTypeHeader
        entityType="document"
        chipPrefix="document"
        label={entity.label || entity.identifier}
        typeLabel={entity.type?.resolvedLabel}
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
      chipClassName: "document-count-chip-alt",
      order: 55,
    }),
  },
};
