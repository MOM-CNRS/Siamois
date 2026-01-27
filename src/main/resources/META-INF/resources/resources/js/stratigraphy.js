// Stratigraphy.js
// ----------------
// Requires D3.js v7+

function drawStratigraphyDiagram(svgId, centralUnitId, relationships, onEdgeSelected) {
    // Validate inputs
    const svgEl = document.getElementById(svgId);
    if (!svgEl) {
        console.error(`SVG element with ID ${svgId} not found.`);
        return;
    }
    if (!relationships) {
        console.error("Relationships data is missing.");
        return;
    }

    const width = svgEl.clientWidth || 800;
    const height = svgEl.clientHeight || 500;

    const svg = d3.select(svgEl)
        .attr("viewBox", [0, 0, width, height]);

    svg.selectAll("*").remove();

    /* ------------------------------------------------------------------
       Zoom & pan
    ------------------------------------------------------------------ */

    const zoomGroup = svg.append("g");

    const zoom = d3.zoom()
        .scaleExtent([0.5, 3])
        .on("zoom", (event) => {
            zoomGroup.attr("transform", event.transform);
        });

    svg.call(zoom);

    /* ------------------------------------------------------------------
       Data
    ------------------------------------------------------------------ */

    const nodes = [];
    const nodesMap = {};
    const links = [];

    const centerX = width / 2;
    const centerY = height / 2;

    const centralNode = {
        id: centralUnitId,
        main: true,
        zone: "central",
        x: centerX,
        y: centerY,
        fx: centerX,
        fy: centerY
    };

    nodes.push(centralNode);
    nodesMap[centralUnitId] = centralNode;

    function addRelationship(relList, zone) {
        if (!relList) return;
        relList.forEach(rel => {
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

    addRelationship(relationships.anterior, "anterior");
    addRelationship(relationships.posterior, "posterior");
    addRelationship(relationships.synchronous, "synchronous");

    /* ------------------------------------------------------------------
       Forces (layout only)
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
                if (d.zone === "anterior") return centerY - Y_OFFSET;
                if (d.zone === "posterior") return centerY + Y_OFFSET;
                return centerY;
            }).strength(d => d.zone === "central" ? 1 : 0.6)
        )
        .force("x",
            d3.forceX(d => {
                if (d.zone === "synchronous") {
                    return d.x < centerX
                        ? centerX - X_OFFSET
                        : centerX + X_OFFSET;
                }
                return centerX;
            }).strength(d => d.zone === "synchronous" ? 0.4 : 0)
        )
        .alpha(0.3)
        .alphaDecay(0.0228)
        .on("tick", ticked)
        .on("end", () => simulation.stop());

    /* ------------------------------------------------------------------
       Arrow marker (static color, original refX)
    ------------------------------------------------------------------ */

    svg.append("defs")
        .append("marker")
        .attr("id", "arrow")
        .attr("viewBox", "0 -5 10 10")
        .attr("refX", 22) // Your original value
        .attr("refY", 0)
        .attr("markerWidth", 6)
        .attr("markerHeight", 6)
        .attr("orient", "auto")
        .append("path")
        .attr("d", "M0,-5L10,0L0,5")
        .attr("fill", "var(--main-color)"); // Static color

    /* ------------------------------------------------------------------
       Links
    ------------------------------------------------------------------ */

const linkGroup = zoomGroup.append("g")
        .attr("class", "links")
        .selectAll("g")
        .data(links)
        .enter()
        .append("g")
        .on("click", (event, d) => {
            if (typeof onEdgeSelected === 'function') {
                onEdgeSelected(d);
            }
        });

    const linkLines = linkGroup.append("line")
        .attr("stroke", d => d.type === "uncertain" ? "#f59e0b" : "var(--main-color)")
        .attr("stroke-width", 2)
        .attr("stroke-dasharray", d => d.type === "uncertain" ? "6,4" : "0")
        .attr("marker-end", "url(#arrow)")
        .on("click", (event, d) => {
            event.stopPropagation();
            if (typeof onEdgeSelected === 'function') {
                onEdgeSelected(d);
            }
        });

    const linkLabels = linkGroup.append("text")
        .text(d => d.label)
        .attr("font-size", "12px")
        .attr("fill", "#111827")
        .attr("text-anchor", "middle")
        .attr("dominant-baseline", "middle")
        .attr("x", d => (d.source.x + d.target.x) / 2 + 10)
        .attr("y", d => (d.source.y + d.target.y) / 2 - 10)
        .on("click", (event, d) => {
            event.stopPropagation();
            if (typeof onEdgeSelected === 'function') {
                onEdgeSelected(d);
            }
        });

    /* ------------------------------------------------------------------
       Nodes
    ------------------------------------------------------------------ */

    const NODE_WIDTH = 90;
    const NODE_HEIGHT = 40;

    const nodeGroup = zoomGroup.append("g")
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
            .attr("x2", d => {
                const dx = d.target.x - d.source.x;
                const dy = d.target.y - d.source.y;
                const angle = Math.atan2(dy, dx);
                return d.target.x - Math.cos(angle) * (NODE_WIDTH / 2);
            })
            .attr("y2", d => {
                const dx = d.target.x - d.source.x;
                const dy = d.target.y - d.source.y;
                const angle = Math.atan2(dy, dx);
                return d.target.y - Math.sin(angle) * (NODE_HEIGHT / 2);
            });

        linkLabels
            .attr("x", d => (d.source.x + d.target.x) / 2 + 10)
            .attr("y", d => (d.source.y + d.target.y) / 2 - 10);

        nodeGroup.attr("transform", d => `translate(${d.x},${d.y})`);
    }

    /* ------------------------------------------------------------------
       Drag handlers (with zone constraints)
    ------------------------------------------------------------------ */

    function dragstarted(event, d) {
        if (!event.active) simulation.alphaTarget(0.3).restart();
        d.fx = d.x;
        d.fy = d.y;
    }

    function dragged(event, d) {
        if (d.zone === "anterior") {
            d.fy = Math.min(event.y, centerY - Y_OFFSET / 2);
        } else if (d.zone === "posterior") {
            d.fy = Math.max(event.y, centerY + Y_OFFSET / 2);
        }
        d.fx = event.x;
        d.fy = event.y;
    }

    function dragended(event, d) {
        if (!event.active) simulation.alphaTarget(0);
        d.fx = d.x;
        d.fy = d.y;
    }
}
