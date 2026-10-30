import {useState} from "react";
import {useMutation, useQuery} from "@tanstack/react-query";
import {Button} from "primereact/button";
import {messageForError} from "../api/errors";
import {AnchoredFormOverlay} from "../components/layout/AnchoredFormOverlay";
import {CreateFormField, CreateFormShell} from "../components/create/CreateFormShell";
import {FitToViewport} from "../components/relationLinks/FitToViewport";
import {InlinePicker, type PickerOption} from "../components/relationLinks/InlinePicker";
import {t} from "../i18n";
import {
  addMeasurement,
  getMeasurementUnits,
  type MeasurementRequest,
  type OnTheFlyFields,
  searchExistingMeasurements,
  searchMeasurementConcepts,
  type UnitOption,
} from "./onTheFly";

// The measurement field's own icon (CustomFieldMeasurement#getIcon). No entity key on the form's shell: it then
// takes the classic Siamois colour, not the colour of the record's entity.
const MEASURE_ICON = "bi bi-flask";
const NO_ENTITY = "";

// A picker option that is not a concept yet: the name the user typed, created with the measure.
const NEW_PREFIX = "new:";
const newOption = (label: string): PickerOption => ({ id: `${NEW_PREFIX}${label}`, label });
const isNew = (option: PickerOption | null): boolean => option?.id.startsWith(NEW_PREFIX) === true;
const conceptIdOf = (option: PickerOption | null): number | null => (option && !isNew(option) ? Number(option.id) : null);
const newLabelOf = (option: PickerOption | null): string | null => (option && isNew(option) ? option.label : null);

/** The last line of a concept picker's list: « Créer "xyz" » for what is typed, when no concept bears that name. */
function createConceptLine(typed: { query: string; pick: (option: PickerOption) => void }) {
  const name = typed.query.trim();
  if (!name) return null;
  return (
    <button type="button" className="sia-inline-picker-item" onMouseDown={(e) => e.preventDefault()} onClick={() => typed.pick(newOption(name))}>
      <i className="bi bi-plus-square" aria-hidden="true" /> {t("onTheFly.createConcept", { name })}
    </button>
  );
}

export interface AddMeasureOverlayProps {
  segment: string;
  entityId: string | number;
  /** The panel the measure is added to. */
  panel: string;
  /** The button the overlay opens next to; null = closed. */
  anchor: HTMLElement | null;
  onAdded: (fields: OnTheFlyFields) => void;
  onHide: () => void;
}

/**
 * « Ajouter une mesure »: search the measures already used on the table's other records of the project, or
 * define a new one (what is measured, its nature, its units). Either way the measure becomes a field of this one
 * record's fiche.
 */
export function AddMeasureOverlay({ anchor, onHide, ...form }: AddMeasureOverlayProps) {
  return (
    <AnchoredFormOverlay anchor={anchor} ariaLabel={t("onTheFly.title")} onHide={onHide}>
      {(close) => (
        <MeasureForm
          {...form}
          onCancel={close}
          onAdded={(fields) => {
            form.onAdded(fields);
            close();
          }}
        />
      )}
    </AnchoredFormOverlay>
  );
}

type Props = Omit<AddMeasureOverlayProps, "anchor" | "onHide"> & { onCancel: () => void };

function MeasureForm(props: Props) {
  const [creating, setCreating] = useState(false);
  return creating ? <NewMeasureForm {...props} onCancel={() => setCreating(false)} /> : <ExistingMeasureForm {...props} onNew={() => setCreating(true)} />;
}

function useAdd(props: Props) {
  return useMutation({
    mutationFn: (body: MeasurementRequest) => addMeasurement(props.segment, props.entityId, body),
    onSuccess: props.onAdded,
  });
}

function ExistingMeasureForm(props: Props & { onNew: () => void }) {
  const [picked, setPicked] = useState<PickerOption | null>(null);
  const add = useAdd(props);

  const load = async (query: string): Promise<PickerOption[]> =>
    (await searchExistingMeasurements(props.segment, props.entityId, query)).map((m) => ({
      id: String(m.fieldId),
      label: m.unit ? `${m.label} (${m.unit})` : m.label,
    }));

  return (
    <FitToViewport>
      <CreateFormShell
        entityType={NO_ENTITY}
        icon={MEASURE_ICON}
        title={t("onTheFly.title")}
        canSubmit={picked != null && !add.isPending}
        submitLabel={t("onTheFly.confirm")}
        pending={add.isPending}
        error={add.error ? messageForError(add.error, t("onTheFly.failed")) : null}
        onSubmit={() => picked && add.mutate({ panel: props.panel, copyFieldId: Number(picked.id) })}
        onCancel={props.onCancel}
      >
        <CreateFormField label={t("onTheFly.existing")}>
          <InlinePicker
            value={picked}
            onChange={setPicked}
            load={load}
            placeholder={t("onTheFly.existingPlaceholder")}
            autoFocus
            footer={
              <button type="button" className="sia-inline-picker-item" onMouseDown={(e) => e.preventDefault()} onClick={props.onNew}>
                <i className="bi bi-plus-square" aria-hidden="true" /> {t("onTheFly.create")}
              </button>
            }
          />
        </CreateFormField>
      </CreateFormShell>
    </FitToViewport>
  );
}

function NewMeasureForm(props: Props) {
  const [type, setType] = useState<PickerOption | null>(null);
  const [nature, setNature] = useState<PickerOption | null>(null);
  const [unit, setUnit] = useState<PickerOption | null>(null);
  const [others, setOthers] = useState<number[]>([]);
  const add = useAdd(props);

  const unitsQuery = useQuery({
    queryKey: ["measurement-units", props.segment, props.entityId],
    queryFn: () => getMeasurementUnits(props.segment, props.entityId),
    staleTime: Infinity,
  });
  const units = unitsQuery.data ?? [];
  const unitOf = (id: number): UnitOption | undefined => units.find((u) => u.id === id);
  const defaultUnit = unit ? unitOf(Number(unit.id)) : undefined;
  // The units a user may pick measure the same thing as the default one.
  const sameDimension = units.filter((u) => defaultUnit?.dimension != null && u.dimension === defaultUnit.dimension && u.id !== defaultUnit.id);

  const loadConcepts = (kind: "TYPE" | "NATURE") => async (q: string): Promise<PickerOption[]> =>
    (await searchMeasurementConcepts(props.segment, props.entityId, kind, q)).map((c) => ({ id: String(c.id), label: c.label }));
  const loadUnits = async (q: string): Promise<PickerOption[]> =>
    units.filter((u) => `${u.label} ${u.symbol}`.toLowerCase().includes(q.toLowerCase())).map((u) => ({ id: String(u.id), label: `${u.label} (${u.symbol})` }));

  return (
    <FitToViewport>
      <CreateFormShell
        entityType={NO_ENTITY}
        icon={MEASURE_ICON}
        title={t("onTheFly.newTitle")}
        canSubmit={type != null && unit != null && !add.isPending}
        submitLabel={t("onTheFly.confirm")}
        pending={add.isPending}
        error={add.error ? messageForError(add.error, t("onTheFly.failed")) : null}
        onSubmit={() =>
          type &&
          unit &&
          add.mutate({
            panel: props.panel,
            typeConceptId: conceptIdOf(type),
            newTypeLabel: newLabelOf(type),
            natureConceptId: conceptIdOf(nature),
            newNatureLabel: newLabelOf(nature),
            defaultUnitId: Number(unit.id),
            allowedUnitIds: others.filter((id) => sameDimension.some((u) => u.id === id)),
          })
        }
        onCancel={props.onCancel}
      >
        <CreateFormField label={t("onTheFly.type")} required>
          <InlinePicker value={type} onChange={setType} load={loadConcepts("TYPE")} placeholder={t("onTheFly.typePlaceholder")} autoFocus footer={createConceptLine} />
        </CreateFormField>
        <CreateFormField label={t("onTheFly.nature")}>
          <InlinePicker value={nature} onChange={setNature} load={loadConcepts("NATURE")} placeholder={t("onTheFly.naturePlaceholder")} footer={createConceptLine} />
        </CreateFormField>
        <CreateFormField label={t("onTheFly.unit")} required>
          <InlinePicker
            value={unit}
            onChange={(next) => {
              setUnit(next);
              setOthers([]);
            }}
            load={loadUnits}
            placeholder={t("onTheFly.unitPlaceholder")}
          />
        </CreateFormField>
        {sameDimension.length > 0 && (
          <CreateFormField label={t("onTheFly.otherUnits")}>
            <span className="sia-measure-units">
              {sameDimension.map((u) => {
                const on = others.includes(u.id);
                return (
                  <Button
                    key={u.id}
                    type="button"
                    size="small"
                    outlined={!on}
                    label={u.symbol}
                    title={u.label}
                    aria-pressed={on}
                    onClick={() => setOthers(on ? others.filter((id) => id !== u.id) : [...others, u.id])}
                  />
                );
              })}
            </span>
          </CreateFormField>
        )}
      </CreateFormShell>
    </FitToViewport>
  );
}
