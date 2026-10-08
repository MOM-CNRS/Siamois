import type { EntityTypeConfig } from "../types";
import { loadTypeCatalog } from "../typeCatalog";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { getContainer, listContainers, patchContainer, patchContainerAnswers } from "./api";
import { containerColumns } from "./columns";
import { ContainerCreateForm } from "./CreateForm";
import { SchemaEntityHeader } from "../../components/SchemaEntityHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import { documentsTab } from "../document/documentsTab";
import type { ContainerDetail, ContainerSummary } from "./types";
import { t } from "../../i18n";

// Container's own EntityTypeConfig (migration plan, lot 3 "Contenants") — registered so it can be
// looked up by key ("container") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// Its dynamic column catalog comes from the project's forms (entities/typeCatalog.ts).
export const containerEntityConfig: EntityTypeConfig<ContainerSummary, ContainerDetail> = {
  key: "container",
  labels: { singular: t("entity.container.singular"), plural: t("entity.container.plural") },
  collectionPath: "containers",
  // Matches ContainerTableDefinitionFactory/ContainerPanel's own icon.
  icon: "bi bi-box-seam",
  panelClass: "container-panel",
  api: {
    siblings: (id) => fetchSiblings("containers", id),
    get: getContainer,
    list: listContainers,
    patchAnswers: (id, answers) => patchContainerAnswers(id, answers),
  },
  list: {
    // Dynamic columns: every field of the project's container forms, additional ones included.
    schema: { load: (ctx) => loadTypeCatalog(ctx, "container-types") },
    columns: containerColumns,
    typesSegment: "container-types",
    // Every list: newest first — the order the fiche's prev/next arrows walk (↓ = the row below).
    defaultSort: "creationTime:desc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc.
    createForm: (ctx) => <ContainerCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "container",
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: t("common.details"),
        render: (entity, helpers) => (
          <SchemaFicheTab entity={entity} entityType="container" typesSegment="container-types" save={patchContainerAnswers} onSaved={helpers.refetch} />
        ),
      },
      documentsTab<ContainerDetail>({
        scopeEntityType: "container",
        segment: "containers",
        linkField: "containerIds",
        badge: (entity) => entity._counts?.documents ?? 0,
        projectId: (entity) => entity.projectId,
        entityRef: (entity) => ({ id: entity.id, label: entity.identifier || String(entity.id) }),
      }),
    ],
    header: (entity, helpers) => (
      <SchemaEntityHeader
        entityType="container"
        chipPrefix="container"
        entity={entity}
        identifier={entity.identifier ?? ""}
        typesSegment="container-types"
        patch={patchContainer}
        onSaved={helpers.refetch}
      />
    ),
    chrome: (entity) => bookmarkChrome(entity, entity.identifier),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: jsfRoutes("container"),
  home: {
    widgets: countCardWidgets({
      entityType: "container",
      count: "containers",
      icon: "bi bi-box-seam",
      label: t("entity.container.plural"),
      description: t("home.container.description"),
      className: "sia-welcome-card sia-container",
      chipClassName: "container-count-chip-alt",
      order: 60,
    }),
  },
};
