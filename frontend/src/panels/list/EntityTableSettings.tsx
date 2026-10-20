import { useRef, type RefObject, type SyntheticEvent } from "react";
import { Button } from "primereact/button";
import { Menu } from "primereact/menu";
import { OverlayPanel } from "primereact/overlaypanel";
import {
  ColumnToggler,
  type ColumnTogglerOption,
} from "../../components/table/ColumnToggler";
import { VisibilityChooser } from "../../components/table/VisibilityChooser";
import { t } from "../../i18n";
import type { ListViewMode, ListViewOption } from "./listViews";

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
  columns,
  actionBar,
}: EntityTableSettingsProps) {
  const gearMenuRef = useRef<Menu>(null);
  const gearButtonRef = useRef<HTMLButtonElement | null>(null);
  const columnTogglerRef = useRef<OverlayPanel>(null);
  const actionBarSettingsRef = useRef<OverlayPanel>(null);

  const hasViews = views != null && views.options.length > 1;
  if (!columns && !actionBar && !hasViews) return null;

  function openSettings(
    ref: RefObject<OverlayPanel>,
    e: { originalEvent: SyntheticEvent },
  ) {
    const anchor = gearButtonRef.current;
    if (anchor) ref.current?.show(e.originalEvent, anchor);
    else ref.current?.toggle(e.originalEvent);
  }
  // The display modes are a choice among several (not an on/off switch): one entry each under their own
  // heading, the one in use ticked.
  const viewItems = hasViews
    ? [
        {
          label: t("list.menuView"),
          items: views.options.map((option) => {
            const active = option.mode === views.value;
            return {
              label: option.label,
              icon: option.icon,
              className: active ? "entity-list-panel-view-active" : undefined,
              command: () => views.onChange(option.mode),
              template: (
                item: { label?: string; icon?: string },
                options: {
                  onClick: (e: SyntheticEvent) => void;
                  className: string;
                  iconClassName: string;
                  labelClassName: string;
                },
              ) => (
                <a
                  className={options.className}
                  role="menuitemradio"
                  aria-checked={active}
                  tabIndex={0}
                  onClick={options.onClick}
                >
                  <span
                    className={`${options.iconClassName} ${item.icon ?? ""}`}
                  />
                  <span className={options.labelClassName}>{item.label}</span>
                  {active && (
                    <i
                      className="bi bi-check2 entity-list-panel-view-check"
                      aria-hidden="true"
                    />
                  )}
                </a>
              ),
            };
          }),
        },
        ...(columns || actionBar ? [{ separator: true }] : []),
      ]
    : [];
  const menuItems = [
    ...viewItems,
    ...(columns
      ? [
          {
            label: t("list.menuColumns"),
            icon: "bi bi-layout-three-columns",
            command: (e: { originalEvent: SyntheticEvent }) =>
              openSettings(columnTogglerRef, e),
          },
        ]
      : []),
    ...(actionBar
      ? [
          {
            label: t("list.menuActionBar"),
            icon: "bi bi-three-dots",
            command: (e: { originalEvent: SyntheticEvent }) =>
              openSettings(actionBarSettingsRef, e),
          },
        ]
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
        onClick={(e) => gearMenuRef.current?.toggle(e)}
      />
      <Menu
        ref={gearMenuRef}
        popup
        model={menuItems}
        className="entity-list-panel-gear-menu"
      />
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
