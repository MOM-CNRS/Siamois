import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BreadCrumb } from "primereact/breadcrumb";
import { Chip } from "primereact/chip";
import { Panel } from "primereact/panel";
import { TabView, TabPanel } from "primereact/tabview";
import { apiUrl } from "../api/basePath";
import { getEntityType } from "../entities/registry";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import { SiblingNav } from "../components/SiblingNav";
import { CreateEntityDialog } from "../components/CreateEntityDialog";
import type { PanelActions, PanelToolbarSlot } from "../mountOptions";
import { useBridge } from "./bridge";
import { useCanEdit } from "./writeMode";

export interface EntityDetailPanelProps {
  entityType: string;
  entityId: string | number;
  // Omitted for the overview pane render call that has no toolbar to show, or once the main
  // pane has navigated away from the entity this toolbar was built for (App.tsx).
  toolbar?: PanelToolbarSlot;
  // Makes the breadcrumb's "Projets" crumb switch the pane to that entity's list, the same way
  // EntityListPanel's own row click navigates. Omitted for the overview pane's OWN breadcrumb
  // (there is no "list" render inside the overview for that crumb to switch to — JSF's own
  // breadcrumb there renders without driving anything either).
  //
  // This and the next three props are also forwarded into every tab's DetailTabHelpers (plan:
  // generic related-list tab) so a tab rendering an embedded EntityListPanel (relationTab) gets
  // exactly what App.tsx's own top-level list gets — no separate wiring per tab. `onOpenOverview`
  // and `overviewEntityId` ARE passed for the overview pane's own instance too (App.tsx): a
  // relationTab rendered there (e.g. a project's own UE list, shown while that project is itself
  // open in the overview) must still be able to retarget the overview on a row click, even though
  // the click originates from within the overview.
  onNavigate?: (entityType: string, id?: string | number) => void;
  organizationId?: number;
  onOpenOverview?: (entityType: string, id: string | number) => void;
  overviewEntityId?: string | number;
  // "Fiche précédente/suivante" (plan: prev/next navigation). Deliberately NOT `onNavigate`: that
  // callback also drives the breadcrumb's "Tous les <plural>" crumb, which must switch the PANE
  // this panel is in to a list — a sibling jump must instead stay a same-kind detail navigation
  // (the main pane's own client-side nav for the main pane's fiche, App's openOverview for the
  // overview pane's fiche). Omitted, or the entity type has no config.api.siblings: no arrows.
  onNavigateSibling?: (id: string | number) => void;
  // True while the pane's own navigation bridge call is in flight (App.tsx's overviewBusy for the
  // overview pane) — both arrows render disabled rather than firing a jump the bean isn't ready
  // to catch up with yet, same reason the overview toolbar's own actions are withheld then.
  siblingNavDisabled?: boolean;
}

/**
 * Generic entity detail — tabs come entirely from config.detail.tabs (plan §3/§4). Project's
 * config registers exactly one tab (the fiche) in phase 4/6; other entities can register more
 * without any change here.
 *
 * The whole thing is one PrimeReact <Panel> (plan §7/§8 follow-up: "the toolbar is part of the
 * panel header, panels should be React panels themselves" / "reproduce the JSF header: title +
 * toolbar"). Its `header` is a single PanelHeaderBar — icon + entity singular label as the title
 * (matching EntityListPanel's own icon+label header), followed by config.detail.header?.(entity,
 * helpers) (actionUnitPanelHeader.xhtml's identifier/type/name/location chips), with the
 * toolbar on the right — one node, not split across <Panel>'s header/icons props.
 */
export function EntityDetailPanel({
  entityType,
  entityId,
  toolbar,
  onNavigate,
  organizationId,
  onOpenOverview,
  overviewEntityId,
  onNavigateSibling,
  siblingNavDisabled,
}: EntityDetailPanelProps) {
  const config = getEntityType(entityType);

  // Mirrors AbstractSingleEntityPanel.activeTabIndex — kept as this component's OWN state,
  // deliberately not TabView's own uncontrolled internal index. A sibling jump changes `entityId`,
  // which changes the detail query's key and makes this component render its "Loading…" early
  // return for a tick; that early return doesn't mount a <TabView> at all, so an uncontrolled
  // index would silently reset to 0 on the entity that follows the loading tick. This component
  // itself never unmounts across that tick (same type, same position in the tree), so this state
  // survives it — the fiche stays on whichever tab you were looking at, same as JSF's `?tab=N`.
  const [activeTabIndex, setActiveTabIndex] = useState(0);
  // ...but ONLY within the same entity type: retargeting the overview to a DIFFERENT entity type
  // (a relationTab row click — "recording units" inside a project's fiche — opening a recording
  // unit in the overview in its place) can change how many tabs even exist, so an index carried
  // over from the previous type could point past the new one's last tab and render nothing.
  // "Adjust state during render" (React's own pattern for this, not a useEffect — that would
  // commit the stale index for one paint before correcting it): comparing against the last seen
  // entityType and resetting synchronously, within the same render, whenever it changes.
  const [renderedForType, setRenderedForType] = useState(entityType);
  if (entityType !== renderedForType) {
    setRenderedForType(entityType);
    setActiveTabIndex(0);
  }

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["entity-detail", entityType, entityId],
    queryFn: () => config!.api.get(entityId),
    enabled: config != null,
  });

  // Separate from the detail query so a slow/failed siblings lookup never blocks the fiche itself
  // from rendering — the arrows just stay disabled (SiblingNav treats `undefined` as "not ready
  // yet") until this resolves. Only ever issued when the entity type registers `api.siblings`.
  const { data: siblings } = useQuery({
    queryKey: ["entity-siblings", entityType, entityId, organizationId],
    queryFn: () => config!.api.siblings!(entityId, { organizationId }),
    enabled: config?.api.siblings != null,
  });

  // The titlebar's entity actions (JSF: create/duplicate/settings) — computed from this entity's
  // own data, so they are right for whichever entity is displayed, however it got here. Hooks
  // stay above the early returns below.
  const canEdit = useCanEdit(data as { _permissions?: { canEdit?: boolean } } | undefined);
  const bridge = useBridge();
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const duplicateMutation = useMutation({
    mutationFn: () => config!.api.duplicate!(entityId),
    onSuccess: (copy) => {
      void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
      // JSF opens the copy in the overview (FlowBean.addRecordingUnitToOverview).
      (onOpenOverview ?? onNavigate)?.(entityType, copy.id);
    },
  });

  if (!config) {
    return <div className="entity-detail-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }
  if (isLoading) {
    return <div className="entity-detail-panel-loading">Loading…</div>;
  }
  if (error) {
    return <div className="entity-detail-panel-error">{(error as Error).message}</div>;
  }
  if (!data) {
    return null;
  }

  const helpers = { refetch: () => void refetch(), organizationId, onNavigate, onOpenOverview, overviewEntityId };

  // A toolbar built server-side (MountOptions) has no entity data to derive chrome from at that
  // point — it's the initial mount's own entity. Once we have the fetched entity, prefer chrome
  // freshly derived from it via config.detail.chrome when the entity type registers one: this is
  // what makes a client-opened overview's toolbar (plan §8 phase 5, EntityListPanel's
  // onOpenOverview — no server round-trip to build a toolbar from) possible at all, and it also
  // keeps title/bookmark state current after an in-place edit for the originally-seeded overview.
  const settingsProjectId = config.detail.settingsProjectId?.(data);
  const entityActions: PanelActions = {
    create: canEdit && config.list.createForm ? () => setCreateOpen(true) : undefined,
    duplicate:
      canEdit && config.api.duplicate && !duplicateMutation.isPending ? () => duplicateMutation.mutate() : undefined,
    settings:
      settingsProjectId != null && bridge.openProjectSettings
        ? () => bridge.openProjectSettings!(settingsProjectId)
        : undefined,
  };
  const resolvedToolbar: PanelToolbarSlot | undefined = toolbar && {
    ...toolbar,
    chrome: config.detail.chrome?.(data) ?? toolbar.chrome,
    actions: { ...toolbar.actions, ...entityActions },
  };

  return (
    <Panel
      className="entity-detail-panel"
      header={
        <PanelHeaderBar
          title={
            <>
              <i className={config.icon} style={{ fontSize: "2rem", color: "var(--main-color)" }} />
              <span style={{ paddingRight: "0.5em" }}>{config.labels.singular}</span>
              {config.api.siblings && onNavigateSibling && (
                <SiblingNav siblings={siblings} onNavigate={onNavigateSibling} disabled={siblingNavDisabled} />
              )}
              {config.detail.header?.(data, helpers)}
            </>
          }
          toolbar={resolvedToolbar}
        />
      }
    >
      {/* singleUnitPanel.xhtml's own p:breadCrumb, above the tabs and inside the panel body.
          AbstractSingleEntityPanel.getAllParentBreadcrumbModels builds exactly two items for a
          non-hierarchical entity like Project — the home item and the "root type" item — both of
          which the registry already knows, so this needs no endpoint. An entity whose panel
          overrides that method with real parents (Specimen, Phase, Container) will need its own
          crumbs from config; it gets this two-item base until then. */}
      <BreadCrumb
        className="panel-bc"
        // createHomeItem: icon only, no label, and a real redirect to the dashboard
        // (FlowBean.redirectToDashboard → /focus/L3dlbGNvbWU=, the base64url of "/welcome"). Not
        // routed through onNavigate: there is no "home" entity type for the registry to resolve,
        // and the dashboard is a JSF page, not one of the three React panel kinds.
        home={{ icon: "bi bi-house", url: apiUrl("/focus/L3dlbGNvbWU=") }}
        model={[
          {
            // ActionUnitPanel.createRootTypeItem: "Tous les projets", linking to the entity's list.
            label: `Tous les ${config.labels.plural.toLowerCase()}`,
            icon: config.icon,
            command: onNavigate ? () => onNavigate(entityType) : undefined,
          },
        ]}
      />
      <CreateEntityDialog
        entityType={entityType}
        visible={createOpen}
        organizationId={organizationId}
        scope={config.detail.createScope?.(data)}
        onHide={() => setCreateOpen(false)}
        onCreated={(id) => {
          setCreateOpen(false);
          void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
          // Same destination as the list toolbar's create: the new entity's own fiche in the main
          // pane, or — from the overview pane, which has no full-navigate — the overview itself.
          (onNavigate ?? onOpenOverview)?.(entityType, id);
        }}
      />
      <TabView
        className="entity-detail-panel-tabs"
        activeIndex={activeTabIndex}
        onTabChange={(e) => setActiveTabIndex(e.index)}
      >
        {config.detail.tabs.map((tab) => {
          const badge = tab.badge?.(data);
          return (
            <TabPanel
              key={tab.key}
              header={
                badge == null ? (
                  tab.label
                ) : (
                  // actionUnitTabView.xhtml's own count pastille next to the tab label
                  // (panelModel.unit.recordingUnitCount) — a plain Chip, not PrimeReact's Badge,
                  // matching the tab-header chips elsewhere in this app (PanelHeaderBar's own
                  // count chip on the list panel).
                  <span className="entity-detail-panel-tab-header">
                    {tab.label} <Chip label={String(badge)} />
                  </span>
                )
              }
            >
              {tab.render(data, helpers)}
            </TabPanel>
          );
        })}
      </TabView>
    </Panel>
  );
}
