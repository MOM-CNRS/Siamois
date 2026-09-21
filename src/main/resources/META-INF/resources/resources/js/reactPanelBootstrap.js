// Bootstraps window.SiamoisMainPanel.mount() for every not-yet-mounted
// .react-main-panel-mount container on the page (plan §7.2/§8 phase 8), reading its options from
// data-* attributes rather than having focus.xhtml inline this logic with EL-interpolated values
// — keeping it a plain static resource avoids any XML/EL escaping concerns in the JSF template
// (this file is never processed by Facelets).
//
// main-panel.js (built by frontend-maven-plugin) is a plain classic script (no top-level
// import/export in the Vite build output), so it runs synchronously in document order; loading
// it before this script means window.SiamoisMainPanel.mount is already the real implementation
// by the time this runs. The window.SiamoisMainPanel._queue stub (mount.ts) is still a defensive
// fallback if a future build change makes that bundle deferred/async.
(function () {
    if (!window.SiamoisMainPanel) {
        window.SiamoisMainPanel = {
            _queue: [],
            mount: function (container, options) { this._queue.push([container, options]); },
            unmount: function () {}
        };
    }

    function actionFn(name) {
        return (name && typeof window[name] === "function") ? window[name] : undefined;
    }

    function mountContainer(container) {
        var d = container.dataset;
        container.dataset.mounted = "true";

        var options = {
            panelKind: d.panelKind,
            entityType: d.entityType,
            entityId: d.entityId || undefined,
            overviewEntityType: d.overviewEntityType || undefined,
            overviewEntityId: d.overviewEntityId || undefined,
            organizationId: d.organizationId ? Number(d.organizationId) : undefined,
            overviewOrganizationId: d.overviewOrganizationId ? Number(d.overviewOrganizationId) : undefined,
            basePath: d.basePath || "",
            csrf: { headerName: d.csrfHeader, token: d.csrfToken },
            main: {
                resourceUri: d.mainResourceUri,
                title: d.mainTitle,
                bookmarked: d.mainBookmarked === "true"
            },
            overview: d.overviewResourceUri ? {
                resourceUri: d.overviewResourceUri,
                title: d.overviewTitle,
                bookmarked: d.overviewBookmarked === "true"
            } : undefined,
            actions: {
                duplicate: actionFn(d.actionDuplicate),
                refresh: actionFn(d.actionRefresh),
                create: actionFn(d.actionCreate),
                settings: actionFn(d.actionSettings),
                listCreate: actionFn(d.actionListCreate)
            },
            overviewActions: {
                closeOverview: actionFn(d.overviewActionCloseOverview),
                fullscreen: actionFn(d.overviewActionFullscreen),
                duplicate: actionFn(d.overviewActionDuplicate),
                refresh: actionFn(d.overviewActionRefresh),
                create: actionFn(d.overviewActionCreate),
                settings: actionFn(d.overviewActionSettings)
            }
            // No onNavigate here: App.tsx owns navigation itself now (a client-side router, not
            // a page load per click) — that's the whole point of this migration.
        };

        window.SiamoisMainPanel.mount(container, options);
    }

    var containers = document.querySelectorAll(".react-main-panel-mount:not([data-mounted='true'])");
    for (var i = 0; i < containers.length; i++) {
        mountContainer(containers[i]);
    }
})();
