// Stratigraphy.js
// ----------------
// Requires D3.js v7+

function drawStratigraphyDiagram(svgId, centralUnitId, relationships) {

    const svgEl = document.getElementById(svgId);
    if (!svgEl) {
        console.error(`SVG element with ID ${svgId} not found.`);
        return;
    }

    const width = svgEl.clientWidth || 800;
    const height = svgEl.clientHeight || 500;

    const svg = d3.select(svgEl)
        .attr("viewBox", [0, 0, width, height]);

    svg.selectAll("*").remove(); // clean re-render

    const svgGroup = svg.append("g");

    /* ------------------------------------------------------------------
       Nodes & links
    ------------------------------------------------------------------ */

    const nodesMap = {};
    const nodes = [];
    const links = [];

    // Central node (fully fixed)
    const centralNode = {
        id: centralUnitId,
        main: true,
        zone: "central",
        x: width / 2,
        y: height / 2,
        fx: width / 2,
        fy: height / 2
    };

    nodesMap[centralUnitId] = centralNode;
    nodes.push(centralNode);

    function addRelationship(relList, zone) {

        relList.forEach((rel) => {

            const nodeId = rel.unit1Id;

            if (!nodesMap[nodeId]) {
                nodesMap[nodeId] = {
                    id: nodeId,
                    uncertain: rel.uncertain,
                    zone: zone
                };
                nodes.push(nodesMap[nodeId]);
            }

            links.push({
                source: rel.vocabularyDirection ? centralUnitId : nodeId,
                target: rel.vocabularyDirection ? nodeId : centralUnitId,
                label: rel.vocabularyLabel,
                type: rel.uncertain ? "uncertain" : "normal"
            });
        });
    }

    addRelationship(relationships.anterior || [], "anterior");
    addRelationship(relationships.posterior || [], "posterior");
    addRelationship(relationships.synchronous || [], "synchronous");

    /* ------------------------------------------------------------------
       Forces (controlled, deterministic)
    ------------------------------------------------------------------ */

    const Y_OFFSET = 140;
    const X_OFFSET = 220;

    const simulation = d3.forceSimulation(nodes)
        .force("link",
            d3.forceLink(links)
                .id(d => d.id)
                .distance(140)
                .strength(0.7)
        )
        .force("charge", d3.forceManyBody().strength(-180))
        .force("collide", d3.forceCollide(45))
        .force("y",
            d3.forceY(d => {
                if (d.zone === "anterior") return height / 2 - Y_OFFSET;
                if (d.zone === "posterior") return height / 2 + Y_OFFSET;
                return height / 2;
            }).strength(d => d.zone === "central" ? 1 : 0.6)
        )
        .force("x",
            d3.forceX(d => {
                if (d.zone === "synchronous") {
                    return d.x < width / 2
                        ? width / 2 - X_OFFSET
                        : width / 2 + X_OFFSET;
                }
                return width / 2;
            }).strength(d => d.zone === "synchronous" ? 0.4 : 0)
        )
        .alpha(0.9)
        .alphaDecay(0.1)
        .on("tick", ticked)
        .on("end", () => simulation.stop());

    /* ------------------------------------------------------------------
       Arrow marker
    ------------------------------------------------------------------ */

    svg.append("defs")
        .append("marker")
        .attr("id", "arrow")
        .attr("viewBox", "0 -5 10 10")
        .attr("refX", 18)
        .attr("refY", 0)
        .attr("markerWidth", 6)
        .attr("markerHeight", 6)
        .attr("orient", "auto")
        .append("path")
        .attr("d", "M0,-5L10,0L0,5")
        .attr("fill", "#3b82f6");

    /* ------------------------------------------------------------------
       Links
    ------------------------------------------------------------------ */

    const linkGroup = svgGroup.append("g")
        .attr("class", "links")
        .selectAll("g")
        .data(links)
        .enter()
        .append("g");

    const linkLines = linkGroup.append("line")
        .attr("stroke", d => d.type === "uncertain" ? "#f59e0b" : "var(--main-color)")
        .attr("stroke-width", 2)
        .attr("stroke-dasharray", d => d.type === "uncertain" ? "6,4" : "0")
        .attr("marker-end", "url(#arrow)");

    const linkLabels = linkGroup.append("text")
        .text(d => d.label)
        .attr("font-size", "12px")
        .attr("fill", "#111827")
        .attr("text-anchor", "middle")
        .attr("dominant-baseline", "middle");

    /* ------------------------------------------------------------------
       Nodes
    ------------------------------------------------------------------ */

    const nodeGroup = svgGroup.append("g")
        .attr("class", "nodes")
        .selectAll("g")
        .data(nodes)
        .enter()
        .append("g")
        .call(
            d3.drag()
                .filter(d => !d.main)
                .on("start", dragstarted)
                .on("drag", dragged)
                .on("end", dragended)
        );

    const NODE_WIDTH = 90;
    const NODE_HEIGHT = 40;

    nodeGroup.append("rect")
        .attr("x", -NODE_WIDTH / 2)
        .attr("y", -NODE_HEIGHT / 2)
        .attr("width", NODE_WIDTH)
        .attr("height", NODE_HEIGHT)
        .attr("rx", 10)
        .attr("fill", d =>
            d.main
                ? "var(--light-color-50)"
                : d.uncertain
                    ? "#fff7ed"
                    : "var(--light-color-50)"
        )
        .attr("stroke", d =>
            d.main
                ? "var(--dark-color)"
                : d.uncertain
                    ? "#f59e0b"
                    : "var(--main-color)"
        )
        .attr("stroke-width", d => d.main ? 3 : 2);

    nodeGroup.append("text")
        .text(d => d.id)
        .attr("text-anchor", "middle")
        .attr("dominant-baseline", "middle")
        .attr("font-weight", d => d.main ? "bold" : "normal")
        .style("pointer-events", "none");

    /* ------------------------------------------------------------------
       Tick
    ------------------------------------------------------------------ */

    function ticked() {

        linkLines
            .attr("x1", d => d.source.x)
            .attr("y1", d => d.source.y)
            .attr("x2", d => d.target.x)
            .attr("y2", d => d.target.y);

        linkLabels
            .attr("x", d => (d.source.x + d.target.x) / 2)
            .attr("y", d => (d.source.y + d.target.y) / 2 - 10);

        nodeGroup.attr("transform", d => `translate(${d.x},${d.y})`);
    }

    /* ------------------------------------------------------------------
       Drag handlers (no physics restart)
    ------------------------------------------------------------------ */


    // Drag functions
    function dragstarted(event, d) {
        if (!event.active) simulation.alphaTarget(0.3).restart();
        d.fx = d.x;
        d.fy = d.y;
    }

    function dragged(event, d) {
        d.fx = event.x;
        d.fy = event.y;
    }

    function dragended(event, d) {
        if (!event.active) simulation.alphaTarget(0);
        d.fx = d.x;
        d.fy = d.y;
    }

}

