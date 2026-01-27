// Stratigraphy.js
// ----------------
// D3.js v7 required

function drawStratigraphyDiagram(svgId, centralUnitId, relationships) {
    const svgEl = document.getElementById(svgId);
    if (!svgEl) {
        console.error(`SVG element with ID ${svgId} not found.`);
        return;
    }

    const width = svgEl.clientWidth || 800;
    const height = svgEl.clientHeight || 500;

    const svg = d3.select(svgEl)
        .attr("viewBox", [0, 0, width, height])
        .call(d3.zoom().scaleExtent([0.5, 3]).on("zoom", (event) => {
            svgGroup.attr("transform", event.transform);
        }));

    const svgGroup = svg.append("g");

    // Nodes and links
    const nodesMap = {};
    const nodes = [];
    const links = [];

    // Central node (main, fixed)
    const centralNode = { id: centralUnitId, main: true, fx: width/2, fy: height/2, x: width/2, y: height/2 };
    nodesMap[centralUnitId] = centralNode;
    nodes.push(centralNode);

    // Helper: add nodes and links
    function addRelationship(relList, type) {
        const yOffset = type === 'posterior' ? -100 : (type === 'anterior' ? 100 : 0);
        const xOffsetBase = type === 'synchronous' ? 150 : 0;

        relList.forEach((rel, idx) => {
            const nodeId = rel.unit1Id;
            if (!nodesMap[nodeId]) {
                nodesMap[nodeId] = { id: nodeId, uncertain: rel.uncertain };
                nodes.push(nodesMap[nodeId]);
            }

            const link = {
                source: rel.vocabularyDirection ? centralUnitId : nodeId,
                target: rel.vocabularyDirection ? nodeId : centralUnitId,
                type: rel.uncertain ? 'uncertain' : 'normal',
                label: rel.vocabularyLabel
            };
            links.push(link);

            // Pre-position nodes
            if (type === 'posterior' || type === 'anterior') {
                nodesMap[nodeId].x = width/2 + (idx - relList.length/2) * 120;
                nodesMap[nodeId].y = height/2 + yOffset;
            } else if (type === 'synchronous') {
                nodesMap[nodeId].x = width/2 + xOffsetBase * (idx % 2 === 0 ? 1 : -1);
                nodesMap[nodeId].y = height/2 + (Math.floor(idx/2) * 60) * (idx % 2 === 0 ? 1 : -1);
            }
        });
    }

    addRelationship(relationships.anterior || [], 'anterior');
    addRelationship(relationships.posterior || [], 'posterior');
    addRelationship(relationships.synchronous || [], 'synchronous');

    // Simulation
    const simulation = d3.forceSimulation(nodes)
        .force("link", d3.forceLink(links).id(d => d.id).distance(150))
        .force("charge", d3.forceManyBody().strength(-400))
        .force("center", d3.forceCenter(width / 2, height / 2))
        .force("collide", d3.forceCollide(50))
        .on("tick", ticked);

    // Arrow marker
    svg.append("defs").append("marker")
        .attr("id", "arrow")
        .attr("viewBox", "0 -5 10 10")
        .attr("refX", 10)
        .attr("refY", 0)
        .attr("markerWidth", 6)
        .attr("markerHeight", 6)
        .attr("orient", "auto")
        .append("path")
        .attr("d", "M0,-5L10,0L0,5")
        .attr("fill", "#3b82f6");

    // Links
    const linkGroup = svgGroup.append("g")
        .attr("class", "links")
        .selectAll("g")
        .data(links)
        .enter()
        .append("g");

    const linkLines = linkGroup.append("line")
        .attr("stroke", d => d.type === 'uncertain' ? "#f59e0b" : "var(--main-color)")
        .attr("stroke-width", 2)
        .attr("stroke-dasharray", d => d.type === 'uncertain' ? "6,4" : "0")
        .attr("marker-end", "url(#arrow)");

    const linkLabels = linkGroup.append("text")
        .text(d => d.label)
        .attr("font-size", "12px")
        .attr("fill", "#111827")
        .attr("text-anchor", "middle")
        .attr("dominant-baseline", "middle");

    // Nodes
    const nodeGroup = svgGroup.append("g")
        .attr("class", "nodes")
        .selectAll("g")
        .data(nodes)
        .enter()
        .append("g")
        .call(d3.drag()
            .filter(d => !d.main) // only non-main nodes are draggable
            .on("start", dragstarted)
            .on("drag", dragged)
            .on("end", dragended)
        );

    const nodeWidth = 90;
    const nodeHeight = 40;

    nodeGroup.append("rect")
        .attr("x", -nodeWidth / 2)
        .attr("y", -nodeHeight / 2)
        .attr("width", nodeWidth)
        .attr("height", nodeHeight)
        .attr("rx", 10)
        .attr("fill", d => d.main ? "var(--light-color-50)" : (d.uncertain ? "#fff7ed" : "var(--light-color-50)"))
        .attr("stroke", d => d.main ? "var(--dark-color)" : (d.uncertain ? "#f59e0b" : "var(--main-color)"))
        .attr("stroke-width", d => d.main ? 3 : 2);

    nodeGroup.append("text")
        .text(d => d.id)
        .attr("text-anchor", "middle")
        .attr("dominant-baseline", "middle")
        .attr("font-weight", d => d.main ? "bold" : "normal")
        .style("pointer-events", "none");

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
