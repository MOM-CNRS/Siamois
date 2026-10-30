import {describe, expect, it} from "vitest";
import {layoutGraph, type LayoutLabels, type LayoutOptions, NODE_HEIGHT} from "./layout";
import type {GraphUnitRef, GraphZone, RelationGraph} from "./types";

const labels: LayoutLabels = {
  zone: { parents: "P", children: "C", anterior: "A", synchronous: "S", posterior: "N", others: "O" },
  section: { hierarchy: "H", stratigraphy: "S" },
  notApplicable: "n/a",
  more: (count) => `+${count}`,
};

const ref = (id: string, extra: Partial<GraphUnitRef> = {}): GraphUnitRef => ({ resourceId: id, resourceType: "recording-units", label: `US ${id}`, ...extra });
const zone = (values: GraphUnitRef[], total = values.length): GraphZone => ({ values, total, complete: total === values.length });
const empty = zone([]);

function graph(over: Partial<RelationGraph>): RelationGraph {
  return { center: ref("42"), parents: empty, children: empty, anterior: empty, synchronous: empty, posterior: empty, others: empty, fields: { anterior: "-328", synchronous: "-330", posterior: "-329" }, ...over };
}

// The detailed layout: each unit linked to the center, the zones in columns.
const detail = (g: RelationGraph, l: LayoutLabels, options: LayoutOptions = {}) => layoutGraph(g, l, { detailed: true, ...options });

describe("layoutGraph", () => {
  it("puts the unit in the middle, parents above, children below", () => {
    const { nodes } = detail(graph({ parents: zone([ref("1")]), children: zone([ref("2")]) }), labels);
    const center = nodes.find((n) => n.kind === "center")!;
    const parent = nodes.find((n) => n.id === "parents:1")!;
    const child = nodes.find((n) => n.id === "children:2")!;
    expect(parent.y).toBeLessThan(center.y);
    expect(child.y).toBeGreaterThan(center.y);
  });

  it("centers a row of units on the unit", () => {
    const { nodes } = detail(graph({ parents: zone([ref("1"), ref("2"), ref("3")]) }), labels);
    const xs = ["parents:1", "parents:2", "parents:3"].map((id) => nodes.find((n) => n.id === id)!.x);
    expect(xs[1]).toBeCloseTo(-85);
    expect(xs[0]).toBeLessThan(xs[1]);
    expect(xs[2]).toBeGreaterThan(xs[1]);
  });

  it("wraps a long row so it stays clear of the right column", () => {
    const children = Array.from({ length: 7 }, (_, i) => ref(`c${i}`));
    const { nodes } = detail(graph({ children: zone(children) }), labels);
    const units = nodes.filter((n) => n.zone === "children" && n.kind === "unit");
    expect(Math.max(...units.map((n) => n.x + n.width))).toBeLessThan(330);
    expect(new Set(units.map((n) => n.y)).size).toBe(3);
  });

  it("aligns the synchronous units with the center, the more recent above and the older below", () => {
    const { nodes } = detail(graph({ anterior: zone([ref("3")]), synchronous: zone([ref("4")]), posterior: zone([ref("5")]) }), labels);
    const find = (id: string) => nodes.find((n) => n.id === id)!;
    const center = find("unit:42");
    const sync = find("synchronous:4");
    expect(sync.y + sync.height / 2).toBeCloseTo(center.y + center.height / 2);
    expect(find("posterior:5").y + NODE_HEIGHT).toBeLessThan(sync.y);
    expect(find("anterior:3").y).toBeGreaterThan(sync.y + NODE_HEIGHT);
    expect(find("anterior:3").x).toBeGreaterThan(0);
  });

  it("shows the left column only once a relation is in it", () => {
    expect(detail(graph({}), labels).nodes.some((n) => n.zone === "others")).toBe(false);
    const { nodes } = detail(graph({ others: zone([ref("9")]) }), labels);
    expect(nodes.find((n) => n.id === "others:9")!.x).toBeLessThan(0);
  });

  it("centers several synchronous units on the center's level", () => {
    const { nodes } = detail(graph({ synchronous: zone([ref("4"), ref("5"), ref("6")]) }), labels);
    const ys = ["4", "5", "6"].map((id) => nodes.find((n) => n.id === `synchronous:${id}`)!.y + NODE_HEIGHT / 2);
    expect(ys[1]).toBeCloseTo(0);
    expect(ys[0] + ys[2]).toBeCloseTo(0);
  });

  it("keeps the title of an empty zone, with its add button, only when the caller can write", () => {
    const readOnly = detail(graph({}), labels).nodes.filter((n) => n.kind === "zone");
    expect(readOnly).toEqual([]);
    const writable = detail(graph({}), labels, { editable: true }).nodes.filter((n) => n.kind === "zone");
    expect(writable.map((n) => n.zone).sort()).toEqual(["anterior", "children", "parents", "posterior", "synchronous"]);
    expect(writable.find((n) => n.zone === "parents")!.addable).toBe(true);
  });

  it("names the relation on each detailed line to the center, unless the relation has a type of its own", () => {
    const g = graph({ parents: zone([ref("1")]), anterior: zone([ref("3", { qualifier: { concept: { label: "recoupe" } } })]) });
    const label = (id: string) => detail(g, labels).edges.find((e) => e.target === id)!.label;
    expect(label("parents:1")).toBe("P");
    expect(label("anterior:3")).toBe("recoupe");
  });

  describe("simplified", () => {
    const simple = (g: RelationGraph, options: LayoutOptions = {}) => layoutGraph(g, labels, options);

    it("draws one arrow to the title of each zone that has units", () => {
      const { edges, nodes } = simple(graph({ parents: zone([ref("1")]), anterior: zone([ref("3"), ref("4")]) }));
      expect(edges.map((e) => e.target).sort()).toEqual(["zone:anterior", "zone:parents"]);
      expect(edges.every((e) => e.source === "unit:42")).toBe(true);
      // The parents' title moves between the center and its units, where the arrow lands.
      expect(nodes.find((n) => n.id === "zone:parents")!.y).toBeGreaterThan(nodes.find((n) => n.id === "parents:1")!.y);
    });

    it("lays a stratigraphic zone out as a grid instead of a stack", () => {
      const units = Array.from({ length: 6 }, (_, i) => ref(`a${i}`));
      const cells = (over: LayoutOptions) => layoutGraph(graph({ anterior: zone(units) }), labels, over).nodes.filter((n) => n.zone === "anterior" && n.kind === "unit");
      expect(new Set(cells({ detailed: true }).map((n) => n.x)).size).toBe(1);
      const grid = cells({});
      expect(new Set(grid.map((n) => n.x)).size).toBe(3);
      expect(new Set(grid.map((n) => n.y)).size).toBe(2);
    });

    it("keeps the synchronous grid centered on the center's level", () => {
      const units = Array.from({ length: 4 }, (_, i) => ref(`s${i}`));
      const ys = simple(graph({ synchronous: zone(units) })).nodes.filter((n) => n.zone === "synchronous" && n.kind === "unit").map((n) => n.y + NODE_HEIGHT / 2);
      expect(Math.min(...ys) + Math.max(...ys)).toBeCloseTo(0);
    });

    it("still carries every relation, for the pointer", () => {
      expect(simple(graph({ anterior: zone([ref("3")]) })).links.map((l) => l.target)).toEqual(["anterior:3"]);
    });
  });

  it("adds a +N tile for the units a zone's preview doesn't carry", () => {
    const { nodes } = detail(graph({ children: zone([ref("2")], 12) }), labels);
    expect(nodes.find((n) => n.id === "more:children")!.label).toBe("+11");
  });

  it("labels a stratigraphic edge with its type and flags the uncertain ones", () => {
    const { edges } = detail(graph({
      anterior: zone([ref("3", { qualifier: { concept: { label: "recoupe" }, uncertain: true } })]),
    }), labels);
    expect(edges).toHaveLength(1);
    expect(edges[0].label).toBe("recoupe ?");
    expect(edges[0].dashed).toBe(true);
  });

  it("draws no title for an empty parents or children zone a reader cannot add to", () => {
    const { nodes } = detail(graph({}), labels);
    expect(nodes.some((n) => n.id === "zone:parents")).toBe(false);
  });

  describe("sections", () => {
    const sections = (g: RelationGraph, opts = {}) => detail(g, labels, opts).nodes.filter((n) => n.kind === "section");

    it("groups the center, parents and children under Hierarchy and the right column under Stratigraphy", () => {
      const { nodes } = detail(graph({ parents: zone([ref("1")]), anterior: zone([ref("3")]) }), labels);
      const hierarchy = nodes.find((n) => n.id === "section:hierarchy")!;
      const strat = nodes.find((n) => n.id === "section:stratigraphy")!;
      expect(hierarchy.label).toBe("H");
      expect(strat.label).toBe("S");
      const center = nodes.find((n) => n.kind === "center")!;
      expect(center.x).toBeGreaterThan(hierarchy.x);
      expect(center.x + center.width).toBeLessThan(hierarchy.x + hierarchy.width);
      const anterior = nodes.find((n) => n.id === "anterior:3")!;
      expect(anterior.x).toBeGreaterThan(strat.x);
      expect(hierarchy.x + hierarchy.width).toBeLessThan(strat.x);
    });

    it("keeps the hierarchy section clear of the stratigraphy one when a hierarchy row is full", () => {
      const parents = Array.from({ length: 3 }, (_, i) => ref(`p${i}`));
      const [h, s] = sections(graph({ parents: zone(parents), posterior: zone([ref("5")]) }));
      expect(h.x + h.width).toBeLessThan(s.x);
    });

    it("draws no stratigraphy section for a reader when nothing is stratified", () => {
      expect(sections(graph({})).map((n) => n.section)).toEqual(["hierarchy"]);
      expect(sections(graph({}), { editable: true }).map((n) => n.section)).toEqual(["hierarchy", "stratigraphy"]);
    });
  });

  describe("zone frames", () => {
    it("draws one frame per filled zone, behind the tiles, never around the center", () => {
      const { nodes } = detail(graph({ parents: zone([ref("1")]), posterior: zone([ref("2")]) }), labels);
      const frames = nodes.filter((n) => n.kind === "frame");
      expect(frames.map((f) => f.zone).sort()).toEqual(["parents", "posterior"]);
      // the frames come first, so they sit under everything else
      expect(nodes.slice(0, frames.length + 2).every((n) => n.kind === "frame" || n.kind === "section")).toBe(true);
    });

    it("contains the title, the units and the +N tile of its zone", () => {
      const { nodes } = detail(graph({ anterior: zone([ref("1"), ref("2")], 5) }), labels);
      const frame = nodes.find((n) => n.id === "frame:anterior")!;
      const members = nodes.filter((n) => n.zone === "anterior" && n.kind !== "frame");
      expect(members.length).toBe(4); // title, two units, "+3"
      for (const m of members) {
        expect(m.x).toBeGreaterThan(frame.x);
        expect(m.y).toBeGreaterThan(frame.y);
        expect(m.x + m.width).toBeLessThan(frame.x + frame.width);
        expect(m.y + m.height).toBeLessThan(frame.y + frame.height);
      }
    });

    it("frames an empty zone only for an editor, and fainter", () => {
      const readOnly = detail(graph({}), labels).nodes.filter((n) => n.kind === "frame");
      expect(readOnly).toEqual([]);
      const editable = detail(graph({}), labels, { editable: true }).nodes.filter((n) => n.kind === "frame");
      expect(editable.length).toBe(5);
      expect(editable.every((f) => f.muted)).toBe(true);
    });

    it("does not change where the tiles are", () => {
      const g = graph({ parents: zone([ref("1")]), synchronous: zone([ref("2")]) });
      const withFrames = detail(g, labels).nodes.find((n) => n.id === "parents:1")!;
      expect(withFrames.y).toBe(-190 - NODE_HEIGHT / 2);
    });
  });

  describe("links", () => {
    it("carries the center's relation with each unit, from the center, saying what it is by the zone's title", () => {
      const g = graph({ parents: zone([ref("1")]), anterior: zone([ref("3", { qualifier: { concept: { label: "recoupe" } } })]), synchronous: zone([ref("5")]) });
      const links = detail(g, labels).links;
      const link = (target: string) => links.find((l) => l.source === "unit:42" && l.target === target)!;
      expect([link("parents:1").kind, link("parents:1").phrase, link("parents:1").label]).toEqual(["hierarchy", "P", undefined]);
      expect([link("anterior:3").kind, link("anterior:3").label]).toEqual(["stratigraphy", "recoupe"]);
      expect(link("synchronous:5").kind).toBe("synchronous");
    });

    it("carries the relations between two units, with the vocabulary of their kind", () => {
      const g = graph({
        parents: zone([ref("1")]), children: zone([ref("2")]), anterior: zone([ref("3"), ref("4")]),
        links: [{ from: "1", to: "2", kind: "hierarchy" }, { from: "3", to: "4", kind: "stratigraphy", type: { label: "recoupe" }, uncertain: true }],
      });
      const links = detail(g, labels).links.filter((l) => l.source !== "unit:42");
      expect(links.map((l) => [l.source, l.target, l.kind, l.label, l.phrase, l.uncertain])).toEqual([
        ["parents:1", "children:2", "hierarchy", undefined, "C", false],
        ["anterior:3", "anterior:4", "stratigraphy", "recoupe", "A", true],
      ]);
    });

    it("counts, on each unit, its relations with units other than the center", () => {
      const g = graph({
        parents: zone([ref("1")]), children: zone([ref("2")]), anterior: zone([ref("3"), ref("4")]),
        links: [{ from: "1", to: "2", kind: "hierarchy" }, { from: "3", to: "4", kind: "stratigraphy" }, { from: "1", to: "4", kind: "stratigraphy" }],
      });
      const { nodes } = detail(g, labels);
      const count = (id: string) => nodes.find((n) => n.id === id)!.linkCount;
      expect([count("parents:1"), count("children:2"), count("anterior:3"), count("anterior:4")]).toEqual([2, 1, 1, 2]);
      expect(nodes.find((n) => n.kind === "center")!.linkCount).toBeUndefined();
    });

    it("ignores a relation with a unit that is not shown", () => {
      const g = graph({ anterior: zone([ref("3")]), links: [{ from: "3", to: "999", kind: "stratigraphy" }] });
      expect(detail(g, labels).links.map((l) => l.target)).toEqual(["anterior:3"]);
          });
  });
});
