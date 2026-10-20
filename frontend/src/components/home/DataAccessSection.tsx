import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { Panel } from "primereact/panel";
import { deleteHomeAccess, listHomeAccesses, type HomeAccess } from "../../api/homeAccesses";
import { queryKeys } from "../../api/queryKeys";
import { getEntityType } from "../../entities/registry";
import type { HomeWidgetContext } from "../../entities/types";
import { t } from "../../i18n";
import { usePaneState } from "../../panels/paneState";
import { AddDataAccessDialog } from "./AddDataAccessDialog";
import { ClickableCard } from "./ClickableCard";
import { openSidebarDrawer } from "./sidebar";

/**
 * « Accès aux données »: the cards the organization's managers set up — a table on a view (so, say, only one type) —
 * for everybody. Who may edit the organization's default views adds and removes them; for the others the section
 * is there only once it has something.
 */
export function DataAccessSection({ organizationId, onOpenView }: HomeWidgetContext) {
  const [folded, setFolded] = usePaneState("dataAccessFolded", false);
  const [adding, setAdding] = useState(false);
  const queryClient = useQueryClient();
  const { data } = useQuery({
    queryKey: queryKeys.homeAccesses(organizationId),
    queryFn: () => listHomeAccesses(organizationId!),
    enabled: organizationId != null,
    staleTime: 0,
  });
  const remove = useMutation({
    mutationFn: (id: number) => deleteHomeAccess(organizationId!, id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: queryKeys.homeAccesses(organizationId) }),
  });

  const accesses = data?.data ?? [];
  const canEdit = data?.canEdit === true;
  if (organizationId == null || (accesses.length === 0 && !canEdit)) return null;

  return (
    <>
      <Panel
        header={
          <span className="home-section-title">
            {t("home.dataAccess")}
            {canEdit && <Button text size="small" label={t("home.dataAccess.addButton")} icon="bi bi-plus-square" onClick={() => setAdding(true)} />}
            <Button text size="small" label={t("home.dataAccess.seeAll")} icon="bi bi-file-earmark-spreadsheet" onClick={() => openSidebarDrawer("views")} />
          </span>
        }
        toggleable
        collapsed={folded}
        onToggle={(e) => setFolded(e.value)}
        className="sia-form-panel"
      >
        {accesses.length === 0 && <div>{t("home.dataAccess.empty")}</div>}
        {accesses.length > 0 && (
          <div className="sia-card-grid">
            {accesses.map((access) => (
              <DataAccessCard
                key={access.id}
                access={access}
                organizationId={organizationId}
                canEdit={canEdit}
                onOpen={() => onOpenView?.(access.resourceType, access.viewId)}
                onRemove={() => remove.mutate(access.id)}
              />
            ))}
          </div>
        )}
      </Panel>
      {adding && <AddDataAccessDialog organizationId={organizationId} onHide={() => setAdding(false)} />}
    </>
  );
}

/** One card: the name of its type, and how many there are (the view's own filters apply). */
function DataAccessCard({
  access,
  organizationId,
  canEdit,
  onOpen,
  onRemove,
}: {
  access: HomeAccess;
  organizationId: number;
  canEdit: boolean;
  onOpen: () => void;
  onRemove: () => void;
}) {
  const type = getEntityType(access.resourceType);
  const filters = access.filters && Object.keys(access.filters).length > 0 ? access.filters : undefined;
  const count = useQuery({
    queryKey: queryKeys.entityListTotal(access.resourceType, organizationId, undefined, filters),
    queryFn: () => type!.api.list({ offset: 0, limit: 1, organizationId, filters }),
    select: (result) => result.totalCount,
    enabled: type != null,
    staleTime: 0,
  });
  return (
    <ClickableCard ariaLabel={access.label} onOpen={onOpen} className={`sia-welcome-card ${type?.cardClassName ?? ""}`}>
      <div className="sia-count-card-row">
        <i className={type?.icon ?? "bi bi-table"} />
        <span className="sia-count-card-title">{access.label}</span>
        {count.data != null && <Chip label={String(count.data)} className={type?.countChipClassName} />}
        {canEdit && (
          <Button
            text
            rounded
            size="small"
            icon="bi bi-trash"
            className="home-data-access-remove"
            aria-label={t("home.dataAccess.remove")}
            tooltip={t("home.dataAccess.remove")}
            onClick={(e) => {
              // The card itself opens the table.
              e.stopPropagation();
              onRemove();
            }}
          />
        )}
      </div>
    </ClickableCard>
  );
}
