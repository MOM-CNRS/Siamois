import {useEffect} from "react";
import {useQuery} from "@tanstack/react-query";
import {fetchBreadcrumbs} from "../entities/shared/breadcrumbsApi";
import {PROJECT_KEY} from "../entities/keys";
import {getEntityType} from "../entities/registry";
import {publishContext} from "./externalBus";
import type {NavigationView} from "./reducer";

/**
 * Tells the search bar (another React root: see externalBus) where the user is: the organization,
 * and the project the main pane shows or belongs to. Renders nothing; lives inside the query provider
 * because a fiche that is not a project learns its project from its breadcrumbs.
 */
export function ContextPublisher({ view, organizationId }: Readonly<{ view: NavigationView; organizationId?: number }>) {
  const isFiche = view.panelKind === "detail" && view.entityId != null;
  const collectionPath = getEntityType(view.entityType)?.collectionPath;

  const breadcrumbs = useQuery({
    queryKey: ["search-context-breadcrumbs", view.entityType, view.entityId],
    queryFn: () => fetchBreadcrumbs(collectionPath!, view.entityId!),
    enabled: isFiche && collectionPath != null,
    staleTime: 60_000,
    retry: false,
  });
  const project = breadcrumbs.data?.paths[0]?.find((crumb) => crumb.type === PROJECT_KEY);

  const projectId = !isFiche ? undefined : view.entityType === PROJECT_KEY ? String(view.entityId) : project?.id;
  const projectLabel = view.entityType === PROJECT_KEY ? (view.preview?.label ?? project?.label) : project?.label;

  useEffect(() => {
    publishContext({ organizationId, projectId, projectLabel });
  }, [organizationId, projectId, projectLabel]);

  return null;
}
