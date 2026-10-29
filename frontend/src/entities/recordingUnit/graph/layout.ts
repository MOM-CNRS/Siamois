import type {GraphLink, GraphUnitRef, GraphZone, RelationGraph, ZoneKey} from "./types";

// Where each zone sits around the center unit: parents above, children below, the stratigraphic
// relations in a column on the right — the more recent units above the center's level, the
// synchronous ones aligned with it, the older ones below — and the rest on the left. Positions are
// computed here, by hand, so the graph is stable and needs no layout engine.

export const NODE_WIDTH = 170;
export const NODE_HEIGHT = 44;
const GAP_X = 20;
const GAP_Y = 14;
const ROW_Y = 190;
const RIGHT_X = 370;
const LEFT_X = -330;
const LABEL_HEIGHT = 26;
const TITLE_GAP = 6;
const BAND_GAP = 40;
const PER_ROW = 3;
// Without their lines the stratigraphic units needn't be stacked: each zone becomes a grid, at most this wide.
const MAX_GRID_COLUMNS = 4;
// The frame drawn around each zone: this much room between it and the zone's outermost tiles.
const FRAME_PADDING = 12;
// The section around the hierarchy and around the stratigraphy: further out than the zone frames, with room on top for its title.
const SECTION_PADDING = 28;
const SECTION_TITLE_HEIGHT = 28;

export type SectionKey = "hierarchy" | "stratigraphy";

export interface LayoutLabels {
  zone: Record<ZoneKey, string>;
  section: Record<SectionKey, string>;
  notApplicable: string;
  // "+12 autres" for the units a zone's preview doesn't carry.
  more: (count: number) => string;
}

export interface LayoutNode {
  id: string;
  kind: "unit" | "center" | "zone" | "more" | "frame" | "section";
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
  // unit / center only — the unit it opens.
  unitId?: string;
  zone?: ZoneKey;
  // section only: which one.
  section?: SectionKey;
  muted?: boolean;
  // unit only: the relation it stands for, for the edit dialog.
  ref?: GraphUnitRef;
  // zone only: whether a unit can be added to it.
  addable?: boolean;
  // unit only: how many relations it has with units other than the center — the ones the pointer shows.
  linkCount?: number;
}

export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
  dashed?: boolean;
  zone: ZoneKey;
}

export type LinkKind = GraphLink["kind"];

// A relation between two units, one of which may be the center: not drawn until the pointer is on one of them.
export interface LayoutLink {
  id: string;
  // The node ids: from the parent (or the more recent unit) to the child (or the older one); from the center, for its own.
  source: string;
  target: string;
  kind: LinkKind;
  // The relation's own type, if it has one; what its kind is called otherwise (`phrase`).
  label?: string;
  phrase: string;
  uncertain: boolean;
}

export interface GraphLayout {
  nodes: LayoutNode[];
  edges: LayoutEdge[];
  // Every relation between two units shown: the center's with each, and the units' with one another when the graph carries them.
  links: LayoutLink[];
}

export interface LayoutOptions {
  // Whether the caller can write: the zones then show their "add" button, and an empty zone keeps its title.
  editable?: boolean;
  // Whether the graph draws the detail of the relations with the center: each unit linked to it by its own line, naming
  // the relation, the zones in a column. Off (the default), a zone is a grid, its title between the center and its
  // first row, and the graph draws one arrow per zone instead.
  detailed?: boolean;
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
  const detailed = options.detailed ?? false;
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
      if (!behindARow) edges.push({ id: `edge:${id}`, source: centerId, target: id, label: edgeLabel(item) ?? labels.zone[zoneKey], dashed: !!item.qualifier?.uncertain, zone: zoneKey });
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
    // Detailed, the title closes the zone's far end; simplified, it sits where the arrow lands.
    const nearTitle = sign === -1 ? rowY(0) + NODE_HEIGHT + TITLE_GAP : rowY(0) - LABEL_HEIGHT - TITLE_GAP;
    const farTitle = sign === -1 ? lastY - LABEL_HEIGHT - TITLE_GAP : lastY + NODE_HEIGHT + TITLE_GAP;
    addTitle(zoneKey, -NODE_WIDTH / 2, detailed ? farTitle : nearTitle, zone.total === 0, editable);
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
  const columns = (zone: GraphZone) => (detailed ? 1 : Math.min(MAX_GRID_COLUMNS, Math.ceil(Math.sqrt(zoneItems(zone).length))));
  const rowsOf = (zone: GraphZone) => Math.max(1, Math.ceil(zoneItems(zone).length / Math.max(1, columns(zone))));
  const blockHeight = (zone: GraphZone) => LABEL_HEIGHT + TITLE_GAP + rowsOf(zone) * STEP - GAP_Y;
  const syncUnitsHeight = rowsOf(graph.synchronous) * STEP - GAP_Y;
  const syncUnitsTop = -syncUnitsHeight / 2;
  const syncBlockTop = syncUnitsTop - LABEL_HEIGHT - TITLE_GAP;
  const syncBlockBottom = syncUnitsTop + syncUnitsHeight;

  const block = (zoneKey: ZoneKey, zone: GraphZone, top: number) => {
    if (zone.total === 0 && !editable) return;
    addTitle(zoneKey, RIGHT_X, top, zone.total === 0, editable);
    const cols = Math.max(1, columns(zone));
    addUnits(zoneKey, zone, (index) => ({
      x: RIGHT_X + (index % cols) * (NODE_WIDTH + GAP_X),
      y: top + LABEL_HEIGHT + TITLE_GAP + Math.floor(index / cols) * STEP,
    }));
  };
  block("synchronous", graph.synchronous, syncBlockTop);
  if (graph.posterior.total > 0 || editable) block("posterior", graph.posterior, syncBlockTop - BAND_GAP - blockHeight(graph.posterior));
  if (graph.anterior.total > 0 || editable) block("anterior", graph.anterior, syncBlockBottom + BAND_GAP);

  // The left column: what is neither hierarchy nor stratigraphy. Nothing applies yet, so it only
  // shows once a relation lands in it.
  if (graph.others.total > 0) {
    addTitle("others", LEFT_X, -LABEL_HEIGHT - TITLE_GAP, true, false);
    addUnits("others", graph.others, (index) => ({ x: LEFT_X, y: index * STEP }));
  }

  // The sections, then the frames: the backgrounds the zones' tiles sit on, the outermost first.
  const all = [...sectionFrames(nodes, labels), ...zoneFrames(nodes), ...nodes];
  const links = resolveLinks(graph, nodes, labels);
  const linkCounts = new Map<string, number>();
  for (const link of links) {
    if (link.source === centerId) continue;
    for (const id of [link.source, link.target]) linkCounts.set(id, (linkCounts.get(id) ?? 0) + 1);
  }
  for (const node of nodes) if (node.kind === "unit") node.linkCount = linkCounts.get(node.id) ?? 0;
  return { nodes: all, edges: detailed ? edges : zoneEdges(nodes, centerId), links };
}

// What a relation between units is called, read from the unit it starts at to the one it ends at.
function phraseOf(kind: LinkKind, labels: LayoutLabels): string {
  return kind === "hierarchy" ? labels.zone.children : kind === "stratigraphy" ? labels.zone.anterior : labels.zone.synchronous;
}

// The relations of the units shown, between the nodes that show them: the center's with each unit (from the center,
// so the zone's title says what it is) and the units' with one another.
function resolveLinks(graph: RelationGraph, nodes: LayoutNode[], labels: LayoutLabels): LayoutLink[] {
  const center = nodes.find((n) => n.kind === "center");
  const units = nodes.filter((n) => n.kind === "unit" && n.unitId && n.zone && n.ref);
  const links: LayoutLink[] = center ? units.map((node) => {
    const kind: LinkKind = node.zone === "parents" || node.zone === "children" ? "hierarchy" : node.zone === "synchronous" ? "synchronous" : "stratigraphy";
    return {
      id: `link:${center.id}>${node.id}`, source: center.id, target: node.id, kind,
      label: edgeLabel(node.ref!), phrase: labels.zone[node.zone!], uncertain: !!node.ref!.qualifier?.uncertain,
    };
  }) : [];
  const nodeOfUnit = new Map<string, string>();
  for (const node of units) if (!nodeOfUnit.has(node.unitId!)) nodeOfUnit.set(node.unitId!, node.id);
  for (const link of graph.links ?? []) {
    const source = nodeOfUnit.get(link.from);
    const target = nodeOfUnit.get(link.to);
    if (!source || !target || source === target) continue;
    links.push({
      id: `link:${source}>${target}:${link.kind}`, source, target, kind: link.kind,
      label: link.type?.label ?? undefined, phrase: phraseOf(link.kind, labels), uncertain: !!link.uncertain,
    });
  }
  return links;
}

const HIERARCHY_ZONES: ZoneKey[] = ["parents", "children"];
const STRATIGRAPHY_ZONES: ZoneKey[] = ["anterior", "synchronous", "posterior"];

// A section around the hierarchy (the center and its parents and children) and one around the
// stratigraphy (the zones on the right), titled, so the two kinds of relation read apart.
function sectionFrames(nodes: LayoutNode[], labels: LayoutLabels): LayoutNode[] {
  const make = (section: SectionKey, members: LayoutNode[]): LayoutNode[] => {
    if (members.length === 0) return [];
    const left = Math.min(...members.map((n) => n.x)) - FRAME_PADDING - SECTION_PADDING;
    const top = Math.min(...members.map((n) => n.y)) - FRAME_PADDING - SECTION_PADDING - SECTION_TITLE_HEIGHT;
    const right = Math.max(...members.map((n) => n.x + n.width)) + FRAME_PADDING + SECTION_PADDING;
    const bottom = Math.max(...members.map((n) => n.y + n.height)) + FRAME_PADDING + SECTION_PADDING;
    return [{ id: `section:${section}`, kind: "section", section, label: labels.section[section], x: left, y: top, width: right - left, height: bottom - top }];
  };
  const inZones = (zones: ZoneKey[]) => nodes.filter((n) => n.zone && zones.includes(n.zone));
  return [
    ...make("hierarchy", [...nodes.filter((n) => n.kind === "center"), ...inZones(HIERARCHY_ZONES)]),
    ...make("stratigraphy", inZones(STRATIGRAPHY_ZONES)),
  ];
}

// A frame around each zone — its title, its units and its "+N" tile — so that where one relation kind ends and
// the next begins reads at a glance, whatever the display mode.
function zoneFrames(nodes: LayoutNode[]): LayoutNode[] {
  const zones = new Map<ZoneKey, LayoutNode[]>();
  for (const node of nodes) {
    if (!node.zone || node.kind === "center") continue;
    zones.set(node.zone, [...(zones.get(node.zone) ?? []), node]);
  }
  return [...zones.entries()].map(([zone, members]) => {
    const left = Math.min(...members.map((n) => n.x)) - FRAME_PADDING;
    const top = Math.min(...members.map((n) => n.y)) - FRAME_PADDING;
    const right = Math.max(...members.map((n) => n.x + n.width)) + FRAME_PADDING;
    const bottom = Math.max(...members.map((n) => n.y + n.height)) + FRAME_PADDING;
    return {
      id: `frame:${zone}`, kind: "frame" as const, zone, label: "",
      x: left, y: top, width: right - left, height: bottom - top,
      // A zone with nothing in it (an empty one an editor can fill) gets a fainter frame.
      muted: members.every((n) => n.kind === "zone" || n.muted),
    };
  });
}

// One arrow from the center to the title of each zone that has units.
function zoneEdges(nodes: LayoutNode[], centerId: string): LayoutEdge[] {
  const filled = new Set(nodes.filter((n) => n.zone && (n.kind === "unit" || n.kind === "more") && n.zone !== "others").map((n) => n.zone));
  return nodes
    .filter((n) => n.kind === "zone" && n.zone && filled.has(n.zone))
    .map((n) => ({ id: `edge:${n.id}`, source: centerId, target: n.id, zone: n.zone! }));
}
