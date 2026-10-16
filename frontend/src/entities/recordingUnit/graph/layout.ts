import type { GraphUnitRef, GraphZone, RelationGraph, ZoneKey } from "./types";

// Where each zone sits around the center unit: parents above, children below, the stratigraphic
// relations in a column on the right — the more recent units above the center's level, the
// synchronous ones aligned with it, the older ones below — and the rest on the left. Positions are
// computed here, by hand, so the graph is stable and needs no layout engine.

export const NODE_WIDTH = 170;
export const NODE_HEIGHT = 44;
const GAP_X = 20;
const GAP_Y = 14;
const ROW_Y = 190;
const RIGHT_X = 330;
const LEFT_X = -330;
const LABEL_HEIGHT = 26;
const TITLE_GAP = 6;
const BAND_GAP = 28;
const PER_ROW = 3;
// Without their lines the stratigraphic units needn't be stacked: each zone becomes a grid, at most this wide.
const MAX_GRID_COLUMNS = 4;

export interface LayoutLabels {
  zone: Record<ZoneKey, string>;
  notApplicable: string;
  // "+12 autres" for the units a zone's preview doesn't carry.
  more: (count: number) => string;
}

export interface LayoutNode {
  id: string;
  kind: "unit" | "center" | "zone" | "more";
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
  // unit / center only — the unit it opens.
  unitId?: string;
  zone?: ZoneKey;
  muted?: boolean;
  // unit only: the relation it stands for, for the edit dialog.
  ref?: GraphUnitRef;
  // zone only: whether a unit can be added to it.
  addable?: boolean;
}

export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  dashed?: boolean;
  zone: ZoneKey;
}

export interface GraphLayout {
  nodes: LayoutNode[];
  edges: LayoutEdge[];
}

export interface LayoutOptions {
  // Whether the caller can write: the zones then show their "add" button, and an empty zone keeps its title.
  editable?: boolean;
  // Whether each unit is linked to the center by its own line (the default). Off, a hierarchy zone's
  // title moves between the center and its first row, and the graph draws one arrow per zone instead.
  showLinks?: boolean;
}

function edgeLabel(ref: GraphUnitRef): string | undefined {
  const concept = ref.qualifier?.concept?.label ?? undefined;
  const uncertain = ref.qualifier?.uncertain ? "?" : "";
  const text = [concept, uncertain].filter(Boolean).join(" ");
  return text || undefined;
}

// The units a zone shows, plus the "+N" tile when its preview is shorter than its total.
function zoneItems(zone: GraphZone): Array<GraphUnitRef | { more: number }> {
  const items: Array<GraphUnitRef | { more: number }> = [...zone.values];
  const hidden = zone.total - zone.values.length;
  if (hidden > 0) items.push({ more: hidden });
  return items;
}

function isMore(item: GraphUnitRef | { more: number }): item is { more: number } {
  return "more" in item;
}

const STEP = NODE_HEIGHT + GAP_Y;

export function layoutGraph(graph: RelationGraph, labels: LayoutLabels, options: LayoutOptions = {}): GraphLayout {
  const editable = options.editable ?? false;
  const showLinks = options.showLinks ?? true;
  const nodes: LayoutNode[] = [];
  const edges: LayoutEdge[] = [];
  const centerId = `unit:${graph.center.resourceId}`;

  nodes.push({
    id: centerId, kind: "center", unitId: graph.center.resourceId,
    x: -NODE_WIDTH / 2, y: -NODE_HEIGHT / 2, width: NODE_WIDTH, height: NODE_HEIGHT,
    label: graph.center.label ?? graph.center.resourceId,
  });

  const addUnits = (zoneKey: ZoneKey, zone: GraphZone, place: (index: number, count: number) => { x: number; y: number }) => {
    const items = zoneItems(zone);
    items.forEach((item, index) => {
      const { x, y } = place(index, items.length);
      if (isMore(item)) {
        nodes.push({ id: `more:${zoneKey}`, kind: "more", x, y, width: NODE_WIDTH, height: NODE_HEIGHT, label: labels.more(item.more), zone: zoneKey, muted: true });
        return;
      }
      const id = `${zoneKey}:${item.resourceId}`;
      nodes.push({ id, kind: "unit", unitId: item.resourceId, ref: item, x, y, width: NODE_WIDTH, height: NODE_HEIGHT, label: item.label ?? item.resourceId, zone: zoneKey });
      // A wrapped hierarchy zone links only its first row: an edge to a unit behind another would cross it.
      const behindARow = (zoneKey === "parents" || zoneKey === "children") && index >= PER_ROW;
      if (!behindARow) edges.push({ id: `edge:${id}`, source: centerId, target: id, label: edgeLabel(item), dashed: !!item.qualifier?.uncertain, zone: zoneKey });
    });
  };

  const addTitle = (zoneKey: ZoneKey, x: number, y: number, muted: boolean, addable: boolean) => {
    nodes.push({ id: `zone:${zoneKey}`, kind: "zone", x, y, width: NODE_WIDTH, height: LABEL_HEIGHT, label: labels.zone[zoneKey], zone: zoneKey, muted, addable });
  };

  // Units laid out in rows of at most PER_ROW, centered on x = 0, above (sign -1) or below (+1) the
  // center; the first row is the closest to it, the zone's title sits past the last.
  const row = (zoneKey: ZoneKey, zone: GraphZone, sign: 1 | -1) => {
    if (zone.total === 0 && !editable) return;
    const count = zoneItems(zone).length;
    const rows = Math.max(1, Math.ceil(count / PER_ROW));
    const rowY = (r: number) => sign * (ROW_Y + r * STEP) - NODE_HEIGHT / 2;
    const lastY = rowY(rows - 1);
    // With its lines, the title closes the zone's far end; without them, it sits where the arrow lands.
    const nearTitle = sign === -1 ? rowY(0) + NODE_HEIGHT + TITLE_GAP : rowY(0) - LABEL_HEIGHT - TITLE_GAP;
    const farTitle = sign === -1 ? lastY - LABEL_HEIGHT - TITLE_GAP : lastY + NODE_HEIGHT + TITLE_GAP;
    addTitle(zoneKey, -NODE_WIDTH / 2, showLinks ? farTitle : nearTitle, zone.total === 0, editable);
    addUnits(zoneKey, zone, (index) => {
      const r = Math.floor(index / PER_ROW);
      const inRow = Math.min(PER_ROW, count - r * PER_ROW);
      return { x: ((index % PER_ROW) - (inRow - 1) / 2) * (NODE_WIDTH + GAP_X) - NODE_WIDTH / 2, y: rowY(r) };
    });
  };
  row("parents", graph.parents, -1);
  row("children", graph.children, 1);

  // The right column. The synchronous units are centered on the center's own level; the more recent
  // ones (posterior) are stacked above that block, the older ones (anterior) below it. Each block is
  // a title followed by its units.
  const columns = (zone: GraphZone) => (showLinks ? 1 : Math.min(MAX_GRID_COLUMNS, Math.ceil(Math.sqrt(zoneItems(zone).length))));
  const rowsOf = (zone: GraphZone) => Math.max(1, Math.ceil(zoneItems(zone).length / Math.max(1, columns(zone))));
  const blockHeight = (zone: GraphZone) => LABEL_HEIGHT + TITLE_GAP + rowsOf(zone) * STEP - GAP_Y;
  const syncUnitsHeight = rowsOf(graph.synchronous) * STEP - GAP_Y;
  const syncUnitsTop = -syncUnitsHeight / 2;
  const syncBlockTop = syncUnitsTop - LABEL_HEIGHT - TITLE_GAP;
  const syncBlockBottom = syncUnitsTop + syncUnitsHeight;

  const block = (zoneKey: ZoneKey, zone: GraphZone, top: number) => {
    if (zone.total === 0 && !editable) return;
    const cols = Math.max(1, columns(zone));
    addTitle(zoneKey, RIGHT_X, top, zone.total === 0, editable);
    addUnits(zoneKey, zone, (index) => ({
      x: RIGHT_X + (index % cols) * (NODE_WIDTH + GAP_X),
      y: top + LABEL_HEIGHT + TITLE_GAP + Math.floor(index / cols) * STEP,
    }));
  };
  block("synchronous", graph.synchronous, syncBlockTop);
  if (graph.posterior.total > 0 || editable) block("posterior", graph.posterior, syncBlockTop - BAND_GAP - blockHeight(graph.posterior));
  if (graph.anterior.total > 0 || editable) block("anterior", graph.anterior, syncBlockBottom + BAND_GAP);

  // The left column: what is neither hierarchy nor stratigraphy. Nothing applies yet.
  addTitle("others", LEFT_X, -LABEL_HEIGHT - TITLE_GAP, true, false);
  if (graph.others.total === 0) {
    nodes.push({ id: "others:empty", kind: "more", x: LEFT_X, y: 0, width: NODE_WIDTH, height: NODE_HEIGHT, label: labels.notApplicable, zone: "others", muted: true });
  } else {
    addUnits("others", graph.others, (index) => ({ x: LEFT_X, y: index * STEP }));
  }

  if (!showLinks) return { nodes, edges: zoneEdges(nodes, centerId) };
  return { nodes, edges };
}

// One arrow from the center to the title of each zone that has units.
function zoneEdges(nodes: LayoutNode[], centerId: string): LayoutEdge[] {
  const filled = new Set(nodes.filter((n) => n.zone && (n.kind === "unit" || n.kind === "more") && n.zone !== "others").map((n) => n.zone));
  return nodes
    .filter((n) => n.kind === "zone" && n.zone && filled.has(n.zone))
    .map((n) => ({ id: `edge:${n.id}`, source: centerId, target: n.id, zone: n.zone! }));
}
