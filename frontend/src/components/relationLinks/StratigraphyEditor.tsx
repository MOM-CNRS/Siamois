import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { apiFetch } from "../../api/client";
import { messageForError } from "../../api/errors";
import { queryKeys } from "../../api/queryKeys";
import { qualifiedLabel, refChipStyle } from "../../fields/display";
import { t } from "../../i18n";
import { useOpenEntity } from "../../panels/entityNavigation";
import { addStratigraphyLink, removeStratigraphyLink, searchProjectUnits, updateStratigraphyLink, type StratigraphyZone } from "./api";
import { FitToViewport } from "./FitToViewport";
import { InlinePicker, type PickerOption } from "./InlinePicker";
import { canCreateUnit, NewUnitForm, NewUnitItem } from "./NewUnit";
import { QualifierFields, type Qualifier } from "./QualifierFields";
import { useToastOnChange } from "../../notify/NotifyProvider";

interface StratigraphyRef {
  resourceId: string;
  label?: string | null;
  qualifier?: { concept?: { resourceId?: string; label?: string | null } | null; uncertain?: boolean | null } | null;
}

type Form = { kind: "add"; unit: PickerOption | null; qualifier: Qualifier } | { kind: "edit"; ref: StratigraphyRef; qualifier: Qualifier };

const qualifierOf = (ref: StratigraphyRef): Qualifier => {
  const concept = ref.qualifier?.concept;
  return { type: concept?.resourceId ? { id: concept.resourceId, label: concept.label ?? concept.resourceId } : null, uncertain: !!ref.qualifier?.uncertain };
};

export interface StratigraphyEditorProps {
  // The field that holds this side of the stratigraphy: its values are the chips, its list gives the types.
  fieldId: string;
  // Where the units of this field stand relative to the unit being edited.
  position: StratigraphyZone;
  unitId: string | number;
  projectId?: string | null;
  organizationId?: number;
  // The unit's type, which can narrow the field's list of relationship types.
  typeConceptId?: string;
  // After a link changed: whoever shows the relations is stale.
  onChanged: () => void;
  // A chip opened a fiche: the overlay's job is done.
  onClose: () => void;
}

/**
 * The value of one stratigraphic field, edited the way every multi-valued reference field is: its
 * chips — the label opens the unit's fiche, the cross removes the link — plus, as the only addition,
 * a pencil that edits the link's type and uncertainty, and a search to add a unit. Nothing else: no
 * header, no footer, it is the content of a cell's editing overlay, not a form of its own. Each
 * change is written at once, by the unit's stratigraphic-relationships endpoints.
 */
export function StratigraphyEditor({ fieldId, position, unitId, projectId, organizationId, typeConceptId, onChanged, onClose }: StratigraphyEditorProps) {
  const queryClient = useQueryClient();
  const openEntity = useOpenEntity();
  const [form, setForm] = useState<Form | null>(null);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useToastOnChange(error);

  const values = useQuery({
    queryKey: queryKeys.stratigraphyValues(unitId, fieldId),
    queryFn: async () => (await apiFetch<{ data: StratigraphyRef[] }>(`/api/v1/recording-units/${unitId}/fields/${fieldId}/values?offset=0&limit=200`)).data,
  });

  // Runs one write, then refreshes what shows the relations; a failure stays in the overlay.
  const run = async (write: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await write();
      await queryClient.invalidateQueries({ queryKey: queryKeys.stratigraphyValues(unitId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.relationGraph(unitId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.relationList(unitId) });
      onChanged();
      setForm(null);
    } catch (e) {
      setError(messageForError(e, t("recordingUnit.graph.failed")));
    } finally {
      setBusy(false);
    }
  };

  const body = (q: Qualifier) => ({ typeConceptId: q.type ? Number(q.type.id) : null, uncertain: q.uncertain });
  const link = (unit: PickerOption, q: Qualifier) => run(() => addStratigraphyLink(unitId, position, unit.id, body(q)));
  const remove = (ref: StratigraphyRef) => run(() => removeStratigraphyLink(unitId, ref.resourceId));
  const save = () => {
    if (!form) return;
    if (form.kind === "edit") void run(() => updateStratigraphyLink(unitId, form.ref.resourceId, body(form.qualifier)));
    else if (form.unit) void link(form.unit, form.qualifier);
  };

  const loadUnits = async (query: string): Promise<PickerOption[]> => {
    if (projectId == null) return [];
    const found = await searchProjectUnits(projectId, query);
    return found.filter((option) => option.id !== String(unitId));
  };

  if (creating) {
    // The created unit is only picked: the link is made when the user confirms it.
    return (
      <NewUnitForm
        projectId={projectId}
        organizationId={organizationId}
        onCreated={(unit) => { setCreating(false); setForm({ kind: "add", unit, qualifier: { type: null, uncertain: false } }); }}
        onCancel={() => setCreating(false)}
      />
    );
  }

  const open = (ref: StratigraphyRef) => {
    if (!openEntity) return;
    openEntity("recordingUnit", ref.resourceId);
    onClose();
  };

  const rows = values.data ?? [];
  const editedId = form?.kind === "edit" ? form.ref.resourceId : null;
  const vocabulary = { fieldId, valueConceptId: typeConceptId };
  return (
    <FitToViewport>
      <div className={`sia-strati-editor${busy ? " sia-strati-editor-busy" : ""}`}>
        <div className="sia-strati-chips">
          {rows.map((ref) => {
            const text = qualifiedLabel(ref.label ?? ref.resourceId, ref.qualifier);
            return (
              <span key={ref.resourceId} className={`ref-chip sia-strati-chip${editedId === ref.resourceId ? " sia-strati-chip-edited" : ""}`} style={refChipStyle("recording-units")}>
                <span
                  className="resource-ref-token-link"
                  role="link"
                  tabIndex={0}
                  title={t("field.open", { label: text })}
                  onClick={() => open(ref)}
                  onKeyDown={(e) => { if (e.key === "Enter") open(ref); }}
                >
                  {text}
                </span>
                <button type="button" className="sia-strati-chip-button" aria-label={t("recordingUnit.graph.edit")} disabled={busy} onClick={() => setForm({ kind: "edit", ref, qualifier: qualifierOf(ref) })}>
                  <i className="pi pi-pencil" aria-hidden="true" />
                </button>
                <button type="button" className="sia-strati-chip-button" aria-label={t("recordingUnit.graph.delete")} disabled={busy} onClick={() => void remove(ref)}>
                  <i className="pi pi-times" aria-hidden="true" />
                </button>
              </span>
            );
          })}
        </div>
        {form?.kind === "edit" ? null : (
          <InlinePicker
            value={form?.kind === "add" ? form.unit : null}
            onChange={(unit) => setForm({ kind: "add", unit, qualifier: form?.kind === "add" ? form.qualifier : { type: null, uncertain: false } })}
            load={loadUnits}
            placeholder={t("recordingUnit.graph.dialog.unitPlaceholder")}
            footer={canCreateUnit(projectId) ? <NewUnitItem onClick={() => setCreating(true)} /> : undefined}
          />
        )}
        {form && (form.kind === "edit" || form.unit) && (
          <div className="sia-strati-form">
            <QualifierFields
              vocabulary={vocabulary}
              projectId={projectId}
              organizationId={organizationId}
              value={form.qualifier}
              onChange={(qualifier) => setForm({ ...form, qualifier })}
            />
            <div className="sia-strati-form-actions">
              <Button type="button" icon="pi pi-check" size="small" rounded aria-label={t("common.confirm")} disabled={busy} onClick={save} />
              <Button type="button" icon="pi pi-times" size="small" rounded text aria-label={t("common.cancel")} disabled={busy} onClick={() => setForm(null)} />
            </div>
          </div>
        )}
      </div>
    </FitToViewport>
  );
}
