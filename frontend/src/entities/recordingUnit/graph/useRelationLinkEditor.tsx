import { useCallback, useState, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { ConfirmPopup, confirmPopup } from "primereact/confirmpopup";
import { messageForError } from "../../../api/errors";
import { queryKeys } from "../../../api/queryKeys";
import {
  addHierarchyLink, addStratigraphyLink, removeHierarchyLink, removeStratigraphyLink, updateStratigraphyLink,
  type HierarchyZone, type StratigraphyZone,
} from "../../../components/relationLinks/api";
import type { LinkFormValue } from "../../../components/relationLinks/LinkForm";
import { LinkOverlay } from "../../../components/relationLinks/LinkOverlay";
import { t } from "../../../i18n";
import { zoneTitle } from "./labels";
import type { GraphUnitRef, RelationGraph, ZoneKey } from "./types";

const STRATIGRAPHY_ZONES: ZoneKey[] = ["anterior", "synchronous", "posterior"];
export const isStratigraphy = (zone?: ZoneKey): zone is StratigraphyZone => zone != null && STRATIGRAPHY_ZONES.includes(zone);
export const isHierarchy = (zone?: ZoneKey): zone is HierarchyZone => zone === "parents" || zone === "children";

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

  const submit = async (value: LinkFormValue) => {
    if (!overlay) return;
    const typeId = value.typeConceptId != null ? Number(value.typeConceptId) : null;
    if (overlay.mode === "edit") {
      await updateStratigraphyLink(unitId, overlay.ref.resourceId, { typeConceptId: typeId, uncertain: value.uncertain });
    } else if (value.unit) {
      if (isHierarchy(overlay.zone)) await addHierarchyLink(unitId, overlay.zone, value.unit.id);
      else if (isStratigraphy(overlay.zone)) await addStratigraphyLink(unitId, overlay.zone, value.unit.id, { typeConceptId: typeId, uncertain: value.uncertain });
    }
    setOverlay(null);
    await changed();
  };

  const zone = overlay?.zone;
  const title = overlay?.mode === "edit"
    ? t("recordingUnit.graph.dialog.editTitle", { label: overlay.ref.label ?? overlay.ref.resourceId })
    : t("recordingUnit.graph.dialog.addTitle", { zone: zone ? zoneTitle(zone) : "" });

  const overlays = (
    <>
      <ConfirmPopup />
      <LinkOverlay
        anchor={overlay?.anchor ?? null}
        title={title}
        mode={overlay?.mode ?? "add"}
        qualified={isStratigraphy(zone)}
        projectId={projectId}
        organizationId={organizationId}
        vocabulary={isStratigraphy(zone) && fields ? { fieldId: fields[zone], valueConceptId: typeConceptId } : undefined}
        initial={overlay?.mode === "edit"
          ? { typeConceptId: overlay.ref.qualifier?.concept?.resourceId ?? null, uncertain: !!overlay.ref.qualifier?.uncertain }
          : undefined}
        excludeUnitId={String(unitId)}
        onHide={() => setOverlay(null)}
        onSubmit={submit}
      />
    </>
  );

  return { add, edit, remove, failure, overlays };
}
