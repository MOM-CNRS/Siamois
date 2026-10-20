import type { EntityTypeConfig } from "../types";
import { deleteEntity } from "../deleteApi";
import { loadTypeCatalog } from "../typeCatalog";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { fetchBreadcrumbs } from "../breadcrumbsApi";
import { getStudy, listStudies, patchStudy, patchStudyAnswers } from "./api";
import { studyColumns } from "./columns";
import { StudyCreateForm } from "./CreateForm";
import { SchemaEntityHeader } from "../../components/SchemaEntityHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import type { StudyDetail, StudySummary } from "./types";
import { t } from "../../i18n";

// The study's own EntityTypeConfig: an independent card on the home, an organization-wide list, a
// project list, and a fiche composed from the project's configuration of the study table. Its
// "type" decides which fields the form shows: a study of finds or of recording units links them, a
// treatment carries the columns of the SDA's "traitement" sheet. Its dynamic column catalog comes
// from the project's forms (entities/typeCatalog.ts).
export const studyEntityConfig: EntityTypeConfig<StudySummary, StudyDetail> = {
  key: "study",
  labels: { singular: t("entity.study.singular"), plural: t("entity.study.plural"), all: t("entity.study.all") },
  collectionPath: "studies",
  // Matches StudyPanel's own icon.
  icon: "bi bi-clipboard-data",
  panelClass: "study-panel",
  cardClassName: "sia-study",
  countChipClassName: "study-count-chip",
  api: {
    delete: (id) => deleteEntity("studies", id),
    siblings: (id) => fetchSiblings("studies", id),
    breadcrumbs: (id) => fetchBreadcrumbs("studies", id),
    get: getStudy,
    list: listStudies,
    patchAnswers: (id, answers) => patchStudyAnswers(id, answers),
  },
  list: {
    // Dynamic columns: every field of the project's study forms, additional ones included.
    schema: { load: (ctx) => loadTypeCatalog(ctx, "study-types") },
    columns: studyColumns,
    typesSegment: "study-types",
    // Every list: newest first — the order the fiche's prev/next arrows walk (↓ = the row below).
    defaultSort: "creationTime:desc",
    searchable: true,
    createForm: (ctx) => <StudyCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "study",
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: t("common.details"),
        render: (entity, helpers) => (
          <SchemaFicheTab entity={entity} entityType="study" typesSegment="study-types" save={patchStudyAnswers} onSaved={helpers.refetch} />
        ),
      },
    ],
    header: (entity, helpers) => (
      <SchemaEntityHeader
        entityType="study"
        chipPrefix="study"
        entity={entity}
        identifier={entity.identifier ?? ""}
        typesSegment="study-types"
        patch={patchStudy}
        onSaved={helpers.refetch}
      />
    ),
    chrome: (entity) => bookmarkChrome(entity, entity.identifier),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: jsfRoutes("study"),
  home: {
    widgets: countCardWidgets({
      entityType: "study",
      count: "studies",
      icon: "bi bi-clipboard-data",
      label: t("entity.study.plural"),
      description: t("home.study.description"),
      className: "sia-welcome-card sia-study",
      chipClassName: "study-count-chip",
      order: 65,
    }),
  },
};
