import {type ReactNode, useCallback, useState} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {ConfirmPopup, confirmPopup} from "primereact/confirmpopup";
import {messageForError} from "../../../api/errors";
import {queryKeys} from "../../../api/queryKeys";
import type {UnitOption} from "../../../components/relationLinks/api";
import {
    addHierarchyLink,
    type HierarchyZone,
    removeHierarchyLink,
    removeStratigraphyLink,
    type StratigraphyZone,
} from "../../../components/relationLinks/api";
import {LinkOverlay} from "../../../components/relationLinks/LinkOverlay";
import {CellEditOverlay} from "../../../components/table/CellEditOverlay";
import {STRATIGRAPHY_ANSWER_TYPE} from "../../../fields/stratigraphy";
import type {FieldResource} from "../../../fields/types";
import {t} from "../../../i18n";
import {zoneTitle} from "./labels";
import type {GraphUnitRef, RelationGraph, ZoneKey} from "./types";

const STRATIGRAPHY_ZONES: ZoneKey[] = ["anterior", "synchronous", "posterior"];
export const isStratigraphy = (zone?: ZoneKey): zone is StratigraphyZone => zone != null && STRATIGRAPHY_ZONES.includes(zone);
export const isHierarchy = (zone?: ZoneKey): zone is HierarchyZone => zone === "parents" || zone === "children";

// The field of the fiche that holds one side of the stratigraphy, as the cell editor takes it.
const stratigraphicField = (zone: StratigraphyZone, id: string): FieldResource => ({
  id,
  resourceType: "fields",
  label: zoneTitle(zone),
  answerType: STRATIGRAPHY_ANSWER_TYPE,
  isSystemField: true,
  valueBinding: `${zone}Relationships`,
});

type OverlayState = { mode: "add"; zone: ZoneKey; anchor: HTMLElement } | { mode: "edit"; zone: ZoneKey; ref: GraphUnitRef; anchor: HTMLElement };

export interface RelationLinkEditorOptions {
  unitId: string | number;
  projectId?: string | null;
  organizationId?: number;
  // The unit's type, which can narrow the list of relationship types.
  typeConceptId?: string;
  // The fields that hold the stratigraphic zones (from the graph): their lists give the types.
  fields?: RelationGraph["fields"];
  // After a link changed: the fiche's own counts and lists are stale.
  onChanged?: () => void;
}

export interface RelationLinkEditor {
  add: (zone: ZoneKey, anchor: HTMLElement) => void;
  edit: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => void;
  remove: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => void;
  // The last failure of a removal, to show where the caller likes.
  failure: string | null;
  // The overlay and the confirmation the actions open: rendered once, anywhere in the tab.
  overlays: ReactNode;
}

// Adding, editing and removing the relations of a unit — the same overlays and calls whether they are
// asked from the graph or from the list.
export function useRelationLinkEditor({ unitId, projectId, organizationId, typeConceptId, fields, onChanged }: RelationLinkEditorOptions): RelationLinkEditor {
  const queryClient = useQueryClient();
  const [overlay, setOverlay] = useState<OverlayState | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  const changed = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.relationGraph(unitId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.relationList(unitId) }),
    ]);
    onChanged?.();
  }, [queryClient, unitId, onChanged]);

  const add = useCallback((zone: ZoneKey, anchor: HTMLElement) => setOverlay({ mode: "add", zone, anchor }), []);
  const edit = useCallback((zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => setOverlay({ mode: "edit", zone, ref, anchor }), []);
  const remove = useCallback((zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => confirmPopup({
    target: anchor,
    message: t("recordingUnit.graph.deleteConfirm", { label: ref.label ?? ref.resourceId }),
    acceptLabel: t("common.confirm"),
    rejectLabel: t("common.cancel"),
    accept: async () => {
      setFailure(null);
      try {
        if (isHierarchy(zone)) await removeHierarchyLink(unitId, zone, ref.resourceId);
        else await removeStratigraphyLink(unitId, ref.resourceId);
        await changed();
      } catch (e) {
        setFailure(messageForError(e, t("recordingUnit.graph.failed")));
      }
    },
  }), [unitId, changed]);

  // Only a hierarchy link is added from a form; a stratigraphic one is edited in StratigraphyEditor.
  const addHierarchy = async (unit: UnitOption) => {
    if (overlay && isHierarchy(overlay.zone)) await addHierarchyLink(unitId, overlay.zone, unit.id);
    setOverlay(null);
    await changed();
  };

  const zone = overlay?.zone;
  const stratigraphic = isStratigraphy(zone) && fields ? zone : undefined;
  const hide = () => setOverlay(null);

  const overlays = (
    <>
      <ConfirmPopup />
      <LinkOverlay
        anchor={overlay && !stratigraphic ? overlay.anchor : null}
        title={t("recordingUnit.graph.dialog.addTitle", { zone: zone ? zoneTitle(zone) : "" })}
        projectId={projectId}
        organizationId={organizationId}
        excludeUnitId={String(unitId)}
        onHide={hide}
        onSubmit={addHierarchy}
      />
      {/* A stratigraphic zone: the field's own cell editor, the one of the fiche and the tables. */}
      <CellEditOverlay
        target={overlay && stratigraphic ? {
          row: { id: unitId, projectId, type: typeConceptId ? { id: typeConceptId } : null },
          field: stratigraphicField(stratigraphic, fields![stratigraphic]),
          anchor: overlay.anchor.getBoundingClientRect(),
        } : null}
        organizationId={organizationId}
        entityType="recordingUnit"
        onSave={async () => undefined}
        onSaved={() => void changed()}
        onClose={hide}
      />
    </>
  );

  return { add, edit, remove, failure, overlays };
}
