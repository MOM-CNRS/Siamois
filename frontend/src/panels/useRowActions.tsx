import { useState, type ReactNode } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { createBookmark, deleteBookmark } from "../api/bookmarks";
import { CreateEntityDialog } from "../components/CreateEntityDialog";
import type { CreatePrefill, EntityTypeConfig, ListScope, RowActionContext } from "../entities/types";

type Row = Record<string, unknown> & {
  id?: string | number;
  resourceUri?: string | null;
  bookmarked?: boolean;
  _permissions?: { canEdit?: boolean };
};

interface PendingCreate {
  entityType: string;
  scope?: ListScope;
  prefill?: CreatePrefill;
}

export interface UseRowActionsOptions {
  entityType: string;
  // Undefined for an unknown entity type (the list then renders its own error, no rows).
  config: EntityTypeConfig<unknown, unknown> | undefined;
  organizationId?: number;
  writeMode: boolean;
  // Where a created or duplicated entity opens — the overview, like JSF's row actions.
  onOpen?: (entityType: string, id: string | number) => void;
}

/**
 * A list row's action buttons — JSF's *TableViewModel.getRowActions(). Two generic ones, for every
 * entity type that can support them: the bookmark toggle (anyone, from the row's own `bookmarked`
 * and `resourceUri`) and "Dupliquer" (when the type registers `api.duplicate`). Then the entity's
 * own `config.list.rowActions` (new child/parent, new find…). Everything but the bookmark needs
 * write mode and the row's `_permissions.canEdit` — a UI gate only, the API enforces it.
 *
 * Returns the cell renderer and the one create dialog the row actions share (rendered once by the
 * list, not per row).
 */
export function useRowActions({ entityType, config, organizationId, writeMode, onOpen }: UseRowActionsOptions): {
  render: (row: Row) => ReactNode;
  dialog: ReactNode;
  error: string | null;
  clearError: () => void;
} {
  const queryClient = useQueryClient();
  const [pendingCreate, setPendingCreate] = useState<PendingCreate | null>(null);
  const [error, setError] = useState<string | null>(null);

  function refreshAfterChange() {
    // A new or copied entity shows up in every list of its type, and its parent's relation
    // counts (a detail's tab badges) change too.
    void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
    void queryClient.invalidateQueries({ queryKey: ["entity-detail"] });
  }

  const bookmarkMutation = useMutation({
    mutationFn: (row: Row) => {
      const resourceUri = row.resourceUri as string;
      if (row.bookmarked) return deleteBookmark(resourceUri, organizationId!);
      const title = config?.detail.chrome?.(row).title || String(row.fullIdentifier ?? row.name ?? row.id ?? "");
      return createBookmark({ resourceUri, titleCode: title, organizationId: organizationId! });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["entity-list"] });
      void queryClient.invalidateQueries({ queryKey: ["entity-detail"] });
      void queryClient.invalidateQueries({ queryKey: ["bookmark-status"] });
    },
    onError: (err: unknown) => setError(err instanceof Error ? err.message : "Échec du favori"),
  });

  const duplicateMutation = useMutation({
    mutationFn: (row: Row) => config!.api.duplicate!(row.id as string | number),
    onSuccess: (copy) => {
      refreshAfterChange();
      onOpen?.(entityType, copy.id);
    },
    onError: (err: unknown) => setError(err instanceof Error ? err.message : "Échec de la duplication"),
  });

  const ctx: RowActionContext = {
    openCreate: (targetType, options) => setPendingCreate({ entityType: targetType, ...options }),
  };

  function isPending(mutation: { isPending: boolean; variables?: Row }, row: Row): boolean {
    return mutation.isPending && mutation.variables?.id === row.id;
  }

  function render(row: Row): ReactNode {
    const canEdit = writeMode && row._permissions?.canEdit === true;
    const canBookmark = organizationId != null && !!row.resourceUri;
    return (
      <span className="entity-list-panel-row-actions">
        {canBookmark && (
          <Button
            icon={row.bookmarked ? "bi bi-bookmark-fill" : "bi bi-bookmark"}
            text
            rounded
            size="small"
            aria-label={row.bookmarked ? "Retirer des favoris" : "Ajouter aux favoris"}
            tooltip={row.bookmarked ? "Retirer des favoris" : "Ajouter aux favoris"}
            tooltipOptions={{ position: "top" }}
            disabled={isPending(bookmarkMutation, row)}
            onClick={(e) => {
              e.stopPropagation();
              bookmarkMutation.mutate(row);
            }}
          />
        )}
        {canEdit && config?.api.duplicate && (
          <Button
            icon="bi bi-copy"
            text
            rounded
            size="small"
            aria-label="Dupliquer"
            tooltip="Dupliquer"
            tooltipOptions={{ position: "top" }}
            disabled={isPending(duplicateMutation, row)}
            onClick={(e) => {
              e.stopPropagation();
              duplicateMutation.mutate(row);
            }}
          />
        )}
        {canEdit &&
          (config?.list.rowActions ?? []).map((action) => (
            <Button
              key={action.key}
              icon={action.icon}
              text
              rounded
              size="small"
              aria-label={action.tooltip}
              tooltip={action.tooltip}
              tooltipOptions={{ position: "top" }}
              onClick={(e) => {
                e.stopPropagation();
                action.run(row, ctx);
              }}
            />
          ))}
      </span>
    );
  }

  const dialog = (
    <CreateEntityDialog
      entityType={pendingCreate?.entityType ?? entityType}
      visible={pendingCreate != null}
      organizationId={organizationId}
      scope={pendingCreate?.scope}
      prefill={pendingCreate?.prefill}
      onCreated={(id) => {
        const created = pendingCreate!;
        setPendingCreate(null);
        refreshAfterChange();
        onOpen?.(created.entityType, id);
      }}
      onHide={() => setPendingCreate(null)}
    />
  );

  return { render, dialog, error, clearError: () => setError(null) };
}
