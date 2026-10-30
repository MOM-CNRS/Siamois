import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {
    Background,
    Controls,
    type Edge,
    Handle,
    MarkerType,
    type Node,
    type NodeProps,
    Position,
    ReactFlow,
    ViewportPortal
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {usePersistedPaneState} from "../../../panels/pane/persistedPaneState";
import {useCanEdit} from "../../../panels/pane/writeMode";
import {entityChipStyle} from "../../../fields/display";
import {editContextOf} from "../../../fields/editContext";
import {t} from "../../../i18n";
import type {EntityPreview} from "../../shared/types";
import {layoutGraph, type LayoutLabels, type LayoutLink, type LayoutNode, type LinkKind, type SectionKey} from "./layout";
import {zoneTitle} from "./labels";
import {RelationsList, RelationsListControls, RelationsListEnd} from "./RelationsList";
import {type RelationsMode, RelationsToolbar} from "./RelationsToolbar";
import {isHierarchy, isStratigraphy, useRelationLinkEditor} from "./useRelationLinkEditor";
import {useRelationGraph} from "./useRelationGraph";
import {useRelationList} from "./useRelationList";
import type {GraphUnitRef, RelationGraph, ZoneKey} from "./types";
import {useToastOnChange} from "../../../notify/NotifyProvider";

// What a node can do. Absent when the caller cannot write (a read-only graph still opens units).
interface NodeActions {
  open: (unitId: string, label: string) => void;
  // The pointer enters (on) or leaves a unit: its relations with the other units show while it is on it.
  hover?: (nodeId: string, on: boolean) => void;
  add?: (zone: ZoneKey, anchor: HTMLElement) => void;
  edit?: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => void;
  remove?: (zone: ZoneKey, ref: GraphUnitRef, anchor: HTMLElement) => void;
}

interface GraphNodeData extends Record<string, unknown> {
  label: string;
  kind: LayoutNode["kind"];
  muted?: boolean;
  zone?: ZoneKey;
  section?: SectionKey;
  unitId?: string;
  // The unit under the pointer ("self") and the ones it is related to, by kind of relation.
  focus?: "self" | LinkKind;
  addable?: boolean;
  // How many relations the unit has with units other than the center.
  linkCount?: number;
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

function GraphNode({ id, data }: NodeProps<Node<GraphNodeData>>) {
  const [hovered, setHovered] = useState(false);
  const leaveTimer = useRef<ReturnType<typeof setTimeout>>();
  const { actions } = data;
  const isUnit = data.kind === "unit";
  const enter = () => { clearTimeout(leaveTimer.current); setHovered(true); if (isUnit) actions.hover?.(id, true); };
  // A short grace period, so crossing from the node to its buttons doesn't close them.
  const leave = () => { leaveTimer.current = setTimeout(() => { setHovered(false); if (isUnit) actions.hover?.(id, false); }, 250); };
  useEffect(() => () => clearTimeout(leaveTimer.current), []);

  const hasHandles = isUnit || data.kind === "center" || data.kind === "zone";
  const open = isUnit && data.unitId ? () => actions.open(data.unitId!, data.label) : undefined;
  return (
    <div className="sia-graph-node-wrap" onMouseEnter={enter} onMouseLeave={leave} onFocus={enter} onBlur={leave}>
      <div
        className={`sia-graph-node sia-graph-node-${data.kind}${data.focus ? ` sia-graph-node-focus-${data.focus}` : ""}`}
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
      {isUnit && !!data.linkCount && !data.focus && (
        // The unit has other relations than the one with the center: the pointer shows them.
        <span className="sia-graph-node-links">
          <i className="pi pi-share-alt" aria-hidden="true" />{data.linkCount}
        </span>
      )}
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
  if (data.section) {
    return <div className={`sia-graph-section sia-graph-section-${data.section}`}><span className="sia-graph-section-title">{data.label}</span></div>;
  }
  return <div className={`sia-graph-frame sia-graph-frame-${data.zone ?? "none"}${data.muted ? " sia-graph-frame-muted" : ""}`} />;
}

const nodeTypes = { graph: GraphNode, frame: FrameNode };

// The hierarchy keeps the hierarchy section's color, the stratigraphy (synchronous included) its own.
const KIND_COLORS: Record<LinkKind, string> = {
  hierarchy: "var(--primary-color, #3b82f6)",
  stratigraphy: "var(--orange-500, #f97316)",
  synchronous: "var(--orange-500, #f97316)",
};

interface Point {
  x: number;
  y: number;
}

const centerOf = (n: Node): Point => ({ x: n.position.x + (n.width ?? 0) / 2, y: n.position.y + (n.height ?? 0) / 2 });

// Where the line from a tile's center to a point leaves the tile's border.
function borderToward(node: Node, toward: Point): Point {
  const c = centerOf(node);
  const w = (node.width ?? 0) / 2 || 1;
  const h = (node.height ?? 0) / 2 || 1;
  const dx = toward.x - c.x;
  const dy = toward.y - c.y;
  if (dx === 0 && dy === 0) return c;
  const scale = 1 / Math.max(Math.abs(dx) / w, Math.abs(dy) / h);
  return { x: c.x + dx * scale, y: c.y + dy * scale };
}

// An arc from a tile to another, bulging to the side of its direction: however near the two tiles are, the arc
// leaves room to be seen and to carry its label at its top.
function arcBetween(from: Node, to: Node): { path: string; label: Point } {
  const a = centerOf(from);
  const b = centerOf(to);
  const dist = Math.hypot(b.x - a.x, b.y - a.y) || 1;
  const bulge = Math.max(ARC_MIN_BULGE, dist * 0.25);
  // A quadratic curve's top is half way to its control point.
  const control = { x: (a.x + b.x) / 2 - ((b.y - a.y) / dist) * bulge * 2, y: (a.y + b.y) / 2 + ((b.x - a.x) / dist) * bulge * 2 };
  const start = borderToward(from, control);
  const end = borderToward(to, control);
  return {
    path: `M ${start.x} ${start.y} Q ${control.x} ${control.y} ${end.x} ${end.y}`,
    label: { x: (start.x + 2 * control.x + end.x) / 4, y: (start.y + 2 * control.y + end.y) / 4 },
  };
}

const ARC_MIN_BULGE = 45;

// The relations of the unit under the pointer, drawn over the graph: an arc in the color of its kind, from the
// parent to the child or the more recent unit to the older one, saying what the relation is — its type, or its
// kind when it has none. Above the tiles and the other lines, so it reads whatever is behind it.
function FocusLines({ links, nodes }: { links: LayoutLink[]; nodes: Node<GraphNodeData>[] }) {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const lines = links.flatMap((link) => {
    const from = byId.get(link.source);
    const to = byId.get(link.target);
    return from && to ? [{ link, ...arcBetween(from, to) }] : [];
  });
  const kinds = [...new Set(lines.map((l) => l.link.kind))];
  return (
    <ViewportPortal>
      <svg className="sia-graph-focus" width={1} height={1}>
        <defs>
          {kinds.map((kind) => (
            <marker key={kind} id={`sia-focus-arrow-${kind}`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill={KIND_COLORS[kind]} />
            </marker>
          ))}
        </defs>
        {lines.map(({ link, path }) => (
          <path
            key={link.id}
            d={path} fill="none"
            stroke={KIND_COLORS[link.kind]} strokeWidth={2}
            strokeDasharray={link.uncertain ? "5 4" : undefined}
            markerEnd={link.kind === "synchronous" ? undefined : `url(#sia-focus-arrow-${link.kind})`}
          />
        ))}
      </svg>
      {lines.map(({ link, label }) => (
        <div
          key={link.id}
          className="sia-graph-focus-label"
          style={{ left: label.x, top: label.y, borderColor: KIND_COLORS[link.kind], color: KIND_COLORS[link.kind] }}
        >
          {link.label ?? link.phrase}{link.uncertain ? " ?" : ""}
        </div>
      ))}
    </ViewportPortal>
  );
}

function labelsFor(): LayoutLabels {
  const zones: ZoneKey[] = ["parents", "children", "anterior", "synchronous", "posterior", "others"];
  return {
    zone: Object.fromEntries(zones.map((zone) => [zone, zoneTitle(zone)])) as LayoutLabels["zone"],
    section: { hierarchy: t("recordingUnit.graph.section.hierarchy"), stratigraphy: t("recordingUnit.graph.section.stratigraphy") },
    notApplicable: t("recordingUnit.graph.notApplicable"),
    more: (count) => t("recordingUnit.graph.more", { count }),
  };
}

export function toFlow(graph: RelationGraph, actions: NodeActions, editable: boolean, detailed = false): { nodes: Node<GraphNodeData>[]; edges: Edge[]; links: LayoutLink[] } {
  const layout = layoutGraph(graph, labelsFor(), { editable, detailed });
  const nodes = layout.nodes.map<Node<GraphNodeData>>((n) => ({
    id: n.id,
    type: n.kind === "frame" || n.kind === "section" ? "frame" : "graph",
    position: { x: n.x, y: n.y },
    width: n.width,
    height: n.height,
    data: { label: n.label, kind: n.kind, muted: n.muted, zone: n.zone, section: n.section, unitId: n.unitId, addable: n.addable, linkCount: n.linkCount, ref: n.ref, actions },
    draggable: false,
    // React Flow switches pointer events off on a node that can't be dragged or selected, and ours hold buttons.
    selectable: n.kind !== "frame" && n.kind !== "section",
    connectable: false,
    // Behind the tiles and the arrows.
    zIndex: n.kind === "section" ? -2 : n.kind === "frame" ? -1 : undefined,
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
    markerEnd: isHierarchy(e.zone) || !detailed ? { type: MarkerType.ArrowClosed } : undefined,
  }));
  return { nodes, edges, links: layout.links };
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
// on the left once it has any; the list has them all in one table, each saying what kind of relation it is. Both
// read zone-grouped resources, so they don't know how a relation is stored; every unit can be
// opened, edited or unlinked, and added to, from either.
export function RelationGraphTab({ entity, organizationId, onNavigate, onOpenOverview, onChanged }: Props) {
  const recordingUnitId = entity.id;
  const canEdit = useCanEdit(entity);
  const list = useRelationList(recordingUnitId);
  const [mode, setMode] = usePersistedPaneState<RelationsMode>("relationsMode", "graph", {
    isValid: (saved) => saved === "graph" || saved === "list",
  });
  const { data, isLoading, isError } = useRelationGraph(recordingUnitId);
  const [detailed, setDetailed] = usePersistedPaneState("detailedLinks", false, { isValid: (saved) => typeof saved === "boolean" });
  const [hovered, setHovered] = useState<string | null>(null);
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
  useToastOnChange(list.error != null ? t("recordingUnit.relations.error") : null);
  useToastOnChange(editor.failure);

  const open = useMemo(
    () => (unitId: string, label: string) => (onOpenOverview ?? onNavigate)?.("recordingUnit", unitId, { label, tab: "graph" }),
    [onNavigate, onOpenOverview],
  );
  const hover = useCallback((nodeId: string, on: boolean) => setHovered((current) => (on ? nodeId : current === nodeId ? null : current)), []);
  const actions = useMemo<NodeActions>(() => ({
    open,
    hover,
    ...(canEdit ? { add, edit, remove } : {}),
  }), [canEdit, open, hover, add, edit, remove]);

  const flow = useMemo(() => (data ? toFlow(data, actions, canEdit, detailed) : null), [data, actions, canEdit, detailed]);

  // The relations of the unit under the pointer, and the tiles they join, colored by kind.
  const focus = useMemo(() => {
    if (!flow || !hovered) return null;
    const links = flow.links.filter((l) => l.source === hovered || l.target === hovered);
    const joined = new Map<string, LinkKind>();
    for (const l of links) {
      const other = l.source === hovered ? l.target : l.source;
      // A unit related in both ways keeps the hierarchy's color.
      if (joined.get(other) !== "hierarchy") joined.set(other, l.kind);
    }
    const nodes = flow.nodes.map((n) => {
      const mark: GraphNodeData["focus"] = n.id === hovered ? "self" : joined.get(n.id);
      return mark ? { ...n, data: { ...n.data, focus: mark } } : n;
    });
    return { links, nodes };
  }, [flow, hovered]);

  const toolbar = (
    <RelationsToolbar
      mode={mode}
      onModeChange={setMode}
      detailed={detailed}
      onDetailedChange={setDetailed}
      start={mode === "list" ? <RelationsListControls list={list} /> : undefined}
      end={mode === "list" ? <RelationsListEnd list={list} editor={canEdit && data ? editor : undefined} /> : undefined}
    />
  );

  if (mode === "list") {
    return (
      <div className="sia-relation-graph" style={entityChipStyle("recordingUnit")}>
        {toolbar}
        <div className="entity-list-panel embedded">
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
      <ReactFlow
        // Remounted with the display mode, so the view fits the new layout.
        key={detailed ? "detailed" : "simplified"}
        nodes={focus?.nodes ?? flow.nodes}
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
        {focus && <FocusLines links={focus.links} nodes={focus.nodes} />}
        <Controls showInteractive={false} />
      </ReactFlow>
      </div>
      {editor.overlays}
    </div>
  );
}
