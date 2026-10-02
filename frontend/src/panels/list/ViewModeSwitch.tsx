import { SelectButton } from "primereact/selectbutton";
import { t } from "../../i18n";
import type { ListViewMode } from "../listPreferences";

export interface ViewModeSwitchProps {
  value: ListViewMode;
  // The presentations this list offers, besides the table.
  modes: ListViewMode[];
  onChange: (mode: ListViewMode) => void;
}

/** The toolbar's choice between the list's presentations: the table or a grid of cards. */
export function ViewModeSwitch({ value, modes, onChange }: ViewModeSwitchProps) {
  const all = [
    { value: "table" as const, icon: "bi bi-table", title: t("list.viewTable") },
    { value: "cards" as const, icon: "bi bi-grid-3x3-gap", title: t("list.viewCards") },
    { value: "map" as const, icon: "bi bi-geo-alt", title: t("list.viewMap") },
  ];
  const options = all.filter((o) => o.value === "table" || modes.includes(o.value));
  return (
    <SelectButton
      className="entity-list-panel-view-switch"
      aria-label={t("list.viewLabel")}
      value={value}
      options={options}
      optionLabel="title"
      allowEmpty={false}
      itemTemplate={(o: (typeof options)[number]) => <i className={o.icon} title={o.title} aria-label={o.title} />}
      onChange={(e) => e.value && onChange(e.value)}
    />
  );
}
