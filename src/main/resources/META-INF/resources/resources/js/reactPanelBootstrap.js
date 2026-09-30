// Bootstraps window.SiamoisMainPanel for every not-yet-mounted .react-main-panel-mount container on
// the page (plan §7.2/§8 phase 8). The container carries its own options as data-* attributes
// (panel/reactPanelMount.xhtml), and reading them is the bundle's job
// (frontend/src/mountOptions.ts, parseMountOptions), not this file's: this stays a plain static
// resource (never processed by Facelets, so no XML/EL escaping concerns) that only decides WHICH
// containers to mount.
//
// main-panel.js (built by frontend-maven-plugin) is a plain classic script (no top-level
// import/export in the Vite build output), so it runs synchronously in document order; loading
// it before this script means window.SiamoisMainPanel is already the real implementation by the
// time this runs. The _queue stub below is only a defensive fallback if a future build change makes
// that bundle deferred/async: the bundle drains it when it loads.
(function () {
    if (!window.SiamoisMainPanel) {
        window.SiamoisMainPanel = {
            _queue: [],
            mountFromDataset: function (container) { this._queue.push(container); },
            unmount: function () {}
        };
    }

    // data-mounted is what makes a JSF re-render of the flow (the write-mode switch replaces the
    // mount div) a fresh mount rather than a duplicate one on the same div.
    var containers = document.querySelectorAll(".react-main-panel-mount:not([data-mounted='true'])");
    for (var i = 0; i < containers.length; i++) {
        containers[i].dataset.mounted = "true";
        window.SiamoisMainPanel.mountFromDataset(containers[i]);
    }
})();
