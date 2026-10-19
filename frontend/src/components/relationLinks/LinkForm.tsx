import { useState, type ReactNode } from "react";
import { messageForError } from "../../api/errors";
import { getEntityType } from "../../entities/registry";
import { t } from "../../i18n";
import { CreateFormField, CreateFormShell } from "../CreateFormShell";
import { searchProjectUnits, type UnitOption } from "./api";
import { FitToViewport } from "./FitToViewport";
import { InlinePicker, type PickerOption } from "./InlinePicker";

// The icon of the relation graph, on every form that adds to it.
export const GRAPH_ICON = "bi bi-diagram-3";

export interface LinkFormProps {
  title: string;
  projectId?: string | number | null;
  organizationId?: number;
  // The unit being edited, so a search never offers it back.
  excludeUnitId?: string;
  onCancel: () => void;
  // Resolves once the link is saved; a rejection is shown in the form.
  onSubmit: (unit: UnitOption) => Promise<void>;
}

// The form that adds a hierarchy link: the unit to link, picked among the project's or created on the
// spot. (A stratigraphic link has a type and an uncertainty to set: that is StratigraphyEditor.)
// Mounted fresh for each use, so it starts empty.
export function LinkForm({ title, projectId, organizationId, excludeUnitId, onCancel, onSubmit }: LinkFormProps) {
  const [unit, setUnit] = useState<PickerOption | null>(null);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadUnits = async (query: string): Promise<PickerOption[]> => {
    if (projectId == null) return [];
    const found = await searchProjectUnits(projectId, query);
    return found.filter((option) => option.id !== excludeUnitId);
  };

  const link = async (picked: UnitOption) => {
    setBusy(true);
    setError(null);
    try {
      await onSubmit(picked);
    } catch (e) {
      setError(messageForError(e, t("recordingUnit.graph.failed")));
    }
    setBusy(false);
  };

  const createUnitForm = getEntityType("recordingUnit")?.list.createForm;
  if (creating && createUnitForm) {
    // The unit to link doesn't exist yet: the recording unit's own create form, then it is linked at once.
    return (
      <FitToViewport>
        {createUnitForm({
          organizationId,
          scope: projectId != null ? { entityType: "project", id: projectId } : undefined,
          onCreated: (id) => void link({ id: String(id), label: "" }),
          onCancel: () => setCreating(false),
        })}
      </FitToViewport>
    );
  }

  let createLink: ReactNode;
  if (createUnitForm && projectId != null) {
    createLink = (
      <button type="button" className="sia-inline-picker-item" onMouseDown={(e) => e.preventDefault()} onClick={() => setCreating(true)}>
        <i className="bi bi-plus-square" aria-hidden="true" /> {t("recordingUnit.graph.dialog.createUnit")}
      </button>
    );
  }

  return (
    <FitToViewport>
      <CreateFormShell
        entityType="recordingUnit"
        icon={GRAPH_ICON}
        title={title}
        canSubmit={!busy && unit != null}
        submitLabel={t("recordingUnit.graph.dialog.save")}
        pending={busy}
        error={error}
        onSubmit={() => unit && void link({ id: unit.id, label: unit.label })}
        onCancel={onCancel}
      >
        <CreateFormField label={t("recordingUnit.graph.dialog.unit")} required>
          <InlinePicker value={unit} onChange={setUnit} load={loadUnits} placeholder={t("recordingUnit.graph.dialog.unitPlaceholder")} autoFocus footer={createLink} />
        </CreateFormField>
      </CreateFormShell>
    </FitToViewport>
  );
}
