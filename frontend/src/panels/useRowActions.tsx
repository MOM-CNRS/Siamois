import { useMemo, useRef, useState, type ReactNode, type SyntheticEvent } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Menu } from "primereact/menu";
import type { MenuItem } from "primereact/menuitem";
import { createBookmark, deleteBookmark } from "../api/bookmarks";
import { CreateEntityDialog } from "../components/CreateEntityDialog";
import type { CreatePrefill, EntityTypeConfig, ListScope, RowActionContext } from "../entities/types";
import { loadListPrefs, reconcileActionBar, saveListPrefs, type ActionBarPrefs } from "./listPreferences";

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
  // listPreferences key: the action bar layout (which actions are inline, in which order) is
  // restored from and saved to this browser's storage.
  prefsKey?: string;
}

// One configurable row action — everything but the bookmark, which is always inline and first.
export interface RowActionItem {
  key: string;
  icon: string;
  label: string;
}

interface RowActionDescriptor extends RowActionItem {
  pending: (row: Row) => boolean;
  run: (row: Row) => void;
}

const DUPLICATE_KEY = "duplicate";

/**
 * A list row's action buttons — JSF's *TableViewModel.getRowActions(). Two generic ones, for every
 * entity type that can support them: the bookmark toggle (anyone, from the row's own `bookmarked`
 * and `resourceUri`) and "Dupliquer" (when the type registers `api.duplicate`). Then the entity's
 * own `config.list.rowActions` (new child/parent, new find…). Everything but the bookmark needs
 * write mode and the row's `_permissions.canEdit` — a UI gate only, the API enforces it.
 *
 * The bookmark is always shown in the row. The others follow the user's action bar layout
 * (`actionBar`, edited from the gear's "Barre d'actions"): inline in the chosen order, or put away
 * in a "…" menu.
 *
 * Returns the cell renderer and what the row actions share, rendered once by the list rather
 * than per row: the create dialog and the "…" menu.
 */
export function useRowActions({ entityType, config, organizationId, writeMode, onOpen, prefsKey }: UseRowActionsOptions): {
  render: (row: Row) => ReactNode;
  dialog: ReactNode;
  error: string | null;
  clearError: () => void;
  // The configurable actions (for the settings overlay), and their current layout.
  items: RowActionItem[];
  actionBar: ActionBarPrefs;
  setActionBar: (actionBar: ActionBarPrefs) => void;
} {
  const queryClient = useQueryClient();
  const [pendingCreate, setPendingCreate] = useState<PendingCreate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const menuRef = useRef<Menu>(null);
  const [menuRow, setMenuRow] = useState<Row | null>(null);

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

  const descriptors: RowActionDescriptor[] = [
    ...(config?.api.duplicate
      ? [
          {
            key: DUPLICATE_KEY,
            icon: "bi bi-copy",
            label: "Dupliquer",
            pending: (row: Row) => isPending(duplicateMutation, row),
            run: (row: Row) => duplicateMutation.mutate(row),
          },
        ]
      : []),
    ...(config?.list.rowActions ?? []).map((action) => ({
      key: action.key,
      icon: action.icon,
      label: action.tooltip,
      pending: () => false,
      run: (row: Row) => action.run(row, ctx),
    })),
  ];
  const keysSignature = descriptors.map((d) => d.key).join(",");
  const items = useMemo<RowActionItem[]>(
    () => descriptors.map(({ key, icon, label }) => ({ key, icon, label })),
    [keysSignature],
  );

  // The user's saved layout, lined up with the actions this list actually has.
  const [savedBar, setSavedBar] = useState<ActionBarPrefs | undefined>(() =>
    prefsKey ? loadListPrefs(prefsKey).actionBar : undefined,
  );
  const actionBar = useMemo(() => reconcileActionBar(savedBar, keysSignature ? keysSignature.split(",") : []), [savedBar, keysSignature]);

  function setActionBar(next: ActionBarPrefs) {
    setSavedBar(next);
    if (prefsKey) saveListPrefs(prefsKey, { actionBar: next });
  }

  const byKey = new Map(descriptors.map((d) => [d.key, d]));
  const inline = actionBar.order.filter((k) => actionBar.inline.includes(k)).map((k) => byKey.get(k)!).filter(Boolean);
  const inMenu = actionBar.order.filter((k) => !actionBar.inline.includes(k)).map((k) => byKey.get(k)!).filter(Boolean);

  function openMenu(e: SyntheticEvent, row: Row) {
    e.stopPropagation();
    setMenuRow(row);
    menuRef.current?.toggle(e);
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
        {canEdit &&
          inline.map((action) => (
            <Button
              key={action.key}
              icon={action.icon}
              text
              rounded
              size="small"
              data-action={action.key}
              aria-label={action.label}
              tooltip={action.label}
              tooltipOptions={{ position: "top" }}
              disabled={action.pending(row)}
              onClick={(e) => {
                e.stopPropagation();
                action.run(row);
              }}
            />
          ))}
        {canEdit && inMenu.length > 0 && (
          <Button
            icon="bi bi-three-dots"
            text
            rounded
            size="small"
            className="entity-list-panel-row-actions-more"
            aria-label="Plus d'actions"
            aria-haspopup
            tooltip="Plus d'actions"
            tooltipOptions={{ position: "top" }}
            onClick={(e) => openMenu(e, row)}
          />
        )}
      </span>
    );
  }

  const menuModel: MenuItem[] = menuRow
    ? inMenu.map((action) => ({
        label: action.label,
        icon: action.icon,
        disabled: action.pending(menuRow),
        command: (e) => {
          e.originalEvent.stopPropagation();
          action.run(menuRow);
        },
      }))
    : [];

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

  const shared = (
    <>
      {dialog}
      <Menu ref={menuRef} popup model={menuModel} className="entity-list-panel-row-actions-menu" onHide={() => setMenuRow(null)} />
    </>
  );

  return { render, dialog: shared, error, clearError: () => setError(null), items, actionBar, setActionBar };
}
