import {useState} from "react";
import {InputText} from "primereact/inputtext";
import type {FieldResource} from "../../../fields/types";
import {t} from "../../../i18n";
import {useNotify} from "../../../notify/NotifyProvider";
import {isRelationField} from "./bulkSelection";

/**
 * A relation that is no field of the catalog (the documents linked to an element), listed among the
 * field relations: the picker hands back its key when it is clicked.
 */
export interface BulkRelationEntry {
  key: string;
  label: string;
  icon: string;
  // Set: listed greyed out; a click on it shows this as a warning.
  disabledReason?: string;
}

export interface BulkFieldPickerProps {
  fields: FieldResource[];
  // A field the selection can't take in bulk (a project-scoped relation across several projects):
  // listed greyed out, and a click on it shows the reason as a warning.
  disabledReason: (field: FieldResource) => string | undefined;
  onPick: (field: FieldResource) => void;
  // The relations besides the catalog's fields, and what clicking one does.
  extraRelations?: BulkRelationEntry[];
  onPickRelation?: (key: string) => void;
}

// One line of the picker, field or not.
interface PickerItem {
  key: string;
  label: string;
  icon: string;
  disabledReason?: string;
  pick: () => void;
}

/**
 * Whether the label holds the typed text, ignoring case and accents.
 */
function matches(label: string, query: string): boolean {
  const normalize = (text: string) => text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
  return normalize(label).includes(normalize(query.trim()));
}

/**
 * The list the bulk bar's « Modifier » opens: every property the selection can be edited on, its
 * plain properties first, then its relations (the links to other entities, documents included),
 * filtered by what is typed. Clicking one opens its bulk editor.
 */
export function BulkFieldPicker({ fields, disabledReason, onPick, extraRelations = [], onPickRelation }: BulkFieldPickerProps) {
  const notify = useNotify();
  const [query, setQuery] = useState("");
  const fieldItem = (field: FieldResource): PickerItem => ({
    key: `field:${field.id}`,
    label: field.label,
    icon: field.icon ?? "bi bi-input-cursor-text",
    disabledReason: disabledReason(field),
    pick: () => onPick(field),
  });
  const relationItem = (entry: BulkRelationEntry): PickerItem => ({
    key: `relation:${entry.key}`,
    label: entry.label,
    icon: entry.icon,
    disabledReason: entry.disabledReason,
    pick: () => onPickRelation?.(entry.key),
  });
  const groups = [
    { key: "properties", label: t("bulk.properties"), items: fields.filter((f) => !isRelationField(f)).map(fieldItem) },
    {
      key: "relations",
      label: t("bulk.relations"),
      items: [...fields.filter(isRelationField).map(fieldItem), ...extraRelations.map(relationItem)],
    },
  ]
    .map((group) => ({ ...group, items: group.items.filter((item) => matches(item.label, query)) }))
    .filter((group) => group.items.length > 0);

  return (
    <div className="bulk-field-picker">
      <span className="p-input-icon-left bulk-field-picker-search">
        <i className="bi bi-search" aria-hidden="true" />
        <InputText
          value={query}
          autoFocus
          placeholder={t("bulk.searchField")}
          aria-label={t("bulk.searchField")}
          onChange={(e) => setQuery(e.target.value)}
        />
      </span>
      {groups.length === 0 && <div className="bulk-field-picker-empty">{t("bulk.noField")}</div>}
      {groups.map((group) => (
        <div
          key={group.key}
          className={`bulk-field-picker-group bulk-field-picker-group-${group.key}`}
          role="group"
          aria-label={group.label}
        >
          <div className="bulk-field-picker-group-label">{group.label}</div>
          {group.items.map((item) => (
            // A greyed line stays clickable: the click says why it can't be edited (a disabled button
            // would swallow it, and its tooltip doesn't show everywhere).
            <button
              key={item.key}
              type="button"
              className={`bulk-field-picker-item${item.disabledReason ? " bulk-field-picker-item-blocked" : ""}`}
              aria-disabled={item.disabledReason != null}
              title={item.disabledReason ?? item.label}
              onClick={() => (item.disabledReason ? notify.warn(item.disabledReason) : item.pick())}
            >
              <i className={item.icon} aria-hidden="true" />
              <span className="bulk-field-picker-item-label">{item.label}</span>
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
