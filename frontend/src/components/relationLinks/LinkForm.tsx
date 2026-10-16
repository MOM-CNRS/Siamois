import { useEffect, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { Checkbox } from "primereact/checkbox";
import { SelectButton } from "primereact/selectbutton";
import { queryKeys } from "../../api/queryKeys";
import { messageForError } from "../../api/errors";
import { getEntityType } from "../../entities/registry";
import { fetchConceptOptions } from "../../fields/optionSources";
import { t } from "../../i18n";
import { CreateFormField, CreateFormShell } from "../CreateFormShell";
import { searchProjectUnits, type StratigraphyZone, type UnitOption } from "./api";
import { FitToViewport } from "./FitToViewport";
import { InlinePicker, type PickerOption } from "./InlinePicker";

// The field code of the stratigraphic relationship types' vocabulary.
const STRATIGRAPHY_FIELD_CODE = "SIARU.STRATI";
// The icon of the relation graph, on every form that adds to it.
export const GRAPH_ICON = "bi bi-diagram-3";

export interface LinkFormValue {
  // The unit to link — only when adding.
  unit?: UnitOption;
  // Where the unit stands relative to this one — only when adding with a choice of positions.
  position?: StratigraphyZone;
  typeConceptId?: string | null;
  uncertain: boolean;
}

export interface LinkFormProps {
  title: string;
  // Adding picks the unit; editing keeps it.
  mode: "add" | "edit";
  // Whether the link has a type and an uncertainty to set — a stratigraphic one does, a hierarchy link doesn't.
  qualified: boolean;
  // The positions to choose from when adding a stratigraphic link whose position isn't known yet.
  positions?: StratigraphyZone[];
  projectId?: string | number | null;
  organizationId?: number;
  initial?: { typeConceptId?: string | null; uncertain?: boolean };
  // The unit being edited, so a search never offers it back.
  excludeUnitId?: string;
  onCancel: () => void;
  // Resolves once the link is saved; a rejection is shown in the form.
  onSubmit: (value: LinkFormValue) => Promise<void>;
}

const POSITION_LABELS: Record<StratigraphyZone, () => string> = {
  posterior: () => t("recordingUnit.graph.posterior"),
  synchronous: () => t("recordingUnit.graph.synchronous"),
  anterior: () => t("recordingUnit.graph.anterior"),
};

// The form that adds or edits one link: the unit to link (picked among the project's, or created on
// the spot) and, for a stratigraphic link, its position, type and whether it is uncertain. Mounted
// fresh for each use, so it starts empty.
export function LinkForm({ title, mode, qualified, positions, projectId, organizationId, initial, excludeUnitId, onCancel, onSubmit }: LinkFormProps) {
  const [unit, setUnit] = useState<PickerOption | null>(null);
  const [position, setPosition] = useState<StratigraphyZone | undefined>(positions?.[0]);
  const [typeOption, setTypeOption] = useState<PickerOption | null>(null);
  const [uncertain, setUncertain] = useState(!!initial?.uncertain);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const types = useQuery({
    queryKey: queryKeys.stratigraphyTypes(organizationId),
    queryFn: () => fetchConceptOptions(organizationId!, STRATIGRAPHY_FIELD_CODE),
    enabled: qualified && organizationId != null,
  });

  // The type being edited is known by id; its label comes with the vocabulary.
  useEffect(() => {
    const id = initial?.typeConceptId;
    if (!id) return;
    const known = types.data?.find((option) => option.id === id);
    setTypeOption(known ? { id: known.id, label: known.label } : { id, label: id });
    // Set once the vocabulary is there; later renders must not undo what the user picked.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [types.data]);

  const canSave = !busy && (mode === "edit" || (unit != null && (positions == null || position != null)));

  const loadUnits = async (query: string): Promise<PickerOption[]> => {
    if (projectId == null) return [];
    const found = await searchProjectUnits(projectId, query);
    return found.filter((option) => option.id !== excludeUnitId);
  };
  const loadTypes = async (query: string): Promise<PickerOption[]> => {
    const q = query.trim().toLowerCase();
    return (types.data ?? []).filter((option) => !q || option.label.toLowerCase().includes(q)).map((option) => ({ id: option.id, label: option.label }));
  };

  const link = async (picked: UnitOption | undefined) => {
    setBusy(true);
    setError(null);
    try {
      await onSubmit({ unit: picked, position, typeConceptId: typeOption?.id ?? null, uncertain });
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
        canSubmit={canSave}
        submitLabel={t("recordingUnit.graph.dialog.save")}
        pending={busy}
        error={error}
        onSubmit={() => void link(unit ? { id: unit.id, label: unit.label } : undefined)}
        onCancel={onCancel}
      >
        {mode === "add" && positions && (
          <CreateFormField label={t("recordingUnit.graph.dialog.position")} required>
            <SelectButton
              value={position}
              options={positions.map((value) => ({ value, label: POSITION_LABELS[value]() }))}
              onChange={(e) => e.value && setPosition(e.value)}
              allowEmpty={false}
            />
          </CreateFormField>
        )}
        {mode === "add" && (
          <CreateFormField label={t("recordingUnit.graph.dialog.unit")} required>
            <InlinePicker value={unit} onChange={setUnit} load={loadUnits} placeholder={t("recordingUnit.graph.dialog.unitPlaceholder")} autoFocus footer={createLink} />
          </CreateFormField>
        )}
        {qualified && (
          <>
            <CreateFormField label={t("recordingUnit.graph.dialog.type")}>
              <InlinePicker value={typeOption} onChange={setTypeOption} load={loadTypes} placeholder={t("recordingUnit.graph.dialog.noType")} />
            </CreateFormField>
            <label className="sia-create-form-field sia-create-form-field-inline">
              <Checkbox inputId="graph-uncertain" checked={uncertain} onChange={(e) => setUncertain(!!e.checked)} />
              <span>{t("recordingUnit.graph.dialog.uncertain")}</span>
            </label>
          </>
        )}
      </CreateFormShell>
    </FitToViewport>
  );
}
