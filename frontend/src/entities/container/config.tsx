import type { EntityTypeConfig } from "../types";
import { loadTypeCatalog } from "../typeCatalog";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { getContainer, listContainers, patchContainerAnswers } from "./api";
import { containerColumns } from "./columns";
import { ContainerCreateForm } from "./CreateForm";
import { ContainerDetailHeader } from "./DetailHeader";
import { ContainerFicheTab } from "./FicheTab";
import { CONTAINER_ROUTES } from "./routes";
import { containerHomeWidgets } from "./homeWidgets";
import type { ContainerDetail, ContainerSummary } from "./types";

// Container's own EntityTypeConfig (migration plan, lot 3 "Contenants") — registered so it can be
// looked up by key ("container") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// Its dynamic column catalog comes from the project's forms (entities/typeCatalog.ts).
export const containerEntityConfig: EntityTypeConfig<ContainerSummary, ContainerDetail> = {
  key: "container",
  labels: { singular: "Contenant", plural: "Contenants" },
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
    defaultSort: "identifier:asc",
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
        label: "Détails",
        render: (entity, helpers) => <ContainerFicheTab entity={entity} onSaved={helpers.refetch} />,
      },
    ],
    header: (entity, helpers) => <ContainerDetailHeader entity={entity} onSaved={helpers.refetch} />,
    chrome: (entity) => bookmarkChrome(entity, entity.identifier),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: CONTAINER_ROUTES,
  home: {
    widgets: containerHomeWidgets,
  },
};
