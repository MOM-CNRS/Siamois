import { useRef, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { OverlayPanel } from "primereact/overlaypanel";
import { Skeleton } from "primereact/skeleton";
import { Toolbar } from "primereact/toolbar";
import { Tooltip } from "primereact/tooltip";
import { queryKeys } from "../../api/queryKeys";
import type { CreatePrefill, EntityTypeConfig, ListScope } from "../../entities/types";
import { t } from "../../i18n";
import { BottomSheet } from "../../components/BottomSheet";
import { useIsMobile } from "../../viewport";

export interface ListToolbarProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  organizationId?: number;
  scope?: ListScope;
  // The left group: the gear, the search box, the filter chips.
  start: ReactNode;
  // The filter chips: after the group above, and the first thing to drop to a second line when the toolbar is narrow.
  startFilters?: ReactNode;
  isLoading: boolean;
  // The size of the filtered result set, and how many of its rows are ticked.
  totalCount: number;
  selectedCount: number;
  onClearSelection: () => void;
  // false: no "Créer" at all (a relation tab whose plain create wouldn't be linked to its parent).
  creatable?: boolean;
  createPrefill?: CreatePrefill;
  // A control the embedding place adds before « Créer ».
  endExtra?: ReactNode;
  // The create form needs a project picked, and none accepts this kind: the button is disabled.
  createBlocked: { loading: boolean } | null;
  onCreate?: () => void;
  // Opens a created entity where a row's own identifier would.
  onCreated: (id: string | number) => void;
}

/**
 * p:toolbar (pages/shared/table/tableToolbar.xhtml) → PrimeReact Toolbar, not a plain div — its own
 * generated classes are what render the chrome the stock theme actually paints. The list's own
 * controls on the left, the selection count and "Créer" on the right.
 */
export function ListToolbar({
  entityType,
  config,
  organizationId,
  scope,
  start,
  startFilters,
  isLoading,
  totalCount,
  selectedCount,
  onClearSelection,
  creatable,
  createPrefill,
  endExtra,
  createBlocked,
  onCreate,
  onCreated,
}: ListToolbarProps) {
  const queryClient = useQueryClient();
  const createOverlayRef = useRef<OverlayPanel>(null);
  // Phone width: the creation form is a drawer from the bottom rather than an overlay under the button.
  const mobile = useIsMobile();
  const [createSheetOpen, setCreateSheetOpen] = useState(false);
  const hideCreate = () => (mobile ? setCreateSheetOpen(false) : createOverlayRef.current?.hide());
  const blockedCreateRef = useRef<HTMLSpanElement>(null);

  function createButton(): ReactNode {
    if (creatable === false) return null;
    if (config.list.createForm && createBlocked) {
      // No project to create in: the button stays visible but disabled (JSF's ToolbarCreateConfig
      // "unavailable" state), saying why.
      // The reason is a Tooltip targeting a wrapper, not the Button's own `tooltip` + showOnDisabled:
      // that moves the disabled button into a wrapper of its own outside React's tree, and the
      // toolbar's next re-render (the count skeleton becoming the count chip) then fails with
      // "insertBefore … is not a child of this node", taking the whole panel down. The wrapper is
      // never disabled, so the Tooltip leaves the DOM alone (the button itself ignores the pointer,
      // see main-panel.css, so the wrapper is what gets hovered). Always rendered, only switched
      // off while loading, so the toolbar's structure doesn't change when the answer arrives.
      return (
        <>
          <span ref={blockedCreateRef} className="entity-list-panel-create-blocked">
            <Button label={t("list.create")} icon="bi bi-plus-square" disabled />
          </span>
          <Tooltip
            target={blockedCreateRef}
            content={t("list.cannotCreate", { type: config.labels.singular.toLowerCase() })}
            position="left"
            disabled={createBlocked.loading}
          />
        </>
      );
    }
    if (config.list.createForm) {
      const form = () =>
        config.list.createForm!({
          organizationId,
          scope,
          prefill: createPrefill,
          onCreated: (id) => {
            hideCreate();
            void queryClient.invalidateQueries({ queryKey: queryKeys.entityList(entityType) });
            // A linked create changes the parent's relation counts (its tab badges).
            if (createPrefill) void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetails() });
            onCreated(id);
          },
          onCancel: hideCreate,
        });
      return (
        <>
          <Button
            label={t("list.create")}
            icon="bi bi-plus-square"
            onClick={(e) => (mobile ? setCreateSheetOpen(true) : createOverlayRef.current?.toggle(e))}
          />
          {mobile ? (
            <BottomSheet
              visible={createSheetOpen}
              onHide={() => setCreateSheetOpen(false)}
              ariaLabel={t("create.newOf", { label: config.labels.singular })}
              className="create-entity-sheet"
            >
              {createSheetOpen && form()}
            </BottomSheet>
          ) : (
            <OverlayPanel ref={createOverlayRef} className="entity-list-panel-create-overlay">
              {form()}
            </OverlayPanel>
          )}
        </>
      );
    }
    return onCreate && <Button label={t("list.create")} icon="bi bi-plus-square" onClick={onCreate} />;
  }

  return (
    <Toolbar
      className="entity-list-panel-toolbar"
      start={
        <>
          <div className="entity-list-panel-toolbar-main">
            {/* selectedCountChip (tableToolbar.xhtml): first in the toolbar, before the table's gear: selected / total of the FILTERED result set —
                the only place that total shows in a relation tab, whose badge counts every related
                row. Here rather than in the selection column's header, which it widened. Its ×
                (only once rows are selected) clears the selection. */}
            {isLoading ? (
              <Skeleton width="3rem" height="1.5rem" borderRadius="16px" />
            ) : (
              <Chip
                // Remounted when the selection empties or fills: PrimeReact's Chip hides itself on
                // remove and would otherwise stay hidden.
                key={selectedCount > 0 ? "selected" : "none"}
                className="entity-list-panel-selection-count"
                label={`${selectedCount}/${totalCount}`}
                removable={selectedCount > 0}
                onRemove={() => {
                  onClearSelection();
                  return false;
                }}
              />
            )}
            {start}
          </div>
          {startFilters}
        </>
      }
      end={
        <>
          {endExtra}
          {createButton()}
        </>
      }
    />
  );
}
