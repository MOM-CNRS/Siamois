import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "primereact/button";
import { apiFetch } from "../../api/client";
import { queryKeys } from "../../api/queryKeys";
import { renderAnswerCell } from "../../fields/display";
import type { FieldResource } from "../../fields/types";
import { t } from "../../i18n";
import { AnchoredFormOverlay } from "../AnchoredFormOverlay";
import { CreateFormShell } from "../CreateFormShell";
import { addStratigraphyLink, removeStratigraphyLink, updateStratigraphyLink, type StratigraphyZone } from "./api";
import { FitToViewport } from "./FitToViewport";
import { GRAPH_ICON, LinkForm, type LinkFormValue } from "./LinkForm";

interface StratigraphyRef {
  resourceId: string;
  label?: string | null;
  qualifier?: { concept?: { resourceId?: string; label?: string | null } | null; position?: StratigraphyZone | null; uncertain?: boolean | null } | null;
}

// Each side of a unit's stratigraphy is a field of its own, bound to where its units stand.
const POSITION_BY_BINDING: Record<string, StratigraphyZone> = {
  anteriorRelationships: "anterior",
  posteriorRelationships: "posterior",
  synchronousRelationships: "synchronous",
};

type View = { kind: "list" } | { kind: "add" } | { kind: "edit"; ref: StratigraphyRef };

interface EditorProps {
  field: FieldResource;
  // Where the units of this field stand relative to the unit being edited.
  position: StratigraphyZone;
  unitId: string | number;
  projectId?: string | null;
  organizationId?: number;
  // The unit's type, which can narrow the field's list of relationship types.
  typeConceptId?: string;
  onChanged: () => void;
  onClose: () => void;
}

// The unit's stratigraphic relationships on one side, each editable or removable, and the means to add one.
function StratigraphyEditor({ field, position, unitId, projectId, organizationId, typeConceptId, onChanged, onClose }: EditorProps) {
  const queryClient = useQueryClient();
  const [view, setView] = useState<View>({ kind: "list" });
  const [confirming, setConfirming] = useState<string | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const values = useQuery({
    queryKey: queryKeys.stratigraphyValues(unitId, field.id),
    queryFn: async () => (await apiFetch<{ data: StratigraphyRef[] }>(`/api/v1/recording-units/${unitId}/fields/${field.id}/values?offset=0&limit=200`)).data,
  });

  const changed = async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.stratigraphyValues(unitId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.relationGraph(unitId) });
    onChanged();
  };

  if (view.kind !== "list") {
    return (
      <LinkForm
        title={view.kind === "add" ? t("recordingUnit.graph.editor.add") : t("recordingUnit.graph.dialog.editTitle", { label: view.ref.label ?? view.ref.resourceId })}
        mode={view.kind}
        qualified
        projectId={projectId}
        organizationId={organizationId}
        vocabulary={{ fieldId: field.id, valueConceptId: typeConceptId }}
        initial={view.kind === "edit" ? { typeConceptId: view.ref.qualifier?.concept?.resourceId ?? null, uncertain: !!view.ref.qualifier?.uncertain } : undefined}
        excludeUnitId={String(unitId)}
        onCancel={() => setView({ kind: "list" })}
        onSubmit={async (value: LinkFormValue) => {
          const typeConceptId = value.typeConceptId != null ? Number(value.typeConceptId) : null;
          if (view.kind === "edit") await updateStratigraphyLink(unitId, view.ref.resourceId, { typeConceptId, uncertain: value.uncertain });
          else if (value.unit) await addStratigraphyLink(unitId, position, value.unit.id, { typeConceptId, uncertain: value.uncertain });
          await changed();
          setView({ kind: "list" });
        }}
      />
    );
  }

  const remove = async (ref: StratigraphyRef) => {
    setFailure(null);
    try {
      await removeStratigraphyLink(unitId, ref.resourceId);
      setConfirming(null);
      await changed();
    } catch {
      setFailure(t("recordingUnit.graph.failed"));
    }
  };

  const rows = values.data ?? [];
  return (
    <FitToViewport>
    <CreateFormShell
      entityType="recordingUnit"
      icon={GRAPH_ICON}
      title={field.label}
      canSubmit
      submitLabel={t("recordingUnit.graph.editor.close")}
      pending={false}
      error={failure}
      onSubmit={onClose}
      onCancel={onClose}
      footerNote={<button type="button" className="sia-link-button" onClick={() => setView({ kind: "add" })}><i className="bi bi-plus-square" aria-hidden="true" /> {t("recordingUnit.graph.editor.add")}</button>}
    >
      {rows.length === 0 && !values.isLoading && <span className="sia-link-editor-empty">{t("recordingUnit.graph.editor.empty")}</span>}
      <ul className="sia-link-editor-list">
        {rows.map((ref) => {
          const type = ref.qualifier?.concept?.label;
          return (
            <li key={ref.resourceId} className="sia-link-editor-row">
              <span className="sia-link-editor-label">
                <strong>{ref.label ?? ref.resourceId}</strong>
                {type && <small className="sia-link-editor-type">{type}</small>}
                {ref.qualifier?.uncertain && <small> ?</small>}
              </span>
              {confirming === ref.resourceId ? (
                <>
                  <Button type="button" icon="pi pi-check" text rounded severity="danger" aria-label={t("common.confirm")} onClick={() => void remove(ref)} />
                  <Button type="button" icon="pi pi-times" text rounded aria-label={t("common.cancel")} onClick={() => setConfirming(null)} />
                </>
              ) : (
                <>
                  <Button type="button" icon="pi pi-pencil" text rounded aria-label={t("recordingUnit.graph.edit")} onClick={() => setView({ kind: "edit", ref })} />
                  <Button type="button" icon="pi pi-trash" text rounded aria-label={t("recordingUnit.graph.delete")} onClick={() => setConfirming(ref.resourceId)} />
                </>
              )}
            </li>
          );
        })}
      </ul>
    </CreateFormShell>
    </FitToViewport>
  );
}

interface CellProps {
  field: FieldResource;
  unitId: string | number;
  stored: unknown;
  projectId?: string | null;
  organizationId?: number;
  typeConceptId?: string;
  // Whether the viewer can write (write mode and the unit's permission).
  editable: boolean;
  onChanged: () => void;
}

/**
 * One side of a recording unit's stratigraphic relationships in its fiche — the units anterior to it,
 * posterior to it or synchronous with it, each its own field: the chips as any reference field shows
 * them, and, for a writer, a click opens their editor — add, edit (type, uncertainty) and remove,
 * written straight to the unit's stratigraphic-relationships endpoints. The types offered are the
 * field's own list.
 */
export function StratigraphyCell({ field, unitId, stored, projectId, organizationId, typeConceptId, editable, onChanged }: CellProps) {
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const content = renderAnswerCell(field, stored, { all: true });
  const empty = <span className="field-value-cell-empty">—</span>;
  const position = POSITION_BY_BINDING[field.valueBinding ?? ""];

  if (!editable || !position) {
    return <span className="field-value-cell field-value-cell-readonly">{content || empty}</span>;
  }
  return (
    <>
      <span
        className="field-value-cell field-value-cell-editable"
        role="button"
        tabIndex={0}
        title={t("list.editCell", { label: field.label })}
        onClick={(e) => setAnchor(e.currentTarget)}
      >
        {content || empty}
      </span>
      <AnchoredFormOverlay anchor={anchor} ariaLabel={field.label} onHide={() => setAnchor(null)}>
        {(close) => (
          <StratigraphyEditor
            field={field}
            position={position}
            unitId={unitId}
            projectId={projectId}
            organizationId={organizationId}
            typeConceptId={typeConceptId}
            onChanged={onChanged}
            onClose={close}
          />
        )}
      </AnchoredFormOverlay>
    </>
  );
}
