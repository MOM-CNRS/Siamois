import type { MenuItem } from "primereact/menuitem";
import { t } from "../i18n";
import type { EditableTab } from "./ficheTabsModel";

/**
 * The entries of the « Ajouter » menu: the built-in tabs that were removed (to put back), then a new list tab.
 * `canAddList` is false for a fiche that lists nothing, which has no list to add.
 */
export function addTabItems(
  restorable: EditableTab[],
  canAddList: boolean,
  onRestore: (key: string) => void,
  onAddList: () => void,
): MenuItem[] {
  return [
    ...restorable.map((row) => ({
      label: row.label.trim() || row.defaultLabel,
      icon: "bi bi-arrow-counterclockwise",
      command: () => onRestore(row.key),
    })),
    ...(restorable.length > 0 && canAddList ? [{ separator: true }] : []),
    ...(canAddList ? [{ label: t("tabs.addList"), icon: "bi bi-funnel", command: onAddList }] : []),
  ];
}
