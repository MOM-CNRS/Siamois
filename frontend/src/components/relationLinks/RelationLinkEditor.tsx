import {useState} from "react";
import {useQuery, useQueryClient} from "@tanstack/react-query";
import {Button} from "primereact/button";
import {apiFetch} from "../../api/client";
import {messageForError} from "../../api/errors";
import {queryKeys} from "../../api/queryKeys";
import {qualifiedLabel, refChipStyle} from "../../fields/display";
import {type MessageKey, t} from "../../i18n";
import {useOpenEntity} from "../../panels/pane/entityNavigation";
import {
    addRelationLink,
    entityKeyOfCollection,
    linkIdOf,
    removeStratigraphyLink,
    searchProjectRecords,
    updateStratigraphyLink
} from "./api";
import {FitToViewport} from "./FitToViewport";
import {InlinePicker, type PickerOption} from "./InlinePicker";
import {canCreateUnit, NewUnitForm, NewUnitItem} from "./NewUnit";
import {type Qualifier, QualifierFields} from "./QualifierFields";
import {useToastOnChange} from "../../notify/NotifyProvider";

interface RelationRef {
  resourceId: string;
  resourceType?: string | null;
  label?: string | null;
  qualifier?: { concept?: { resourceId?: string; label?: string | null } | null; uncertain?: boolean | null; linkId?: number | null } | null;
}

type Form = { kind: "add"; unit: PickerOption | null; qualifier: Qualifier } | { kind: "edit"; ref: RelationRef; qualifier: Qualifier };

const qualifierOf = (ref: RelationRef): Qualifier => {
  const concept = ref.qualifier?.concept;
  return { type: concept?.resourceId ? { id: concept.resourceId, label: concept.label ?? concept.resourceId } : null, uncertain: !!ref.qualifier?.uncertain };
};

export interface RelationLinkEditorProps {
  // The relation field: its values are the chips, its vocabulary gives the qualifiers.
  fieldId: string;
  // The record being edited: its API collection ("recording-units", "finds"…) and its id.
  recordCollection: string;
  recordId: string | number;
  // The collections of the records the field can link (one picker per kind, merged).
  accepts: string[];
  // A record has only one link through this field: no picker once it has one.
  singleChoice?: boolean;
  projectId?: string | null;
  organizationId?: number;
  // The record's type, which can narrow the field's list of qualifiers.
  typeConceptId?: string;
  // After a link changed: whoever shows the relations is stale.
  onChanged: () => void;
  // A chip opened a fiche: the overlay's job is done.
  onClose: () => void;
}

const SEPARATOR = ":";

// How a kind of record is named after a picker option, when the field links several kinds.
const COLLECTION_LABELS: Record<string, MessageKey> = {
  "recording-units": "relation.collection.recording-units",
  finds: "relation.collection.finds",
  phases: "relation.collection.phases",
  containers: "relation.collection.containers",
  documents: "relation.collection.documents",
  studies: "relation.collection.studies",
  projects: "relation.collection.projects",
};

const collectionLabel = (collection: string): string => {
  const key = COLLECTION_LABELS[collection];
  return key ? t(key) : collection;
};

/**
 * The value of one relation field, edited the way every multi-valued reference field is: its chips —
 * the label opens the linked record's fiche, the cross removes the link — plus, as the only addition,
 * a pencil that edits the link's qualifier and uncertainty, and a search to add a record. Nothing
 * else: no header, no footer, it is the content of a cell's editing overlay, not a form of its own.
 * Each change is written at once, through /relation-links by this field.
 */
export function RelationLinkEditor({ fieldId, recordCollection, recordId, accepts, singleChoice, projectId, organizationId, typeConceptId, onChanged, onClose }: Readonly<RelationLinkEditorProps>) {
  // The stratigraphy keeps the key the unit's graph and list refresh; any other record has its own.
  const valuesKey = recordCollection === "recording-units"
    ? queryKeys.stratigraphyValues(recordId, fieldId)
    : queryKeys.relationValues(recordCollection, recordId, fieldId);
  const queryClient = useQueryClient();
  const openEntity = useOpenEntity();
  const [form, setForm] = useState<Form | null>(null);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useToastOnChange(error);

  const values = useQuery({
    queryKey: valuesKey,
    queryFn: async () => (await apiFetch<{ data: RelationRef[] }>(`/api/v1/${recordCollection}/${recordId}/fields/${fieldId}/values?offset=0&limit=200`)).data,
  });

  // Runs one write, then refreshes what shows the relations; a failure stays in the overlay.
  const run = async (write: () => Promise<void>) => {
    setBusy(true);
    setError(null);
    try {
      await write();
      await queryClient.invalidateQueries({ queryKey: valuesKey });
      void queryClient.invalidateQueries({ queryKey: queryKeys.relationGraph(recordId) });
      void queryClient.invalidateQueries({ queryKey: queryKeys.relationList(recordId) });
      onChanged();
      setForm(null);
    } catch (e) {
      setError(messageForError(e, t("recordingUnit.graph.failed")));
    } finally {
      setBusy(false);
    }
  };

  const body = (q: Qualifier) => ({ typeConceptId: q.type ? Number(q.type.id) : null, uncertain: q.uncertain });
  const link = (picked: PickerOption, q: Qualifier) => {
    const [collection, id] = endOf(picked);
    return run(() => addRelationLink(fieldId, { collection: recordCollection, id: recordId }, { collection, id }, body(q)));
  };
  // A picked option names its kind when the field links several ("finds:12"), else the field's one kind.
  const endOf = (picked: PickerOption): [string, string] => {
    const at = picked.id.indexOf(SEPARATOR);
    return at > 0 ? [picked.id.slice(0, at), picked.id.slice(at + 1)] : [accepts[0] ?? recordCollection, picked.id];
  };
  const remove = (ref: RelationRef) => run(() => removeStratigraphyLink(fieldId, linkIdOf(ref)));
  const save = () => {
    if (!form) return;
    if (form.kind === "edit") void run(() => updateStratigraphyLink(fieldId, linkIdOf(form.ref), body(form.qualifier)));
    else if (form.unit) void link(form.unit, form.qualifier);
  };

  const loadUnits = async (query: string): Promise<PickerOption[]> => {
    if (projectId == null) return [];
    const several = accepts.length > 1;
    const pages = await Promise.all(accepts.map(async (collection) => {
      const found = await searchProjectRecords(collection, projectId, query);
      return found
        .filter((option) => !(collection === recordCollection && option.id === String(recordId)))
        .map((option) => (several
          ? { id: `${collection}${SEPARATOR}${option.id}`, label: `${option.label} · ${collectionLabel(collection)}` }
          : option));
    }));
    return pages.flat();
  };
  // Creating a unit from the picker is only offered when units are all the field links.
  const unitsOnly = accepts.length === 1 && accepts[0] === "recording-units";

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

  const open = (ref: RelationRef) => {
    const entity = entityKeyOfCollection(ref.resourceType ?? accepts[0] ?? recordCollection);
    if (!openEntity || !entity) return;
    openEntity(entity, ref.resourceId);
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
              <span key={`${ref.resourceType ?? ""}${ref.resourceId}`} className={`ref-chip sia-strati-chip${editedId === ref.resourceId ? " sia-strati-chip-edited" : ""}`} style={refChipStyle(ref.resourceType ?? accepts[0] ?? recordCollection)}>
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
        {form?.kind === "edit" || (singleChoice && rows.length > 0) ? null : (
          <InlinePicker
            value={form?.kind === "add" ? form.unit : null}
            onChange={(unit) => setForm({ kind: "add", unit, qualifier: form?.kind === "add" ? form.qualifier : { type: null, uncertain: false } })}
            load={loadUnits}
            placeholder={unitsOnly ? t("recordingUnit.graph.dialog.unitPlaceholder") : t("relation.picker.placeholder")}
            footer={unitsOnly && canCreateUnit(projectId) ? <NewUnitItem onClick={() => setCreating(true)} /> : undefined}
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
