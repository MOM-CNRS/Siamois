import {type ReactNode, useState} from "react";
import {Dropdown} from "primereact/dropdown";
import type {FilterOperator} from "../../entities/shared/types";
import {t} from "../../i18n";
import {type OperatorProfile, operatorLabel, takesValue} from "./filterOperators";

export interface FilterOperatorEditorProps {
  label: string;
  profile: OperatorProfile;
  operator: FilterOperator;
  onOperatorChange: (operator: FilterOperator) => void;
  onRemove: () => void;
  // The value's widget (ColumnFilter): left out for an operator that takes no value.
  children: ReactNode;
}

/**
 * The editor of a datatable filter chip: « <column>… [operator ▾] ⋯ » on top, the value underneath. The
 * operator menu offers what the column's profile can do (filterOperators.ts); « ⋯ » holds the removal of the
 * filter. An operator without value (empty, not empty) hides the value widget: choosing it applies the filter.
 */
export function FilterOperatorEditor({ label, profile, operator, onOperatorChange, onRemove, children }: FilterOperatorEditorProps) {
  const [actionsOpen, setActionsOpen] = useState(false);
  const choices = profile.operators.map((value) => ({ value, label: operatorLabel(value, profile) }));
  return (
    <div className="filter-editor">
      <div className="filter-editor-header">
        <span className="filter-editor-label" title={label}>{label}</span>
        {profile.operators.length > 1 ? (
          <Dropdown
            className="filter-operator"
            panelClassName="filter-operator-panel"
            value={operator}
            options={choices}
            aria-label={t("filter.operator", { label })}
            onChange={(e) => onOperatorChange(e.value as FilterOperator)}
          />
        ) : (
          <span className="filter-operator filter-operator-static">{operatorLabel(operator, profile)}</span>
        )}
        <button
          type="button"
          className="filter-editor-actions-toggle"
          aria-label={t("filter.actions", { label })}
          aria-expanded={actionsOpen}
          onClick={() => setActionsOpen((open) => !open)}
        >
          <i className="bi bi-three-dots" />
        </button>
      </div>
      {actionsOpen && (
        <div className="filter-editor-actions">
          <button type="button" className="filter-picker-item" onClick={onRemove}>
            <i className="bi bi-trash" /> {t("filter.delete")}
          </button>
        </div>
      )}
      {takesValue(operator) && <div className="filter-editor-value">{children}</div>}
    </div>
  );
}
