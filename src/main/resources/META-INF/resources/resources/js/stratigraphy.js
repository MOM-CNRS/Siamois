// Define linkLabels globally
const linkLabels = {
    above: "s'appuie contre",
    below: "sous",
    side: "équivalent à",
    uncertain: "sous"
};

// Initialize stratigraphy if the SVG exists
function initStratigraphyIfNeeded() {
    const svgElement = document.getElementById("stratigraphy");
    if (!svgElement) return;

    const svg = d3.select(svgElement)
        .attr("viewBox", "0 0 800 500");

    drawStratigraphy(svg);
}

// Main drawing function
function drawStratigraphy(svg) {
    const nodes = [
        { id: "US 100", x: 400, y: 250, main: true },
        { id: "US 23",  x: 400, y: 80 },
        { id: "US 18",  x: 120, y: 250 },
        { id: "US 12",  x: 350, y: 420 },
        { id: "US 15",  x: 470, y: 420, uncertain: true }
    ];

    const links = [
        { source: "US 100", target: "US 23", type: "above", doc: ["coupe_stratigraphique.pdf", "notes_terrain.docx"] },
        { source: "US 100", target: "US 18", type: "side", doc: [] },
        { source: "US 100", target: "US 12", type: "below", doc: [] },
        { source: "US 100", target: "US 15", type: "uncertain", doc: [] }
    ];

    const nodeById = Object.fromEntries(nodes.map(n => [n.id, n]));

    // Define arrow marker
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

    // Group for links
    const linkGroup = svg.append("g")
        .attr("class", "links")
        .selectAll("g")
        .data(links)
        .enter()
        .append("g");

    // Draw lines with arrows
    linkGroup.append("line")
        .attr("x1", d => nodeById[d.source].x)
        .attr("y1", d => nodeById[d.source].y)
        .attr("x2", d => nodeById[d.target].x)
        .attr("y2", d => nodeById[d.target].y)
        .attr("stroke", d => d.type === "uncertain" ? "#f59e0b" : "#3b82f6")
        .attr("stroke-width", 2)
        .attr("stroke-dasharray", d => d.type === "uncertain" ? "6,4" : "0")
        .attr("marker-end", "url(#arrow)") // arrow at end of every link
        .style("cursor", "pointer")
        .on("click", (event, d) => {
            showSidePanel(d);
        });

    // Edge labels
    linkGroup.append("text")
        .text(d => linkLabels[d.type] || "")
        .attr("x", d => (nodeById[d.source].x + nodeById[d.target].x)/2)
        .attr("y", d => (nodeById[d.source].y + nodeById[d.target].y)/2 - 10)
        .attr("text-anchor", "middle")
        .attr("font-size", "12px")
        .attr("fill", "#111827")
        .style("cursor", "pointer")
        .on("click", (event, d) => {
            showSidePanel(d);
        });

    // Draw nodes
    const node = svg.append("g")
        .attr("class", "nodes")
        .selectAll("g")
        .data(nodes)
        .enter()
        .append("g")
        .attr("transform", d => `translate(${d.x}, ${d.y})`);

    node.append("rect")
        .attr("x", -45)
        .attr("y", -20)
        .attr("width", 90)
        .attr("height", 40)
        .attr("rx", 10)
        .attr("fill", d =>
            d.main ? "#f8fafc" :
            d.uncertain ? "#fff7ed" : "#eff6ff"
        )
        .attr("stroke", d =>
            d.uncertain ? "#f59e0b" : "#3b82f6"
        )
        .attr("stroke-width", d => d.main ? 3 : 2)
        .style("cursor", "pointer")
        .on("click", (event, d) => console.log("Node rect clicked:", d));

    node.append("text")
        .text(d => d.id)
        .attr("text-anchor", "middle")
        .attr("dominant-baseline", "middle")
        .attr("font-size", "14px")
        .attr("font-weight", d => d.main ? "bold" : "normal")
        .style("cursor", "pointer")
        .on("click", (event, d) => console.log("Node text clicked:", d));
}

// Show side panel and populate fields
function showSidePanel(d) {
    const title = document.getElementById("relationTitle");
    if (title) {
        title.textContent = `Relation – ${d.source} → ${d.target}`;
    }

    // Documents
    const docsGroup = document.getElementById("relationDocs");

    openSidePanel();
}

// Open side panel (show the div)
function openSidePanel() {
    const sidePanel = document.querySelector(".side-panel-content");
    if (sidePanel) {
        sidePanel.style.display = "block";
    }
}

// Close side panel
function closeSidePanel() {
    const sidePanel = document.querySelector(".side-panel-content");
    if (!sidePanel) return;

    sidePanel.style.display = "none";
}
