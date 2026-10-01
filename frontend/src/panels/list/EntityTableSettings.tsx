import { useRef, type RefObject, type SyntheticEvent } from "react";
import { Button } from "primereact/button";
import { Menu } from "primereact/menu";
import { OverlayPanel } from "primereact/overlaypanel";
import { ColumnToggler, type ColumnTogglerOption } from "../../components/table/ColumnToggler";
import { VisibilityChooser } from "../../components/table/VisibilityChooser";
import { t } from "../../i18n";

export interface ActionBarLayout {
  order: string[];
  inline: string[];
}

export interface EntityTableSettingsProps {
  // The columns the user can show or hide; null when the list has no column catalog.
  columns: { options: ColumnTogglerOption[]; visible: string[]; onChange: (visible: string[]) => void } | null;
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
export function EntityTableSettings({ columns, actionBar }: EntityTableSettingsProps) {
  const gearMenuRef = useRef<Menu>(null);
  const gearButtonRef = useRef<HTMLButtonElement | null>(null);
  const columnTogglerRef = useRef<OverlayPanel>(null);
  const actionBarSettingsRef = useRef<OverlayPanel>(null);

  if (!columns && !actionBar) return null;

  function openSettings(ref: RefObject<OverlayPanel>, e: { originalEvent: SyntheticEvent }) {
    const anchor = gearButtonRef.current;
    if (anchor) ref.current?.show(e.originalEvent, anchor);
    else ref.current?.toggle(e.originalEvent);
  }
  const menuItems = [
    ...(columns
      ? [{ label: t("list.menuColumns"), icon: "bi bi-layout-three-columns", command: (e: { originalEvent: SyntheticEvent }) => openSettings(columnTogglerRef, e) }]
      : []),
    ...(actionBar
      ? [{ label: t("list.menuActionBar"), icon: "bi bi-three-dots", command: (e: { originalEvent: SyntheticEvent }) => openSettings(actionBarSettingsRef, e) }]
      : []),
  ];

  // The action bar settings list every configurable action in the current layout's order; the
  // shown ones are the inline ones. Hidden ones keep their relative order (the "…" menu's).
  const itemsByKey = new Map((actionBar?.items ?? []).map((item) => [item.key, item]));
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
      <Menu ref={gearMenuRef} popup model={menuItems} className="entity-list-panel-gear-menu" />
      {columns && (
        <OverlayPanel ref={columnTogglerRef} className="entity-list-panel-settings-overlay">
          <ColumnToggler options={columns.options} value={columns.visible} onChange={columns.onChange} />
        </OverlayPanel>
      )}
      {actionBar && (
        <OverlayPanel ref={actionBarSettingsRef} className="entity-list-panel-settings-overlay">
          <VisibilityChooser
            className="entity-list-panel-action-bar-settings"
            items={orderedItems}
            visible={actionBar.layout.inline.filter((k) => actionBar.layout.order.includes(k))}
            onChange={onActionBarChange}
            visibleTitle={t("list.inRow")}
            hiddenTitle={t("list.inMenu")}
            locked={[{ id: "bookmark", label: t("list.bookmark"), icon: "bi bi-bookmark" }]}
          />
        </OverlayPanel>
      )}
    </>
  );
}
