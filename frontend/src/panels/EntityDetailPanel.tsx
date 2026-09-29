import { useCallback, useEffect, useRef, useState, type SyntheticEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BreadCrumb } from "primereact/breadcrumb";
import { Panel } from "primereact/panel";
import { TabView, TabPanel } from "primereact/tabview";
import { Skeleton } from "primereact/skeleton";
import { apiUrl } from "../api/basePath";
import { getEntityType } from "../entities/registry";
import { PanelHeaderBar } from "../components/PanelHeaderBar";
import { SiblingNav } from "../components/SiblingNav";
import { CreateEntityOverlay } from "../components/CreateEntityOverlay";
import type { PanelActions, PanelToolbarSlot } from "../mountOptions";
import { useBridge } from "./bridge";
import { useCanEdit, useWriteMode } from "./writeMode";
import { ValidationStatusButton } from "../components/ValidationStatusButton";
import { DetailBodySkeleton } from "../components/DetailSkeleton";

import type { EntityPreview } from "../entities/types";

export type { EntityPreview };

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
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  overviewEntityId?: string | number;
  // "Fiche précédente/suivante" (plan: prev/next navigation). Deliberately NOT `onNavigate`: that
  // callback also drives the breadcrumb's "Tous les <plural>" crumb, which must switch the PANE
  // this panel is in to a list — a sibling jump must instead stay a same-kind detail navigation
  // (the main pane's own client-side nav for the main pane's fiche, App's openOverview for the
  // overview pane's fiche). Omitted, or the entity type has no config.api.siblings: no arrows.
  onNavigateSibling?: (id: string | number, preview?: EntityPreview) => void;
  // True while the pane's own navigation bridge call is in flight (App.tsx's overviewBusy for the
  // overview pane) — both arrows render disabled rather than firing a jump the bean isn't ready
  // to catch up with yet, same reason the overview toolbar's own actions are withheld then.
  siblingNavDisabled?: boolean;
  // Shown in the header while this entity loads (see EntityPreview). Ignored once it has loaded,
  // and whenever it doesn't belong to the entity being shown.
  preview?: EntityPreview;
}

/**
 * Generic entity detail — tabs come entirely from config.detail.tabs (plan §3/§4). Project's
 * config registers exactly one tab (the fiche) in phase 4/6; other entities can register more
 * without any change here.
 *
 * The whole thing is one PrimeReact <Panel> (plan §7/§8 follow-up: "the toolbar is part of the
 * panel header, panels should be React panels themselves" / "reproduce the JSF header: title +
 * toolbar"). Its `header` is a single PanelHeaderBar — the status button, then
 * config.detail.header?.(entity, helpers) (the identifier chip as title, then the other chips);
 * the toolbar (prev/next inside its navigation group) sits on the right in the main pane and
 * first in the overview pane — one node, not split across <Panel>'s header/icons props.
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
  preview,
}: EntityDetailPanelProps) {
  const config = getEntityType(entityType);

  // Mirrors AbstractSingleEntityPanel.activeTabIndex — kept as this component's OWN state,
  // deliberately not TabView's own uncontrolled internal index. A sibling jump changes `entityId`,
  // which changes the detail query's key and makes this component render its loading skeleton
  // for a tick; that skeleton doesn't mount a <TabView> at all, so an uncontrolled
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
  const writeMode = useWriteMode();
  const bridge = useBridge();
  const queryClient = useQueryClient();
  // Hovering or focusing a prev/next arrow warms that fiche (and its own neighbours) in the query
  // cache, so the jump usually lands on data that is already there — no skeleton at all.
  const prefetchSibling = useCallback(
    (id: string | number) => {
      if (!config) return;
      void queryClient.prefetchQuery({
        queryKey: ["entity-detail", entityType, id],
        queryFn: () => config.api.get(id),
      });
      if (config.api.siblings) {
        void queryClient.prefetchQuery({
          queryKey: ["entity-siblings", entityType, id, organizationId],
          queryFn: () => config.api.siblings!(id, { organizationId }),
        });
      }
    },
    [config, entityType, organizationId, queryClient],
  );
  // Where the creation overlay opens (null = closed): the clicked "Créer" button, or the panel's
  // own toolbar when it was picked from the "…" menu, which is gone by the time the overlay opens.
  const [createAnchor, setCreateAnchor] = useState<HTMLElement | null>(null);
  const panelRef = useRef<Panel>(null);
  function openCreate(event?: SyntheticEvent) {
    const clicked = event?.currentTarget instanceof HTMLElement ? event.currentTarget : null;
    const toolbarEl = panelRef.current?.getElement()?.querySelector<HTMLElement>(".panel-toolbar") ?? null;
    setCreateAnchor(clicked && clicked.isConnected && !clicked.closest(".p-menu") ? clicked : toolbarEl);
  }
  const duplicateMutation = useMutation({
    mutationFn: () => config!.api.duplicate!(entityId),
    onSuccess: (copy) => {
      void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
      // JSF opens the copy in the overview (FlowBean.addRecordingUnitToOverview).
      (onOpenOverview ?? onNavigate)?.(entityType, copy.id);
    },
  });

  // The main pane's fiche names the browser tab after its entity (focus.xhtml's <title> only knows
  // the entity JSF rendered). The overview's fiche doesn't.
  const documentTitle =
    toolbar && !toolbar.actions?.closeOverview && data && config?.detail.chrome ? config.detail.chrome(data).title : "";
  useEffect(() => {
    if (documentTitle) document.title = documentTitle;
  }, [documentTitle]);

  if (!config) {
    return <div className="entity-detail-panel-unsupported">Unknown entity type &quot;{entityType}&quot;</div>;
  }
  const siblingNav =
    config.api.siblings && onNavigateSibling ? (
      <SiblingNav
        siblings={siblings}
        onNavigate={(sibling) => onNavigateSibling(sibling.id, { label: sibling.label })}
        onPrefetch={(sibling) => prefetchSibling(sibling.id)}
        disabled={siblingNavDisabled}
      />
    ) : undefined;
  const headerLayout = toolbar?.actions?.closeOverview ? "overview" : "main";

  if (isLoading) {
    // Same frame as the loaded fiche — panel, header, toolbar — so nothing jumps when the data
    // arrives: the identifier from the preview (or a skeleton), the rest as skeletons.
    return (
      <Panel
        className="entity-detail-panel entity-detail-panel-loading"
        header={
          <PanelHeaderBar
            layout={headerLayout}
            navigation={siblingNav}
            title={
              <span className="entity-detail-panel-preview-header">
                <Skeleton shape="circle" size="2rem" />
                {preview ? (
                  // The header's own identifier chip class (<entity>-chip-alt, filled in the
                  // entity's colour by the theme), so nothing changes colour when the entity loads.
                  <span
                    className={`p-chip p-component entity-nav-chip entity-detail-panel-preview-chip ${config.panelClass ? config.panelClass.replace(/-panel$/, "-chip-alt") : ""}`}
                  >
                    <i className={`p-chip-icon ${config.icon}`} aria-hidden="true" />
                    <span className="p-chip-text">{preview.label}</span>
                  </span>
                ) : (
                  <Skeleton width="8rem" height="2rem" borderRadius="16px" />
                )}
                <Skeleton width="6rem" height="2rem" borderRadius="16px" className="loading-skeleton" />
              </span>
            }
            toolbar={toolbar}
          />
        }
      >
        <DetailBodySkeleton />
      </Panel>
    );
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
  // Every entity resource carries its TraceableEntity status and the validator right.
  const validation = data as { validated?: string | null; _permissions?: { canValidate?: boolean } };
  const entityActions: PanelActions = {
    create: canEdit && config.list.createForm ? openCreate : undefined,
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
      ref={panelRef}
      className="entity-detail-panel"
      header={
        <PanelHeaderBar
          layout={headerLayout}
          navigation={siblingNav}
          title={
            <>
              {/* validationButton.xhtml's place in the JSF headers, now on every fiche that has a status. */}
              {validation.validated !== undefined && (
                <ValidationStatusButton
                  entityType={entityType}
                  collectionPath={config.collectionPath}
                  entityId={entityId}
                  status={validation.validated}
                  canEdit={canEdit}
                  canValidate={writeMode && validation._permissions?.canValidate === true}
                />
              )}
              {/* The identifier chip (the fiche's title), then the entity's other chips. */}
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
            label: config.labels.all ?? `Tous les ${config.labels.plural.toLowerCase()}`,
            icon: config.icon,
            command: onNavigate ? () => onNavigate(entityType) : undefined,
          },
        ]}
      />
      <CreateEntityOverlay
        entityType={entityType}
        anchor={createAnchor}
        organizationId={organizationId}
        scope={config.detail.createScope?.(data)}
        onHide={() => setCreateAnchor(null)}
        onCreated={(id) => {
          setCreateAnchor(null);
          void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
          // Same destination as the list toolbar's create: the overview, so the current view stays
          // in place (the full fiche is one click away, the overview's focus button).
          (onOpenOverview ?? onNavigate)?.(entityType, id);
        }}
      />
      <TabView
        className="entity-detail-panel-tabs"
        // One line, scrolled with ‹ › when the tabs don't fit (the narrow overview pane).
        scrollable
        activeIndex={activeTabIndex}
        onTabChange={(e) => setActiveTabIndex(e.index)}
      >
        {config.detail.tabs.map((tab) => {
          const badge = tab.badge?.(data);
          return (
            <TabPanel
              key={tab.key}
              header={
                // pages/shared/tab/tabTitle.xhtml's markup, as every JSF tab renders its title:
                // bold label, then the count in parentheses when the tab has one.
                <div style={{ display: "flex", gap: "0.2em", alignItems: "center", fontWeight: "bold" }}>
                  {tab.label}
                  {badge != null && <span>{` (${badge})`}</span>}
                </div>
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
