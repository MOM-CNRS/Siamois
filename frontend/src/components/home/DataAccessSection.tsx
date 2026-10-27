import {useState} from "react";
import {useMutation, useQuery, useQueryClient} from "@tanstack/react-query";
import {Button} from "primereact/button";
import {Chip} from "primereact/chip";
import {Dialog} from "primereact/dialog";
import {Panel} from "primereact/panel";
import {
    deleteHomeAccess,
    type HomeAccess,
    listHomeAccesses,
    reorderHomeAccesses
} from "../../api/resources/homeAccesses";
import {queryKeys} from "../../api/queryKeys";
import {getEntityType} from "../../entities/registry";
import type {HomeWidgetContext} from "../../entities/shared/types";
import {t} from "../../i18n";
import {usePaneState} from "../../panels/pane/paneState";
import {AddDataAccessDialog} from "./AddDataAccessDialog";
import {AddViewCard} from "./AddViewCard";
import {ClickableCard} from "./ClickableCard";
import {openSidebarDrawer} from "./sidebar";

/**
 * « Accès aux données »: the cards the organization's managers set up — a table on a view (so, say, only one type) —
 * for everybody. Who may edit the organization's default views adds and removes them; for the others the section
 * is there only once it has something.
 */
export function DataAccessSection({ organizationId, onOpenView }: HomeWidgetContext) {
  const [folded, setFolded] = usePaneState("dataAccessFolded", false);
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(false);
  const [removing, setRemoving] = useState<HomeAccess | null>(null);
  // Card being dragged, and the one it hovers.
  const [dragging, setDragging] = useState<number | null>(null);
  const [over, setOver] = useState<number | null>(null);
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
  const reorder = useMutation({
    mutationFn: (ids: number[]) => reorderHomeAccesses(organizationId!, ids),
    onSettled: () => void queryClient.invalidateQueries({ queryKey: queryKeys.homeAccesses(organizationId) }),
  });

  const accesses = data?.data ?? [];
  const canEdit = data?.canEdit === true;
  if (organizationId == null || (accesses.length === 0 && !canEdit)) return null;
  const editMode = editing && canEdit;

  function endDrag() {
    setDragging(null);
    setOver(null);
  }

  // Moves the dragged card to the place of the one it is dropped on, at once, then tells the server.
  function dropOn(targetId: number) {
    if (dragging == null || dragging === targetId) return;
    const ids = accesses.map((a) => a.id);
    ids.splice(ids.indexOf(dragging), 1);
    ids.splice(ids.indexOf(targetId), 0, dragging);
    const byId = new Map(accesses.map((a) => [a.id, a]));
    queryClient.setQueryData(queryKeys.homeAccesses(organizationId), {
      data: ids.map((id) => byId.get(id)!),
      canEdit,
    });
    reorder.mutate(ids);
  }

  return (
    <>
      <Panel
        header={
          <span className="home-section-title">
            {t("home.dataAccess")}
            {canEdit && (
              <Button
                text
                size="small"
                label={editMode ? t("home.dataAccess.done") : t("home.dataAccess.edit")}
                icon={editMode ? "bi bi-check-lg" : "bi bi-pencil"}
                onClick={() => setEditing(!editMode)}
              />
            )}
            <Button text size="small" label={t("home.dataAccess.seeAll")} icon="bi bi-file-earmark-spreadsheet" onClick={() => openSidebarDrawer("views")} />
          </span>
        }
        toggleable
        collapsed={folded}
        onToggle={(e) => setFolded(e.value)}
        className="sia-form-panel"
      >
        {accesses.length === 0 && !editMode && <div>{t("home.dataAccess.empty")}</div>}
        {(accesses.length > 0 || editMode) && (
          <div className="sia-card-grid">
            {accesses.map((access) => (
              <DataAccessCard
                key={access.id}
                access={access}
                organizationId={organizationId}
                editing={editMode}
                dragState={dragging === access.id ? "dragging" : over === access.id && dragging != null ? "target" : undefined}
                onDragStart={() => setDragging(access.id)}
                onDragOver={() => setOver(access.id)}
                onDrop={() => {
                  dropOn(access.id);
                  endDrag();
                }}
                onDragEnd={endDrag}
                onOpen={() => onOpenView?.(access.resourceType, access.viewId)}
                onRemove={() => setRemoving(access)}
              />
            ))}
            {editMode && <AddViewCard organizationId={organizationId} onCreate={() => setAdding(true)} />}
          </div>
        )}
      </Panel>
      <Dialog
        visible={removing != null}
        onHide={() => setRemoving(null)}
        modal
        draggable={false}
        header={t("home.dataAccess.remove")}
        style={{ width: "min(28rem, 94vw)" }}
        footer={
          <>
            <Button label={t("common.cancel")} text onClick={() => setRemoving(null)} />
            <Button
              label={t("home.dataAccess.remove")}
              icon="bi bi-trash"
              severity="danger"
              onClick={() => {
                if (removing) remove.mutate(removing.id);
                setRemoving(null);
              }}
            />
          </>
        }
      >
        <p>{t("home.dataAccess.removeConfirm")}</p>
      </Dialog>
      {adding && <AddDataAccessDialog organizationId={organizationId} onHide={() => setAdding(false)} />}
    </>
  );
}

/** One card: the name of its view, and how many there are (the view's own filters apply). */
function DataAccessCard({
  access,
  organizationId,
  editing,
  dragState,
  onDragStart,
  onDragOver,
  onDrop,
  onDragEnd,
  onOpen,
  onRemove,
}: {
  access: HomeAccess;
  organizationId: number;
  editing: boolean;
  dragState?: "dragging" | "target";
  onDragStart: () => void;
  onDragOver: () => void;
  onDrop: () => void;
  onDragEnd: () => void;
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
  const card = (
    <ClickableCard ariaLabel={access.title} onOpen={onOpen} className={`sia-welcome-card ${type?.cardClassName ?? ""}`}>
      <div className="sia-count-card-row">
        {editing && <i className="bi bi-grip-vertical home-data-access-handle" title={t("home.dataAccess.drag")} />}
        <i className={type?.icon ?? "bi bi-table"} />
        <span className="sia-count-card-title">{access.title}</span>
        {count.data != null && <Chip label={String(count.data)} className={type?.countChipClassName} />}
        {editing && (
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
  if (!editing) return card;
  return (
    <div
      className={`home-data-access-item${dragState ? ` is-${dragState}` : ""}`}
      draggable
      onDragStart={(e) => {
        e.dataTransfer.effectAllowed = "move";
        e.dataTransfer.setData("text/plain", String(access.id));
        onDragStart();
      }}
      onDragOver={(e) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
        onDragOver();
      }}
      onDrop={(e) => {
        e.preventDefault();
        onDrop();
      }}
      onDragEnd={onDragEnd}
    >
      {card}
    </div>
  );
}
