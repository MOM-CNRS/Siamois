import type { ReactNode, SyntheticEvent } from "react";
import { Paginator, type PaginatorPageChangeEvent } from "primereact/paginator";
import { Skeleton } from "primereact/skeleton";
import { ClickableCard } from "../../components/home/ClickableCard";
import { ValidationStatusBadge } from "../../components/table/ValidationStatusBadge";
import type { EntityPreview, EntityTypeConfig, ListParams } from "../../entities/types";
import { entityChipStyle } from "../../fields/display";
import { entityRowLabel } from "../../fields/optionSources";
import { t } from "../../i18n";
import { rememberListContext } from "../listContext";
import { PAGE_SIZE_OPTIONS } from "../tableState";
import type { RowRecord } from "./useEntityListData";

const SKELETON_CARD_COUNT = 8;

// The actions are their own targets: what they do must not also open the card.
function stopHere(e: SyntheticEvent) {
  e.stopPropagation();
}

export interface EntityCardGridProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  rows: RowRecord[];
  isLoading: boolean;
  totalCount: number;
  params: Omit<ListParams, "offset" | "limit" | "fields">;
  offset: number;
  limit: number;
  onPage: (offset: number, limit: number) => void;
  // Renders a row's actions and the dialogs they open (useRowActions) — the same bar as the table's.
  renderRowActions: (row: RowRecord) => ReactNode;
  onNavigate?: (entityType: string, id: string | number) => void;
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  overviewEntityId?: string | number;
}

/**
 * The list as a grid of cards, for an entity whose config supplies `list.card`. Same rows, same
 * server-side paging as the table (the panel's one list state): only the presentation differs.
 * The whole card opens its entity, like the table's identifier chip does.
 */
export function EntityCardGrid({
  entityType,
  config,
  rows,
  isLoading,
  totalCount,
  params,
  offset,
  limit,
  onPage,
  renderRowActions,
  onNavigate,
  onOpenOverview,
  overviewEntityId,
}: EntityCardGridProps) {
  const card = config.list.card;
  const identifierColumn = config.list.columns.find((c) => c.identifier);

  function open(row: RowRecord) {
    if (row.id == null) return;
    const at = rows.findIndex((r) => String(r.id) === String(row.id));
    if (at >= 0) rememberListContext(entityType, row.id, { params, index: offset + at });
    if (onOpenOverview) {
      onOpenOverview(entityType, row.id, {
        label: entityRowLabel(row as Parameters<typeof entityRowLabel>[0]),
        validated: (row as { validated?: string | null }).validated,
      });
    } else onNavigate?.(entityType, row.id);
  }

  function onPageChange(e: PaginatorPageChangeEvent) {
    onPage(e.first, e.rows);
  }

  function renderCard(row: RowRecord) {
    const title = identifierColumn ? identifierColumn.render(row) : entityRowLabel(row as Parameters<typeof entityRowLabel>[0]);
    const typeLabel = (row as { type?: { resolvedLabel?: string | null } | null }).type?.resolvedLabel;
    const subtitle = card?.subtitle?.(row);
    const details = (card?.details?.(row) ?? []).filter((d) => d.label);
    const selected = overviewEntityId != null && String(row.id) === String(overviewEntityId);
    return (
      // The whole card opens the entity, like the home page's cards; the row actions inside stop
      // their own clicks and keys from reaching it.
      <ClickableCard
        key={String(row.id)}
        onOpen={() => open(row)}
        className={`entity-card${selected ? " overview-open" : ""}`}
        style={entityChipStyle(entityType)}
        ariaLabel={typeof title === "string" ? title : undefined}
      >
        <header className="entity-card-header">
          <span className="entity-card-title entity-nav-chip">
            <i className={config.icon} aria-hidden="true" />
            <span className="entity-nav-chip-label">{title}</span>
          </span>
          <ValidationStatusBadge status={(row as { validated?: string | null }).validated} />
        </header>
        {typeLabel && <span className="entity-card-type">{typeLabel}</span>}
        {subtitle && <div className="entity-card-subtitle">{subtitle}</div>}
        {details.length > 0 && (
          <ul className="entity-card-details">
            {details.map((d) => (
              <li key={d.icon + d.label}>
                <i className={d.icon} aria-hidden="true" />
                <span>{d.label}</span>
              </li>
            ))}
          </ul>
        )}
        <footer className="entity-card-actions" onClick={stopHere} onKeyDown={stopHere}>
          {renderRowActions(row)}
        </footer>
      </ClickableCard>
    );
  }

  return (
    <div className="entity-card-view">
      <div className="entity-card-scroll">
        {isLoading ? (
          <div className="entity-card-grid">
            {Array.from({ length: SKELETON_CARD_COUNT }, (_, i) => (
              <Skeleton key={i} height="8rem" borderRadius="8px" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <div className="entity-card-empty">{t("list.noResults")}</div>
        ) : (
          <div className="entity-card-grid">{rows.map(renderCard)}</div>
        )}
      </div>
      <Paginator
        first={offset}
        rows={limit}
        totalRecords={totalCount}
        rowsPerPageOptions={PAGE_SIZE_OPTIONS}
        onPageChange={onPageChange}
      />
    </div>
  );
}
