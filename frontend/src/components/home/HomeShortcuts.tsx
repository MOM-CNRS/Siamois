import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";
import {Button} from "primereact/button";
import {Panel} from "primereact/panel";
import {Skeleton} from "primereact/skeleton";
import {deleteBookmark, listBookmarks} from "../../api/resources/bookmarks";
import {queryKeys} from "../../api/queryKeys";
import {getEntityType} from "../../entities/registry";
import type {HomeWidgetContext, HomeWidgetDef} from "../../entities/shared/types";
import {t} from "../../i18n";
import {resolveEntityPath} from "../../navigation/paths";
import {usePaneState} from "../../panels/pane/paneState";
import {ClickableCard} from "./ClickableCard";
import {DataAccessSection} from "./DataAccessSection";
import {openSidebarDrawer} from "./sidebar";

// « Mes vues épinglées » on the home page: the user's favorites that are a list or a saved view, as cards like the
// other sections. The favorite star of a list or of a view is the pin. « Voir tout » opens the sidebar's favorites,
// which hold the pinned fiches too.
const PINNED_LIMIT = 50;

/** Opens a stored path (`/recording-unit`, `/recording-unit?viewId=3`) as a click on a sidebar entry would. */
function openList(path: string, ctx: HomeWidgetContext) {
  const target = resolveEntityPath(path);
  if (!target || target.entityId != null) return;
  if (target.viewId != null) ctx.onOpenView?.(target.entityType, target.viewId);
  else ctx.onNavigate?.(target.entityType);
}

function PinnedViews(ctx: HomeWidgetContext) {
  const [folded, setFolded] = usePaneState("pinnedViewsFolded", false);
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: queryKeys.homePinnedViews(ctx.organizationId),
    queryFn: () => listBookmarks(ctx.organizationId!, PINNED_LIMIT),
    enabled: ctx.organizationId != null,
    // Favorites change all the time elsewhere: read them again each time Home is shown.
    staleTime: 0,
  });
  const unpin = useMutation({
    mutationFn: (resourceUri: string) => deleteBookmark(resourceUri, ctx.organizationId!),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: queryKeys.homePinnedViews(ctx.organizationId) }),
  });
  // A list, or a list on a saved view: a favorite fiche is not a view.
  const pinned = (data ?? []).flatMap((bookmark) => {
    const target = resolveEntityPath(bookmark.resourceUri);
    return target && target.entityId == null ? [{ bookmark, type: getEntityType(target.entityType) }] : [];
  });

  return (
    <Panel
      header={
        <span className="home-section-title">
          {t("home.pinned")}
          <Button text size="small" label={t("home.seeFavorites")} icon="bi bi-star" onClick={() => openSidebarDrawer("bookmarks")} />
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
      {!isLoading && pinned.length === 0 && <div>{t("home.noPinned")}</div>}
      {pinned.length > 0 && (
        <div className="sia-card-grid">
          {pinned.map(({ bookmark, type }) => (
            <ClickableCard
              key={bookmark.id}
              ariaLabel={bookmark.title}
              onOpen={() => openList(bookmark.resourceUri, ctx)}
              className={`sia-welcome-card ${type?.cardClassName ?? ""}`}
            >
              <div className="sia-count-card-row">
                <i className={type?.icon ?? "bi bi-star"} />
                <span className="sia-count-card-title">{bookmark.title}</span>
                <Button
                  text
                  rounded
                  size="small"
                  icon="bi bi-pin-angle-fill"
                  className="home-pinned-unpin"
                  aria-label={t("home.unpin")}
                  tooltip={t("home.unpin")}
                  onClick={(e) => {
                    // The card itself opens the view.
                    e.stopPropagation();
                    unpin.mutate(bookmark.resourceUri);
                  }}
                />
              </div>
            </ClickableCard>
          ))}
        </div>
      )}
    </Panel>
  );
}

export function homeShortcutWidgets(ctx: HomeWidgetContext): HomeWidgetDef[] {
  return [
    { key: "home-pinned-views", render: () => <PinnedViews {...ctx} /> },
    { key: "home-data-access", render: () => <DataAccessSection {...ctx} /> },
  ];
}
