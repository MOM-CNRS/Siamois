import { useEffect, useMemo, useRef, useState } from "react";
import { Background, Controls, Handle, MarkerType, Position, ReactFlow, type Edge, type Node, type NodeProps } from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { usePaneState } from "../../../panels/paneState";
import { useCanEdit } from "../../../panels/writeMode";
import { entityChipStyle } from "../../../fields/display";
import { editContextOf } from "../../../fields/editContext";
import { t } from "../../../i18n";
import type { EntityPreview } from "../../types";
import { layoutGraph, type LayoutLabels, type LayoutNode } from "./layout";
import { zoneTitle } from "./labels";
import { RelationsList, RelationsListControls, RelationsListEnd } from "./RelationsList";
import { RelationsToolbar, type RelationsMode } from "./RelationsToolbar";
import { isHierarchy, isStratigraphy, useRelationLinkEditor } from "./useRelationLinkEditor";
import { useRelationGraph } from "./useRelationGraph";
import { useRelationList } from "./useRelationList";
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

// The frame around a zone: pure background, nothing in it reacts.
function FrameNode({ data }: NodeProps<Node<GraphNodeData>>) {
  return <div className={`sia-graph-frame sia-graph-frame-${data.zone ?? "none"}${data.muted ? " sia-graph-frame-muted" : ""}`} />;
}

const nodeTypes = { graph: GraphNode, frame: FrameNode };

function labelsFor(): LayoutLabels {
  const zones: ZoneKey[] = ["parents", "children", "anterior", "synchronous", "posterior", "others"];
  return {
    zone: Object.fromEntries(zones.map((zone) => [zone, zoneTitle(zone)])) as LayoutLabels["zone"],
    notApplicable: t("recordingUnit.graph.notApplicable"),
    more: (count) => t("recordingUnit.graph.more", { count }),
  };
}

export function toFlow(graph: RelationGraph, actions: NodeActions, editable: boolean, showLinks = true): { nodes: Node<GraphNodeData>[]; edges: Edge[] } {
  const layout = layoutGraph(graph, labelsFor(), { editable, showLinks });
  const nodes = layout.nodes.map<Node<GraphNodeData>>((n) => ({
    id: n.id,
    type: n.kind === "frame" ? "frame" : "graph",
    position: { x: n.x, y: n.y },
    width: n.width,
    height: n.height,
    data: { label: n.label, kind: n.kind, muted: n.muted, zone: n.zone, unitId: n.unitId, addable: n.addable, ref: n.ref, actions },
    draggable: false,
    // React Flow switches pointer events off on a node that can't be dragged or selected, and ours hold buttons.
    selectable: n.kind !== "frame",
    connectable: false,
    // Behind the tiles and the arrows.
    zIndex: n.kind === "frame" ? -1 : undefined,
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
  entity: { id: string | number; projectId?: string | null; type?: { id?: string | number; resourceId?: string | number } | null; _permissions?: { canEdit?: boolean } };
  organizationId?: number;
  onNavigate?: (entityType: string, id?: string | number) => void;
  // A unit opens in the overview panel when the fiche has one (it is in the main panel), else in place.
  onOpenOverview?: (entityType: string, id: string | number, preview?: EntityPreview) => void;
  // After a link changed: the fiche's own counts and lists are stale.
  onChanged?: () => void;
}

// The relations of a recording unit, as a graph or as a list. The graph has the unit in the middle,
// its parents above, its children below, its stratigraphic relations on the right (the more recent
// above its level, the synchronous ones on it, the older below), and the rest (not applicable yet)
// on the left; the list has them all in one table, each saying what kind of relation it is. Both
// read zone-grouped resources, so they don't know how a relation is stored; every unit can be
// opened, edited or unlinked, and added to, from either.
export function RelationGraphTab({ entity, organizationId, onNavigate, onOpenOverview, onChanged }: Props) {
  const recordingUnitId = entity.id;
  const canEdit = useCanEdit(entity);
  const { data, isLoading, isError } = useRelationGraph(recordingUnitId);
  const list = useRelationList(recordingUnitId);
  const [mode, setMode] = usePaneState<RelationsMode>("relationsMode", "graph");
  const [showLinks, setShowLinks] = usePaneState("showLinks", true);
  const typeConceptId = editContextOf(entity, "recordingUnit", organizationId).typeConceptId;
  const editor = useRelationLinkEditor({
    unitId: recordingUnitId,
    projectId: entity.projectId,
    organizationId,
    typeConceptId,
    fields: data?.fields,
    onChanged,
  });
  const { add, edit, remove } = editor;

  const open = useMemo(
    () => (unitId: string, label: string) => (onOpenOverview ?? onNavigate)?.("recordingUnit", unitId, { label, tab: "graph" }),
    [onNavigate, onOpenOverview],
  );
  const actions = useMemo<NodeActions>(() => ({
    open,
    ...(canEdit ? { add, edit, remove } : {}),
  }), [canEdit, open, add, edit, remove]);

  const flow = useMemo(() => (data ? toFlow(data, actions, canEdit, showLinks) : null), [data, actions, canEdit, showLinks]);

  const toolbar = (
    <RelationsToolbar
      mode={mode}
      onModeChange={setMode}
      showLinks={showLinks}
      onShowLinksChange={setShowLinks}
      start={mode === "list" ? <RelationsListControls list={list} /> : undefined}
      end={mode === "list" ? <RelationsListEnd list={list} editor={canEdit && data ? editor : undefined} /> : undefined}
    />
  );

  if (mode === "list") {
    return (
      <div className="sia-relation-graph" style={entityChipStyle("recordingUnit")}>
        {toolbar}
        <div className="entity-list-panel embedded">
          {list.error != null && <div className="entity-list-panel-error">{t("recordingUnit.relations.error")}</div>}
          <RelationsList list={list} onOpen={open} editor={canEdit ? editor : undefined} />
        </div>
        {editor.overlays}
      </div>
    );
  }

  if (isLoading) return <p className="sia-relation-graph-message">{t("recordingUnit.graph.loading")}</p>;
  if (isError || !flow) return <p className="sia-relation-graph-message">{t("recordingUnit.graph.error")}</p>;

  return (
    <div className="sia-relation-graph" style={entityChipStyle("recordingUnit")}>
      {toolbar}
      <div className="sia-relation-graph-canvas">
      {editor.failure && <small role="alert" className="sia-relation-graph-failure">{editor.failure}</small>}
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
      {editor.overlays}
    </div>
  );
}
