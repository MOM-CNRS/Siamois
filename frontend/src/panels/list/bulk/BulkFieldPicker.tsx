import {useState} from "react";
import {InputText} from "primereact/inputtext";
import type {FieldResource} from "../../../fields/types";
import {t} from "../../../i18n";
import {isRelationField} from "./bulkSelection";

export interface BulkFieldPickerProps {
  fields: FieldResource[];
  // A field the selection can't take in bulk (a project-scoped relation across several projects):
  // listed, but greyed out with the reason as its tooltip.
  disabledReason: (field: FieldResource) => string | undefined;
  onPick: (field: FieldResource) => void;
}

/**
 * Whether the field's label holds the typed text, ignoring case and accents.
 */
function matches(field: FieldResource, query: string): boolean {
  const normalize = (text: string) => text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();
  return normalize(field.label).includes(normalize(query.trim()));
}

/**
 * The list the bulk bar's « Modifier » opens: every property the selection can be edited on, its
 * plain properties first, then its relations (the links to other entities), filtered by what is
 * typed. Clicking one opens its bulk editor.
 */
export function BulkFieldPicker({ fields, disabledReason, onPick }: BulkFieldPickerProps) {
  const [query, setQuery] = useState("");
  const shown = fields.filter((field) => matches(field, query));
  const groups = [
    { key: "properties", label: t("bulk.properties"), fields: shown.filter((field) => !isRelationField(field)) },
    { key: "relations", label: t("bulk.relations"), fields: shown.filter(isRelationField) },
  ].filter((group) => group.fields.length > 0);

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
        <div key={group.key} className="bulk-field-picker-group" role="group" aria-label={group.label}>
          <div className="bulk-field-picker-group-label">{group.label}</div>
          {group.fields.map((field) => {
            const reason = disabledReason(field);
            return (
              <button
                key={field.id}
                type="button"
                className="bulk-field-picker-item"
                disabled={reason != null}
                title={reason ?? field.label}
                onClick={() => onPick(field)}
              >
                <i className={field.icon ?? "bi bi-input-cursor-text"} aria-hidden="true" />
                <span className="bulk-field-picker-item-label">{field.label}</span>
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}
