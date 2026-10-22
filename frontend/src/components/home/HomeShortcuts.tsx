import { useQuery } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Panel } from "primereact/panel";
import { Skeleton } from "primereact/skeleton";
import { listHistory } from "../../api/homeShortcuts";
import { queryKeys } from "../../api/queryKeys";
import { getEntityType } from "../../entities/registry";
import type { HomeWidgetContext, HomeWidgetDef } from "../../entities/types";
import { t } from "../../i18n";
import { resolveEntityPath } from "../../navigation/paths";
import { usePaneState } from "../../panels/paneState";
import { ProjectName } from "../ProjectName";
import { ClickableCard } from "./ClickableCard";
import { DataAccessSection } from "./DataAccessSection";
import { openSidebarDrawer } from "./sidebar";

// « Consulté récemment » on the home page: the pages the user opened last, as cards like the other sections.
// « Voir tout » opens the sidebar's history, which has the whole list.
// Asked for more than shown: the home page itself is in the history, and is left out.
const SHOWN = 8;
const RECENT_LIMIT = 20;

/** Opens a stored path (`/action-unit/12`, `/recording-unit?viewId=3`) as a click on a sidebar entry would. */
function openPath(path: string, ctx: HomeWidgetContext) {
  const target = resolveEntityPath(path);
  if (!target) return;
  if (target.entityId != null) ctx.onNavigate?.(target.entityType, target.entityId);
  else if (target.viewId != null) ctx.onOpenView?.(target.entityType, target.viewId);
  else ctx.onNavigate?.(target.entityType);
}

function RecentlyViewed(ctx: HomeWidgetContext) {
  const [folded, setFolded] = usePaneState("recentlyViewedFolded", false);
  const { data, isLoading } = useQuery({
    queryKey: queryKeys.homeHistory(ctx.organizationId),
    queryFn: () => listHistory(ctx.organizationId!, RECENT_LIMIT),
    enabled: ctx.organizationId != null,
    // Pages are opened all the time elsewhere: read them again each time Home is shown.
    staleTime: 0,
  });
  // The pages that show an entity or a list: not the home page, which is where this is shown. A fiche opened
  // from a table has the pane it was opened beside as its main (the server may still know that one as Home).
  const pages = (data ?? []).filter((page) => resolveEntityPath(page.secondaryUri ?? page.mainUri) != null).slice(0, SHOWN);

  function open(page: (typeof pages)[number]) {
    openPath(page.mainUri, ctx);
    // The page had a fiche open next to the main pane: back as it was.
    const overview = page.secondaryUri ? resolveEntityPath(page.secondaryUri) : null;
    if (overview?.entityId != null) ctx.onOpenOverview?.(overview.entityType, overview.entityId);
  }

  return (
    <Panel
      header={
        <span className="home-section-title">
          {t("home.history")}
          <Button text size="small" label={t("home.seeHistory")} icon="bi bi-clock" onClick={() => openSidebarDrawer("historique")} />
        </span>
      }
      toggleable
      collapsed={folded}
      onToggle={(e) => setFolded(e.value)}
      className="sia-form-panel"
    >
      {isLoading && (
        <div className="loading-skeleton sia-card-grid">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} height="3rem" />
          ))}
        </div>
      )}
      {!isLoading && pages.length === 0 && <div>{t("home.noHistory")}</div>}
      {pages.length > 0 && (
        <div className="sia-card-grid">
          {pages.map((page) => {
            // The card of the page's type: its colour and icon, those of the type of the fiche opened last.
            const type = getEntityType(resolveEntityPath(page.secondaryUri ?? page.mainUri)?.entityType ?? "");
            return (
              <ClickableCard
                key={page.id}
                ariaLabel={page.title}
                onOpen={() => open(page)}
                className={`sia-welcome-card ${type?.cardClassName ?? ""}`}
              >
                <div className="sia-count-card-row">
                  <i className={type?.icon ?? page.icon ?? "bi bi-clock"} />
                  <span className="sia-count-card-title">{page.title}</span>
                </div>
                {/* The context: the page a fiche was opened beside, and the project it belongs to (once if the same). */}
                {(page.secondaryUri || page.project) && (
                  <div className="sia-row-gap">
                    {page.secondaryUri && page.mainTitle !== page.project?.name && <small>{page.mainTitle}</small>}
                    <ProjectName project={page.project} />
                  </div>
                )}
              </ClickableCard>
            );
          })}
        </div>
      )}
    </Panel>
  );
}

export function homeShortcutWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [
    { key: "home-history", render: () => <RecentlyViewed {...ctx} /> },
    { key: "home-data-access", render: () => <DataAccessSection {...ctx} /> },
  ];
}
