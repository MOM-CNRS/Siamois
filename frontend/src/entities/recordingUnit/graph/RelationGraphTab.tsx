import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Background, Controls, Handle, MarkerType, Position, ReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { ConfirmPopup, confirmPopup } from "primereact/confirmpopup";
import { queryKeys } from "../../../api/queryKeys";
import { messageForError } from "../../../api/errors";
import { usePaneState } from "../../../panels/paneState";
import { useCanEdit } from "../../../panels/writeMode";
import { entityChipStyle } from "../../../fields/display";
import { t } from "../../../i18n";
import type { EntityPreview } from "../../types";
import {
  addHierarchyLink, addStratigraphyLink, removeHierarchyLink, removeStratigraphyLink, updateStratigraphyLink,
  type HierarchyZone, type StratigraphyZone,
} from "../../../components/relationLinks/api";
import { layoutGraph, type LayoutLabels, type LayoutNode } from "./layout";
import { GraphToolbar, type GraphOptions } from "./GraphToolbar";
import { LinkOverlay } from "../../../components/relationLinks/LinkOverlay";
import type { LinkFormValue } from "../../../components/relationLinks/LinkForm";
import { useRelationGraph } from "./useRelationGraph";
import type { GraphUnitRef, RelationGraph, ZoneKey } from "./types";

// What a node can do. Absent when the caller cannot write (a read-only graph still opens units).
interface NodeActions {
  open: (unitId: string, label: string) => void;
  add?: (zone: ZoneKey, anchor: HTMLElement) => void;
  edit?: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => void;
  remove?: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => void;
}

interface GraphNodeData extends Record<string, unknown> {
  label: string;
  kind: LayoutNode["kind"];
  muted?: boolean;
  zone?: ZoneKey;
  unitId?: string;
  addable?: boolean;
  ref?: GraphUnitRef;
  actions: NodeActions;
}

const SIDES: Array<[string, Position]> = [["top", Position.Top], ["bottom", Position.Bottom], ["left", Position.Left], ["right", Position.Right]];
const STRATIGRAPHY_ZONES: ZoneKey[] = ["anterior", "synchronous", "posterior"];
const isStratigraphy = (zone?: ZoneKey) => zone != null && STRATIGRAPHY_ZONES.includes(zone);
const isHierarchy = (zone?: ZoneKey): zone is HierarchyZone => zone === "parents" || zone === "children";

// The side of the center an edge leaves from and the side of the unit it reaches, by zone: hierarchy
// runs vertically, stratigraphy goes out to the right.
function handlesOf(zone: ZoneKey): { source: string; target: string; type: string } {
  if (zone === "parents") return { source: "src-top", target: "tgt-bottom", type: "smoothstep" };
  if (zone === "children") return { source: "src-bottom", target: "tgt-top", type: "smoothstep" };
  return { source: "src-right", target: "tgt-left", type: "default" };
}

function IconButton({ icon, title, onClick }: { icon: string; title: string; onClick: (anchor: HTMLElement) => void }) {
  return (
    <button
      type="button"
      className="sia-graph-icon-button nodrag nopan"
      title={title}
      aria-label={title}
      onClick={(e) => { e.stopPropagation(); onClick(e.currentTarget); }}
    >
      <i className={icon} />
    </button>
  );
}

function GraphNode({ data }: NodeProps<Node<GraphNodeData>>) {
  const [hovered, setHovered] = useState(false);
  const leaveTimer = useRef<ReturnType<typeof setTimeout>>();
  const { actions } = data;
  const enter = () => { clearTimeout(leaveTimer.current); setHovered(true); };
  // A short grace period, so crossing from the node to its buttons doesn't close them.
  const leave = () => { leaveTimer.current = setTimeout(() => setHovered(false), 250); };
  useEffect(() => () => clearTimeout(leaveTimer.current), []);

  const isUnit = data.kind === "unit";
  const hasHandles = isUnit || data.kind === "center" || data.kind === "zone";
  const open = isUnit && data.unitId ? () => actions.open(data.unitId!, data.label) : undefined;
  return (
    <div className="sia-graph-node-wrap" onMouseEnter={enter} onMouseLeave={leave} onFocus={enter} onBlur={leave}>
      <div
        className={`sia-graph-node sia-graph-node-${data.kind}`}
        title={data.label}
        // A unit opens on click or Enter.
        role={open ? "link" : undefined}
        tabIndex={open ? 0 : undefined}
        onClick={open}
        onKeyDown={open ? (e) => { if (e.key === "Enter") open(); } : undefined}
      >
        {hasHandles && SIDES.map(([name, position]) => (
          <span key={name}>
            <Handle id={`src-${name}`} type="source" position={position} className="sia-graph-handle" isConnectable={false} />
            <Handle id={`tgt-${name}`} type="target" position={position} className="sia-graph-handle" isConnectable={false} />
          </span>
        ))}
        {data.label}
        {data.kind === "zone" && data.addable && actions.add && data.zone && (
          <IconButton icon="pi pi-plus" title={t("recordingUnit.graph.add")} onClick={(anchor) => actions.add?.(data.zone!, anchor)} />
        )}
      </div>
      {isUnit && hovered && data.unitId && (
        <div className="sia-graph-node-actions">
          {actions.edit && isStratigraphy(data.zone) && data.ref && (
            <IconButton icon="pi pi-pencil" title={t("recordingUnit.graph.edit")} onClick={(anchor) => actions.edit?.(data.zone!, data.ref!, anchor)} />
          )}
          {actions.remove && data.ref && (
            <IconButton icon="pi pi-trash" title={t("recordingUnit.graph.delete")} onClick={(anchor) => actions.remove?.(data.zone!, data.ref!, anchor)} />
          )}
        </div>
      )}
    </div>
  );
}

const nodeTypes = { graph: GraphNode };

function labelsFor(): LayoutLabels {
  return {
    zone: {
      parents: t("recordingUnit.graph.parents"),
      children: t("recordingUnit.graph.children"),
      anterior: t("recordingUnit.graph.anterior"),
      synchronous: t("recordingUnit.graph.synchronous"),
      posterior: t("recordingUnit.graph.posterior"),
      others: t("recordingUnit.graph.others"),
    },
    notApplicable: t("recordingUnit.graph.notApplicable"),
    more: (count) => t("recordingUnit.graph.more", { count }),
  };
}

export function toFlow(graph: RelationGraph, actions: NodeActions, editable: boolean, showLinks = true): { nodes: Node<GraphNodeData>[]; edges: Edge[] } {
  const layout = layoutGraph(graph, labelsFor(), { editable, showLinks });
  const nodes = layout.nodes.map<Node<GraphNodeData>>((n) => ({
    id: n.id,
    type: "graph",
    position: { x: n.x, y: n.y },
    width: n.width,
    height: n.height,
    data: { label: n.label, kind: n.kind, muted: n.muted, zone: n.zone, unitId: n.unitId, addable: n.addable, ref: n.ref, actions },
    draggable: false,
    // React Flow switches pointer events off on a node that can't be dragged or selected, and ours hold buttons.
    selectable: true,
    connectable: false,
  }));
  const edges = layout.edges.map<Edge>((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.label,
    sourceHandle: handlesOf(e.zone).source,
    targetHandle: handlesOf(e.zone).target,
    type: handlesOf(e.zone).type,
    style: e.dashed ? { strokeDasharray: "5 4" } : undefined,
    markerEnd: isHierarchy(e.zone) || !showLinks ? { type: MarkerType.ArrowClosed } : undefined,
  }));
  return { nodes, edges };
}

interface Props {
  entity: { id: string | number; projectId?: string | null; _permissions?: { canEdit?: boolean } };
  organizationId?: number;
  onNavigate?: (entityType: string, id?: string | number) => void;
  // A unit opens in the overview panel when the fiche has one (it is in the main panel), else in place.
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  // After a link changed: the fiche's own counts and lists are stale.
  onChanged?: () => void;
}

type OverlayState = { mode: "add"; zone: ZoneKey; anchor: HTMLElement } | { mode: "edit"; zone: ZoneKey; ref: GraphUnitRef; anchor: HTMLElement };

// The relation graph of a recording unit: the unit in the middle, its parents above, its children
// below, its stratigraphic relations on the right (the more recent above its level, the synchronous
// ones on it, the older below), and the rest (not applicable yet) on the left. Reads one
// zone-grouped resource, so it doesn't know how a relation is stored; every zone can be added to,
// and every unit opened, edited or unlinked from the graph.
export function RelationGraphTab({ entity, organizationId, onNavigate, onOpenOverview, onChanged }: Props) {
  const recordingUnitId = entity.id;
  const queryClient = useQueryClient();
  const canEdit = useCanEdit(entity);
  const { data, isLoading, isError } = useRelationGraph(recordingUnitId);
  const [overlay, setOverlay] = useState<OverlayState | null>(null);
  const [showLinks, setShowLinks] = usePaneState("showLinks", true);
  const options: GraphOptions = { showLinks };
  const [failure, setFailure] = useState<string | null>(null);

  const changed = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: queryKeys.relationGraph(recordingUnitId) });
    onChanged?.();
  }, [queryClient, recordingUnitId, onChanged]);

  const actions = useMemo<NodeActions>(() => ({
    open: (unitId, label) => (onOpenOverview ?? onNavigate)?.("recordingUnit", unitId, { label, tab: "graph" }),
    ...(canEdit ? {
      add: (zone: ZoneKey, anchor: HTMLElement) => setOverlay({ mode: "add", zone, anchor }),
      edit: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => setOverlay({ mode: "edit", zone, ref, anchor }),
      remove: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => confirmPopup({
        target: anchor,
        message: t("recordingUnit.graph.deleteConfirm", { label: ref.label ?? ref.resourceId }),
        acceptLabel: t("common.confirm"),
        rejectLabel: t("common.cancel"),
        accept: async () => {
          setFailure(null);
          try {
            if (isHierarchy(zone)) await removeHierarchyLink(recordingUnitId, zone, ref.resourceId);
            else await removeStratigraphyLink(recordingUnitId, ref.resourceId);
            await changed();
          } catch (e) {
            setFailure(messageForError(e, t("recordingUnit.graph.failed")));
          }
        },
      }),
    } : {}),
  }), [canEdit, onNavigate, onOpenOverview, recordingUnitId, changed]);

  const flow = useMemo(() => (data ? toFlow(data, actions, canEdit, showLinks) : null), [data, actions, canEdit, showLinks]);

  if (isLoading) return <p className="sia-relation-graph-message">{t("recordingUnit.graph.loading")}</p>;
  if (isError || !flow) return <p className="sia-relation-graph-message">{t("recordingUnit.graph.error")}</p>;

  const submit = async (value: LinkFormValue) => {
    if (!overlay) return;
    const typeConceptId = value.typeConceptId != null ? Number(value.typeConceptId) : null;
    if (overlay.mode === "edit") {
      await updateStratigraphyLink(recordingUnitId, overlay.ref.resourceId, { typeConceptId, uncertain: value.uncertain });
    } else if (value.unit) {
      if (isHierarchy(overlay.zone)) await addHierarchyLink(recordingUnitId, overlay.zone, value.unit.id);
      else await addStratigraphyLink(recordingUnitId, overlay.zone as StratigraphyZone, value.unit.id, { typeConceptId, uncertain: value.uncertain });
    }
    setOverlay(null);
    await changed();
  };

  const labels = labelsFor();
  const overlayZone = overlay?.zone;
  const title = overlay?.mode === "edit"
    ? t("recordingUnit.graph.dialog.editTitle", { label: overlay.ref.label ?? overlay.ref.resourceId })
    : t("recordingUnit.graph.dialog.addTitle", { zone: overlayZone ? labels.zone[overlayZone] : "" });

  return (
    <div className="sia-relation-graph" style={entityChipStyle("recordingUnit")}>
      <GraphToolbar options={options} onChange={(next) => setShowLinks(next.showLinks)} />
      <div className="sia-relation-graph-canvas">
      {failure && <small role="alert" className="sia-relation-graph-failure">{failure}</small>}
      <ReactFlow
        // Remounted with the display mode, so the view fits the new layout.
        key={showLinks ? "links" : "grid"}
        nodes={flow.nodes}
        edges={flow.edges}
        nodeTypes={nodeTypes}
        fitView
        // A zone with many units is tall: the default minimum zoom (0.5) would keep it from fitting.
        minZoom={0.1}
        fitViewOptions={{ padding: 0.15 }}
        // Fit again once the canvas has its final size: the first fit can run while it is still settling.
        onInit={(instance) => requestAnimationFrame(() => void instance.fitView({ padding: 0.15 }))}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable
        proOptions={{ hideAttribution: true }}
      >
        <Background />
        <Controls showInteractive={false} />
      </ReactFlow>
      </div>
      <ConfirmPopup />
      <LinkOverlay
        anchor={overlay?.anchor ?? null}
        title={title}
        mode={overlay?.mode ?? "add"}
        qualified={isStratigraphy(overlayZone)}
        projectId={entity.projectId}
        organizationId={organizationId}
        initial={overlay?.mode === "edit"
          ? { typeConceptId: overlay.ref.qualifier?.concept?.resourceId ?? null, uncertain: !!overlay.ref.qualifier?.uncertain }
          : undefined}
        excludeUnitId={String(recordingUnitId)}
        onHide={() => setOverlay(null)}
        onSubmit={submit}
      />
    </div>
  );
}
