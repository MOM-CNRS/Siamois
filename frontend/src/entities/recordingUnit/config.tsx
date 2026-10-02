import type { EntityRef, EntityTypeConfig, ListScope } from "../types";
import { relationTab } from "../../panels/relationTab";
import { documentsTab } from "../document/documentsTab";
import { bookmarkChrome } from "../chrome";
import { fetchSiblings } from "../siblingsApi";
import { scopeProjectId } from "../scope";
import { duplicateRecordingUnit, duplicateRecordingUnitStructure, getRecordingUnit, getRecordingUnitStructure, listRecordingUnits, patchRecordingUnitAnswers } from "./api";
import { recordingUnitColumns } from "./columns";
import { RecordingUnitCreateForm } from "./CreateForm";
import { RecordingUnitDetailHeader } from "./DetailHeader";
import { SchemaFicheTab } from "../../components/SchemaFicheTab";
import { getOrganizationRecordingUnitTypes, getRecordingUnitTypes } from "./recordingUnitTypes";
import { jsfRoutes } from "../routes";
import { countCardWidgets } from "../countCard";
import type { RecordingUnitDetail, RecordingUnitSummary } from "./types";
import { t } from "../../i18n";

function recordingUnitRef(ru: RecordingUnitSummary): EntityRef {
  return { id: ru.id, label: ru.fullIdentifier };
}

// A new UE or find created from this UE belongs to the same project.
function projectScope(ru: RecordingUnitSummary): ListScope | undefined {
  return ru.projectId ? { entityType: "project", id: ru.projectId } : undefined;
}

// RecordingUnit's own EntityTypeConfig (plan: generic related-list tab) — registered so it can be
// looked up by key ("recordingUnit") both by the registry's generics-erasure boundary and by
// entities/project/config.tsx's relationTab, which only ever references it by that string.
//
// detail.tabs/header now built (fiche + identifier/category header, mirroring Project's own —
// see FicheTab.tsx/DetailHeader.tsx for what's deliberately still absent and why). Prev/next is
// scoped to the RU's own project (GET /recording-units/{id}/siblings), not an organization the way
// Project's is.
export const recordingUnitEntityConfig: EntityTypeConfig<RecordingUnitSummary, RecordingUnitDetail> = {
  key: "recordingUnit",
  labels: { singular: t("entity.recordingUnit.singular"), plural: t("entity.recordingUnit.plural"), all: t("entity.recordingUnit.all") },
  collectionPath: "recording-units",
  // Matches RecordingUnitTableDefinitionFactory's own identifierCol iconClass.
  icon: "bi bi-pencil-square",
  panelClass: "recording-unit-panel",
  api: {
    siblings: (id) => fetchSiblings("recording-units", id),
    get: getRecordingUnit,
    list: listRecordingUnits,
    patchAnswers: (id, answers) => patchRecordingUnitAnswers(id, answers),
    duplicate: duplicateRecordingUnit,
  },
  // The list row's "Dupliquer" (JSF's DUPLICATE_ROW): pick the descendants and the number of copies.
  duplication: {
    load: getRecordingUnitStructure,
    run: duplicateRecordingUnitStructure,
    unit: t("entity.recordingUnit.unit"),
    maxCopies: 50,
  },
  list: {
    columns: recordingUnitColumns,
    // A project's own effective RU form(s) when the list has a project (EntityListPanel forwards its
    // own `scope` prop here — see relationTab/entities/types.ts's ListParams.scope); the
    // organization's aggregate of every project's forms for the organization-wide list.
    schema: {
      load: async ({ scope, organizationId }) => {
        const projectId = scopeProjectId(scope);
        let types;
        if (projectId != null) types = await getRecordingUnitTypes(projectId);
        else if (!scope && organizationId != null) types = await getOrganizationRecordingUnitTypes(organizationId);
        else return { fields: {}, columns: [] };
        return { fields: types.fields, columns: types.tableColumns };
      },
    },
    typesSegment: "recording-unit-types",
    defaultSort: "creationTime:desc",
    searchable: true,
    // Overlay-hosted creation form (migration plan follow-up) — see CreateForm.tsx's own doc.
    createForm: (ctx) => <RecordingUnitCreateForm {...ctx} />,
    // Created in a project: from the organization-wide list, the form picks it first.
    createProjectKind: "recordingUnit",
    mappable: true,
    card: {
      subtitle: (ru) => ru.project?.label,
      details: (ru) => [
        { icon: "bi bi-bucket", label: ru._counts?.finds ? `${ru._counts.finds} ${t("entity.find.plural").toLowerCase()}` : null },
        { icon: "bi bi-diagram-3", label: ru._counts?.children ? `${ru._counts.children} ${t("entity.recordingUnit.plural").toLowerCase()}` : null },
        { icon: "bi bi-file-earmark", label: ru._counts?.documents ? String(ru._counts.documents) : null },
      ],
    },
    // JSF's RecordingUnitTableViewModel row actions, after the generic bookmark/duplicate.
    rowActions: [
      {
        key: "new-parent",
        icon: "bi bi-node-plus-fill rotate-minus90",
        tooltip: t("row.newParentRU"),
        run: (row, ctx) => ctx.openCreate("recordingUnit", { scope: projectScope(row), prefill: { child: recordingUnitRef(row) } }),
      },
      {
        key: "new-child",
        icon: "bi bi-node-plus-fill rotate-90",
        tooltip: t("row.newChildRU"),
        run: (row, ctx) => ctx.openCreate("recordingUnit", { scope: projectScope(row), prefill: { parent: recordingUnitRef(row) } }),
      },
      {
        key: "new-find",
        icon: "bi bi-bucket",
        tooltip: t("row.newFind"),
        run: (row, ctx) => ctx.openCreate("find", { scope: projectScope(row), prefill: { recordingUnit: recordingUnitRef(row) } }),
      },
    ],
  },
  detail: {
    tabs: [
      {
        key: "fiche",
        label: t("common.details"),
        render: (entity, helpers) => (
          <SchemaFicheTab entity={entity} entityType="recordingUnit" typesSegment="recording-unit-types" save={patchRecordingUnitAnswers} onSaved={helpers.refetch} />
        ),
      },
      // JSF's hierarchy tab, reduced to what the recording unit contains (its direct children).
      relationTab<RecordingUnitDetail>({
        key: "children",
        label: t("entity.recordingUnit.plural"),
        target: "recordingUnit",
        scopeEntityType: "recordingUnit",
        path: "children",
        projectId: (entity) => entity.projectId,
        createPrefill: (entity) => ({ parent: recordingUnitRef(entity) }),
        badge: (entity) => entity._counts?.children ?? 0,
      }),
      // JSF's SpecimenTab. REST segment "mobiliers", not Find's own collectionPath.
      relationTab<RecordingUnitDetail>({
        key: "finds",
        label: t("entity.find.plural"),
        target: "find",
        scopeEntityType: "recordingUnit",
        path: "mobiliers",
        projectId: (entity) => entity.projectId,
        createPrefill: (entity) => ({ recordingUnit: recordingUnitRef(entity) }),
        badge: (entity) => entity._counts?.finds ?? 0,
      }),
      documentsTab<RecordingUnitDetail>({
        scopeEntityType: "recordingUnit",
        segment: "recording-units",
        linkField: "recordingUnitIds",
        badge: (entity) => entity._counts?.documents ?? 0,
        projectId: (entity) => entity.projectId,
        entityRef: recordingUnitRef,
      }),
    ],
    header: (entity, helpers) => <RecordingUnitDetailHeader entity={entity} onSaved={helpers.refetch} />,
    chrome: (entity) => bookmarkChrome(entity, entity.fullIdentifier),
    // The titlebar's "Créer" makes a sibling in the same project.
    createScope: (entity) => (entity.projectId ? { entityType: "project", id: entity.projectId } : undefined),
  },
  routes: jsfRoutes("recording-unit"),
  home: {
    widgets: countCardWidgets({
      entityType: "recordingUnit",
      count: "recordingUnits",
      icon: "bi bi-pencil-square",
      label: t("entity.recordingUnit.plural"),
      description: t("home.recordingUnit.description"),
      className: "sia-welcome-card sia-recording-unit",
      chipClassName: "recording-unit-count-chip-alt",
      order: 30,
    }),
  },
};
