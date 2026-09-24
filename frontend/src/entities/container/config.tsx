import type { EntityTypeConfig } from "../types";
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
// Reduced scope vs RecordingUnit's own config, same precedent set for Find/Phase: no
// `list.schema` (pinned columns only) and no `api.siblings`/`detail.chrome` (no bookmark/
// prev-next wiring for containers).
export const containerEntityConfig: EntityTypeConfig<ContainerSummary, ContainerDetail> = {
  key: "container",
  labels: { singular: "Contenant", plural: "Contenants" },
  collectionPath: "containers",
  // Matches ContainerTableDefinitionFactory/ContainerPanel's own icon.
  icon: "bi bi-box-seam",
  api: {
    get: getContainer,
    list: listContainers,
    patchAnswers: (id, answers) => patchContainerAnswers(id, answers),
  },
  list: {
    columns: containerColumns,
    defaultSort: "identifier:asc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc.
    createForm: (ctx) => <ContainerCreateForm {...ctx} />,
    // JSF's own organization-wide list disables creation too (ToolbarCreateConfig
    // createAllowedSupplier false): the form needs the project this list has no scope for.
    createRequiresScope: "La création de contenant n'est disponible que depuis un projet.",
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
  },
  routes: CONTAINER_ROUTES,
  home: {
    widgets: containerHomeWidgets,
  },
};
