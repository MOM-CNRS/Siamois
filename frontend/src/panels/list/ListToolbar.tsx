import { useRef, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { Chip } from "primereact/chip";
import { OverlayPanel } from "primereact/overlaypanel";
import { Skeleton } from "primereact/skeleton";
import { Toolbar } from "primereact/toolbar";
import { queryKeys } from "../../api/queryKeys";
import type { CreatePrefill, EntityTypeConfig, ListScope } from "../../entities/types";
import { t } from "../../i18n";

export interface ListToolbarProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  organizationId?: number;
  scope?: ListScope;
  // The left group: the gear, the search box, the filter chips.
  start: ReactNode;
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

  function createButton(): ReactNode {
    if (creatable === false) return null;
    if (config.list.createForm && createBlocked) {
      // No project to create in: the button stays visible but disabled (JSF's ToolbarCreateConfig
      // "unavailable" state), saying why.
      return (
        <Button
          label={t("list.create")}
          icon="bi bi-plus-square"
          disabled
          tooltip={
            createBlocked.loading
              ? undefined
              : t("list.cannotCreate", { type: config.labels.singular.toLowerCase() })
          }
          tooltipOptions={{ showOnDisabled: true, position: "left" }}
        />
      );
    }
    if (config.list.createForm) {
      return (
        <>
          <Button label={t("list.create")} icon="bi bi-plus-square" onClick={(e) => createOverlayRef.current?.toggle(e)} />
          <OverlayPanel ref={createOverlayRef} className="entity-list-panel-create-overlay">
            {config.list.createForm({
              organizationId,
              scope,
              prefill: createPrefill,
              onCreated: (id) => {
                createOverlayRef.current?.hide();
                void queryClient.invalidateQueries({ queryKey: queryKeys.entityList(entityType) });
                // A linked create changes the parent's relation counts (its tab badges).
                if (createPrefill) void queryClient.invalidateQueries({ queryKey: queryKeys.entityDetails() });
                onCreated(id);
              },
              onCancel: () => createOverlayRef.current?.hide(),
            })}
          </OverlayPanel>
        </>
      );
    }
    return onCreate && <Button label={t("list.create")} icon="bi bi-plus-square" onClick={onCreate} />;
  }

  return (
    <Toolbar
      className="entity-list-panel-toolbar"
      start={start}
      end={
        <>
          {/* selectedCountChip (tableToolbar.xhtml): selected / total of the FILTERED result set —
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
          {endExtra}
          {createButton()}
        </>
      }
    />
  );
}
