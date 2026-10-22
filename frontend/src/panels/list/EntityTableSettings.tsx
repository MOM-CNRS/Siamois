import { useRef, type RefObject, type SyntheticEvent } from "react";
import { Button } from "primereact/button";
import { OverlayPanel } from "primereact/overlaypanel";
import {
  ColumnToggler,
  type ColumnTogglerOption,
} from "../../components/table/ColumnToggler";
import { VisibilityChooser } from "../../components/table/VisibilityChooser";
import { t } from "../../i18n";
import type { ListViewMode, ListViewOption } from "./listViews";
import type { OpenMode } from "./listPreferences";

export interface ActionBarLayout {
  order: string[];
  inline: string[];
}

export interface EntityTableSettingsProps {
  // The ways to show this list (list, tree, later a map, a timeline…) and the one in use; null when the
  // list has only its table.
  views: {
    options: ListViewOption[];
    value: ListViewMode;
    onChange: (mode: ListViewMode) => void;
  } | null;
  // How a row opens a page: in the side overview or in the main pane (focus); null when the list has no
  // overview to open into.
  openMode: {
    value: OpenMode;
    onChange: (mode: OpenMode) => void;
  } | null;
  // The columns the user can show or hide; null when the list has no column catalog.
  columns: {
    options: ColumnTogglerOption[];
    visible: string[];
    onChange: (visible: string[]) => void;
  } | null;
  // The row actions the user can arrange; null when the list has none.
  actionBar: {
    items: { key: string; label: string; icon: string }[];
    layout: ActionBarLayout;
    onChange: (layout: ActionBarLayout) => void;
  } | null;
}

/**
 * The gear: the table's settings, each in its own overlay anchored on the gear itself (the menu that
 * opened it is gone by then). Renders nothing when there is nothing to configure.
 */
export function EntityTableSettings({
  views,
  openMode,
  columns,
  actionBar,
}: EntityTableSettingsProps) {
  const gearPanelRef = useRef<OverlayPanel>(null);
  const gearButtonRef = useRef<HTMLButtonElement | null>(null);
  const columnTogglerRef = useRef<OverlayPanel>(null);
  const actionBarSettingsRef = useRef<OverlayPanel>(null);

  const hasViews = views != null && views.options.length > 1;
  if (!columns && !actionBar && !hasViews && !openMode) return null;

  // The gear's own panel gives way to the one asked for, anchored on the gear itself.
  function openSettings(ref: RefObject<OverlayPanel>, e: SyntheticEvent) {
    gearPanelRef.current?.hide();
    const anchor = gearButtonRef.current;
    if (anchor) ref.current?.show(e, anchor);
    else ref.current?.toggle(e);
  }

  // A choice among a few modes: one tile each, icon over label, the one in use outlined.
  function tiles<T extends string>(
    title: string,
    options: { mode: T; label: string; icon: string }[],
    value: T,
    onChange: (mode: T) => void,
  ) {
    return (
      <div className="entity-list-panel-view-mode" role="radiogroup" aria-label={title}>
        <span className="entity-list-panel-view-mode-title">{title}</span>
        <div className="entity-list-panel-view-mode-grid">
          {options.map((option) => {
            const active = option.mode === value;
            return (
              <button
                key={option.mode}
                type="button"
                role="radio"
                aria-checked={active}
                className={`entity-list-panel-view-tile${active ? " is-active" : ""}`}
                onClick={() => {
                  if (!active) onChange(option.mode);
                }}
              >
                <i className={option.icon} aria-hidden="true" />
                <span>{option.label}</span>
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  const openModeOptions: { mode: OpenMode; label: string; icon: string }[] = [
    { mode: "overview", label: t("list.openOverview"), icon: "bi bi-layout-sidebar-inset-reverse" },
    { mode: "focus", label: t("list.openFocus"), icon: "bi bi-arrows-fullscreen" },
  ];
  // The columns and the action bar arrange a table: they are offered on the list and the tree, not on cards.
  const tableLayout = views == null || views.value !== "cards";
  const entries = [
    ...(columns && tableLayout
      ? [{ key: "columns", label: t("list.menuColumns"), icon: "bi bi-layout-three-columns", ref: columnTogglerRef }]
      : []),
    ...(actionBar && tableLayout
      ? [{ key: "actionBar", label: t("list.menuActionBar"), icon: "bi bi-three-dots", ref: actionBarSettingsRef }]
      : []),
  ];

  // The action bar settings list every configurable action in the current layout's order; the
  // shown ones are the inline ones. Hidden ones keep their relative order (the "…" menu's).
  const itemsByKey = new Map(
    (actionBar?.items ?? []).map((item) => [item.key, item]),
  );
  const orderedItems = (actionBar?.layout.order ?? [])
    .map((key) => itemsByKey.get(key))
    .filter((item): item is NonNullable<typeof item> => item != null)
    .map((item) => ({ id: item.key, label: item.label, icon: item.icon }));
  function onActionBarChange(inline: string[]) {
    if (!actionBar) return;
    const rest = actionBar.layout.order.filter((k) => !inline.includes(k));
    actionBar.onChange({ order: [...inline, ...rest], inline });
  }

  return (
    <>
      <Button
        // PrimeReact's Button forwards its ref to the <button> itself (its typings say the
        // component instance).
        ref={(button) => {
          gearButtonRef.current = button as unknown as HTMLButtonElement | null;
        }}
        icon="bi bi-gear"
        text
        rounded
        size="large"
        aria-label={t("list.settings")}
        aria-haspopup
        tooltip={t("list.settings")}
        className="entity-list-panel-gear-button"
        onClick={(e) => gearPanelRef.current?.toggle(e)}
      />
      <OverlayPanel ref={gearPanelRef} className="entity-list-panel-settings-overlay entity-list-panel-gear-panel">
        {hasViews &&
          tiles(t("list.menuView"), views.options, views.value, (mode) => {
            gearPanelRef.current?.hide();
            views.onChange(mode);
          })}
        {openMode &&
          tiles(t("list.menuOpening"), openModeOptions, openMode.value, (mode) => {
            gearPanelRef.current?.hide();
            openMode.onChange(mode);
          })}
        {entries.length > 0 && (
          <div className="entity-list-panel-gear-menu" role="menu">
            {entries.map((entry) => (
              <a
                key={entry.key}
                className="p-menuitem-link"
                role="menuitem"
                tabIndex={0}
                onClick={(e) => openSettings(entry.ref, e)}
              >
                <span className={`p-menuitem-icon ${entry.icon}`} />
                <span className="p-menuitem-text">{entry.label}</span>
              </a>
            ))}
          </div>
        )}
      </OverlayPanel>
      {columns && (
        <OverlayPanel
          ref={columnTogglerRef}
          className="entity-list-panel-settings-overlay"
        >
          <ColumnToggler
            options={columns.options}
            value={columns.visible}
            onChange={columns.onChange}
          />
        </OverlayPanel>
      )}
      {actionBar && (
        <OverlayPanel
          ref={actionBarSettingsRef}
          className="entity-list-panel-settings-overlay"
        >
          <VisibilityChooser
            className="entity-list-panel-action-bar-settings"
            items={orderedItems}
            visible={actionBar.layout.inline.filter((k) =>
              actionBar.layout.order.includes(k),
            )}
            onChange={onActionBarChange}
            visibleTitle={t("list.inRow")}
            hiddenTitle={t("list.inMenu")}
            locked={[
              {
                id: "bookmark",
                label: t("list.bookmark"),
                icon: "bi bi-bookmark",
              },
            ]}
          />
        </OverlayPanel>
      )}
    </>
  );
}
