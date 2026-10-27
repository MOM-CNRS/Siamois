import {type KeyboardEvent, type ReactNode, type SyntheticEvent, useEffect, useRef, useState} from "react";
import {Button} from "primereact/button";
import {Checkbox} from "primereact/checkbox";
import {Dialog} from "primereact/dialog";
import {Paginator, type PaginatorPageChangeEvent} from "primereact/paginator";
import {Skeleton} from "primereact/skeleton";
import {ValidationStatusCell, type ValidationStatusCellProps} from "../../components/table/ValidationStatusCell";
import type {EntityPreview, EntityTypeConfig, ListParams} from "../../entities/shared/types";
import {entityChipStyle} from "../../fields/display";
import {entityRowLabel} from "../../fields/optionSources";
import {t} from "../../i18n";
import {rememberListContext} from "./listContext";
import {PAGE_SIZE_OPTIONS} from "./tableState";
import type {RowRecord} from "./useEntityListData";
import {useDocumentFullImage, useDocumentThumbnail} from "./useDocumentThumbnail";

const SKELETON_CARD_COUNT = 8;

// The footer's controls are their own targets: what they do must not also open the card.
function stopHere(e: SyntheticEvent) {
  e.stopPropagation();
}

// True once the card has come near the viewport: the picture of a card far down a page is not
// fetched until it is about to be seen.
function useNearViewport(ref: React.RefObject<HTMLElement | null>): boolean {
  const [near, setNear] = useState(typeof IntersectionObserver === "undefined");
  useEffect(() => {
    const el = ref.current;
    if (near || !el) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          setNear(true);
          observer.disconnect();
        }
      },
      { rootMargin: "200px" },
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [near, ref]);
  return near;
}

// The whole image of a card, in a dialog.
function ImageDialog({ documentId, title, onHide }: { documentId: string | number; title: string; onHide: () => void }) {
  const url = useDocumentFullImage(documentId, true);
  return (
    <Dialog visible modal dismissableMask draggable={false} header={title} className="entity-card-image-dialog" onHide={onHide}>
      {url ? <img src={url} alt={title} className="entity-card-image-full" /> : <Skeleton height="50vh" />}
    </Dialog>
  );
}

interface EntityCardProps {
  entityType: string;
  config: EntityTypeConfig<unknown, unknown>;
  row: RowRecord;
  selected: boolean;
  checked: boolean;
  onToggleChecked: (row: RowRecord) => void;
  onOpen: (row: RowRecord) => void;
  actions: ReactNode;
}

function EntityCard({ entityType, config, row, selected, checked, onToggleChecked, onOpen, actions }: EntityCardProps) {
  const [zoomed, setZoomed] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const near = useNearViewport(ref);
  const card = config.list.card;
  const image = card?.image?.(row);
  const url = useDocumentThumbnail(image?.documentId, near);
  const identifierColumn = config.list.columns.find((c) => c.identifier);
  const label = entityRowLabel(row as Parameters<typeof entityRowLabel>[0]);
  const subtitle = card?.subtitle?.(row);

  // The card of the entity open in the overview comes into view when it was opened from elsewhere.
  useEffect(() => {
    if (selected) ref.current?.scrollIntoView?.({ block: "nearest" });
  }, [selected]);

  function onKeyDown(e: KeyboardEvent) {
    if (e.target !== e.currentTarget) return;
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onOpen(row);
    }
  }

  return (
    <div
      ref={ref}
      className={`entity-card${selected ? " is-selected" : ""}`}
      style={entityChipStyle(entityType)}
      role="button"
      tabIndex={0}
      aria-label={label}
      aria-current={selected ? "true" : undefined}
      onClick={() => onOpen(row)}
      onKeyDown={onKeyDown}
    >
      <div className="entity-card-media">
        {url ? (
          <img src={url} alt="" loading="lazy" draggable={false} />
        ) : (
          <i className={`${config.icon} entity-card-placeholder`} aria-hidden="true" />
        )}
        {/* In the picture's corner; its clicks are its own, they must not also open the card. */}
        <span className="entity-card-select" onClick={stopHere} onKeyDown={stopHere}>
          <Checkbox checked={checked} aria-label={t("list.selectRow")} onChange={() => onToggleChecked(row)} />
        </span>
      </div>
      {(subtitle || image) && (
        <div className="entity-card-typeline" onClick={stopHere} onKeyDown={stopHere}>
          <span className="entity-card-subtitle">{subtitle}</span>
          {image && (
            <Button
              icon="bi bi-zoom-in"
              text
              rounded
              size="small"
              className="entity-card-zoom"
              aria-label={t("list.viewImage")}
              tooltip={t("list.viewImage")}
              onClick={() => setZoomed(true)}
            />
          )}
        </div>
      )}
      {zoomed && image && <ImageDialog documentId={image.documentId} title={label} onHide={() => setZoomed(false)} />}
      <footer className="entity-card-footer" onClick={stopHere} onKeyDown={stopHere}>
        <ValidationStatusCell
          entityType={entityType}
          collectionPath={config.collectionPath}
          row={row as ValidationStatusCellProps["row"]}
        />
        <span
          className="entity-card-chip entity-nav-chip"
          style={entityChipStyle(entityType)}
          role="button"
          tabIndex={0}
          onClick={() => onOpen(row)}
          onKeyDown={(e) => {
            if (e.key === "Enter" || e.key === " ") {
              e.preventDefault();
              e.stopPropagation();
              onOpen(row);
            }
          }}
        >
          <i className={config.icon} aria-hidden="true" />
          <span className="entity-nav-chip-label">{identifierColumn ? identifierColumn.render(row) : label}</span>
        </span>
        <span className="entity-card-actions">{actions}</span>
      </footer>
    </div>
  );
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
  // A row's actions bar and the dialogs it opens (useRowActions) — the table's own.
  renderRowActions: (row: RowRecord, compact?: boolean) => ReactNode;
  onNavigate?: (entityType: string, id: string | number) => void;
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  overviewEntityId?: string | number;
  selectedRows: RowRecord[];
  onSelectionChange: (rows: RowRecord[]) => void;
}

/**
 * The list as a grid of cards, for a type whose config supplies `list.card`. The same rows and the
 * same server-side paging as the table: only the presentation differs. A card opens its entity in
 * the overview like the table's identifier chip does, and the one open there is marked selected.
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
  selectedRows,
  onSelectionChange,
}: EntityCardGridProps) {
  const isChecked = (row: RowRecord) => selectedRows.some((r) => String(r.id) === String(row.id));
  function toggleChecked(row: RowRecord) {
    onSelectionChange(isChecked(row) ? selectedRows.filter((r) => String(r.id) !== String(row.id)) : [...selectedRows, row]);
  }

  function open(row: RowRecord) {
    if (row.id == null) return;
    // The fiche's previous/next arrows walk THIS list from this row's position (panels/list/listContext.ts).
    const at = rows.findIndex((r) => String(r.id) === String(row.id));
    if (at >= 0) rememberListContext(entityType, row.id, { params, index: offset + at });
    if (onOpenOverview) {
      onOpenOverview(entityType, row.id, {
        label: entityRowLabel(row as Parameters<typeof entityRowLabel>[0]),
        validationStatus: (row as { validationStatus?: string | null }).validationStatus,
      });
    } else onNavigate?.(entityType, row.id);
  }

  return (
    <div className="entity-card-view">
      <div className="entity-card-scroll">
        {isLoading ? (
          <div className="entity-card-grid">
            {Array.from({ length: SKELETON_CARD_COUNT }, (_, i) => (
              <Skeleton key={i} height="12rem" borderRadius="8px" />
            ))}
          </div>
        ) : rows.length === 0 ? (
          <div className="entity-card-empty">{t("list.treeEmpty")}</div>
        ) : (
          <div className="entity-card-grid">
            {rows.map((row) => (
              <EntityCard
                key={String(row.id)}
                entityType={entityType}
                config={config}
                row={row}
                selected={overviewEntityId != null && String(row.id) === String(overviewEntityId)}
                checked={isChecked(row)}
                onToggleChecked={toggleChecked}
                onOpen={open}
                actions={renderRowActions(row, true)}
              />
            ))}
          </div>
        )}
      </div>
      <Paginator
        first={offset}
        rows={limit}
        totalRecords={totalCount}
        rowsPerPageOptions={PAGE_SIZE_OPTIONS}
        onPageChange={(e: PaginatorPageChangeEvent) => onPage(e.first, e.rows)}
      />
    </div>
  );
}
