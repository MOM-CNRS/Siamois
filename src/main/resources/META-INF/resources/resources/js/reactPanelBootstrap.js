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

    // Unlike the other actions, this one takes arguments — the type and id of the entity to open
    // in the overview. Both are sent to the server: FlowBean.addEntityToOverviewFromRequest
    // dispatches on entityType to build the right overview panel (an action unit and a recording
    // unit sharing the same numeric id are two different entities server-side).
    function setOverviewFn(name) {
        var fn = actionFn(name);
        return fn ? function (entityType, id) { return fn({ entityType: entityType, id: id }); } : undefined;
    }

    function mountContainer(container) {
        var d = container.dataset;
        container.dataset.mounted = "true";

        var options = {
            panelIndex: d.panelIndex,
            panelKind: d.panelKind,
            entityType: d.entityType,
            entityId: d.entityId || undefined,
            overviewEntityType: d.overviewEntityType || undefined,
            overviewEntityId: d.overviewEntityId || undefined,
            organizationId: d.organizationId ? Number(d.organizationId) : undefined,
            overviewOrganizationId: d.overviewOrganizationId ? Number(d.overviewOrganizationId) : undefined,
            // FlowBean.isWriteMode — the topbar's global read/write switch (pages/shared/topbar.xhtml).
            // No event bridge is needed for changes: that switch's p:ajax does update="flow", which
            // replaces this very container, so React is remounted with the new value (the
            // data-mounted guard below is what makes that a remount rather than a duplicate mount).
            writeMode: d.writeMode === "true",
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
                listCreate: actionFn(d.actionListCreate),
                setOverview: setOverviewFn(d.actionSetOverview)
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
