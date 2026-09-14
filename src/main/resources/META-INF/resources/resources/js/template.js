const sidebar = document.getElementsByClassName("sidebar")[0];
const toggleBtn = document.getElementById("toggle-btn");
const logoutForm = document.getElementById("logout-form");


function isMobile() {
    return globalThis.matchMedia("(max-width: 768px)").matches;
}


function toggleCollapseSidebar() {
    const sidebar = document.getElementsByClassName("sidebar")[0];
    const toggleBtn = document.getElementById("toggle-btn");

    if (toggleBtn && sidebar) {
        if (isMobile()) {
            // En mobile : collapse + cacher
            sidebar.classList.add("collapsed");
            sidebar.classList.remove("open");
        } else {
            // En desktop : juste collapse/uncollapse
            sidebar.classList.toggle("collapsed");
        }
    }
}

function animateSidebarClose() {
    // Manually force the CSS collapse transition right before AJAX finishes
    let sidebar = document.getElementById('subSidebarForm');
    if (sidebar) {
        sidebar.classList.add('is-collapsed');
    }
}

function handleSidebarToggle(targetMode) {
    let sidebar = document.getElementById('subSidebarForm');
    if (!sidebar) return;

    // Normalize input to lowercase to match our JSF class naming structure
    let requestedModeClass = 'mode-' + targetMode.toLowerCase();

    let isCurrentlyCollapsed = sidebar.classList.contains('is-collapsed');
    let isSameModeActive = sidebar.classList.contains(requestedModeClass);

    if (!isCurrentlyCollapsed && isSameModeActive) {
        // toggle the button already open
        sidebar.classList.add('is-collapsed');
    }
    else if (!isCurrentlyCollapsed && !isSameModeActive) {
        // CASE 2: The sidebar is open, but you clicked the OTHER icon.
        // Action: Do NOT close it. Keep it wide open so the user just sees the content swap.
        // Optional: You can manually swap the class immediately for a smoother transition feel
        sidebar.className = sidebar.className.replace(/mode-\w+/g, requestedModeClass);
    }
    else {
        sidebar.className = sidebar.className.replace(/mode-\w+/g, requestedModeClass);
        sidebar.classList.remove('is-collapsed');
    }
}

function toggleCollapseHistory(history) {

    if(history) {
        const $historyForm = $('#subSidebarForm');
        const $history = $('#subSidebarForm\\:sidebarContent');
        const $buttons = $history.find('.panel-menu button');


        // On gère l'aspect visuel des boutons en fonction de l'état
        if ($historyForm.hasClass('is-collapsed')) {
            $buttons.addClass('ui-button-icon-only');
        } else {
            $buttons.removeClass('ui-button-icon-only');
        }
    }

}


function toggleSiamoisSidebar() {
    const sidebar = document.getElementById('siamoisNav');
    if (!sidebar) return;

    if (isMobile()) {
        // En mobile : la sidebar n'est jamais "collapsed" quand ouverte
        sidebar.classList.remove('collapsed');
        // On ne fait que l’ouvrir / fermer
        sidebar.classList.toggle('open');
    } else {
        // En desktop : comportement classique (toggle collapsed)
        sidebar.classList.toggle('collapsed');
    }
}

function toggleSiamoisSettingsSidebar() {
    const sidebar = document.getElementById('sidebarSettings');
    if (!sidebar) return;

    if (isMobile()) {
        // En mobile : la sidebar n'est jamais "collapsed" quand ouverte
        sidebar.classList.remove('collapsed');
        // On ne fait que l’ouvrir / fermer
        sidebar.classList.toggle('open');
    } else {
        // En desktop : comportement classique (toggle collapsed)
        sidebar.classList.toggle('collapsed');
    }
}

function logout() {
    logoutForm.submit();
}

// BELOW IS A TEMP SOLUTION FOR RELOADING THE PAGE ON NAVIGATOR TAB CHANGE
function handleDesynchronization(needsReload) {
    if (needsReload === true) {
        window.location.reload();
    }
}

function checkDesync() {
    const institutionId =
        document.getElementById("contextForm:currentInstitutionId")?.value;
    const panelIds =
        document.getElementById("contextForm:currentPanelIds")?.value;

    if (!institutionId || !panelIds) {
        location.reload(true);
        return;
    }

    fetch(APP_CTX + `/api/context/check?institutionId=${encodeURIComponent(institutionId)}&panelIds=${encodeURIComponent(panelIds)}`, {
        credentials: "same-origin"
    })
        .then(r => r.json())
        .then(data => {
            if (data.reload === true) {
                location.reload(true);
            }
        });
}

document.addEventListener("visibilitychange", () => {
    if (document.visibilityState === "visible") {
        checkDesync();
    }
});

// Show the spinner
function showSpinner(panelId) {
    try {
        const panel = document.getElementById(`panel-${panelId}`);
        if (!panel) {
            console.warn(`Panel with ID "panel-${panelId}" not found.`);
            return;
        }

        const spinner = panel.querySelector('#spinner');
        if (!spinner) {
            console.warn('Spinner element not found inside the panel.');
            return;
        }

        spinner.style.display = 'inline-block'; // or 'flex' if using flexbox
    } catch (error) {
        console.error('Error showing spinner:', error);
    }
}

// Hide the spinner
function hideSpinner(panelId) {
    try {
        const panel = document.getElementById(`panel-${panelId}`);
        if (!panel) {
            console.warn(`Panel with ID "panel-${panelId}" not found.`);
            return;
        }

        const spinner = panel.querySelector('#spinner');
        if (!spinner) {
            console.warn('Spinner element not found inside the panel.');
            return;
        }

        spinner.style.display = 'none';
    } catch (error) {
        console.error('Error hiding spinner:', error);
    }
}


// Handle AJAX errors
function handleAutoSaveError(xhr, status, panelId) {
    hideSpinner(panelId);
    // Show error message (e.g., growl)
    PF('templateGrowl').renderMessage({
        summary: 'Error',
        detail: 'Failed to save changes. Please try again.',
        severity: 'error'
    });
}



function handleMenuItemClick(panelSelector) {
    // Remove 'active' class from all menu items
    $('.panel-menu .menu-item').removeClass('active');

    // Add 'active' class to the clicked menu item
    $(event.currentTarget).addClass('active');

    // Scroll the corresponding panel into view
    scrollToPanel(panelSelector);
}


function scrollToPanel(selector) {
    const panel = $(selector);
    if (panel.length) {
        // Find the panel header (PrimeFaces adds aria-expanded to the header)
        const panelHeader = panel.find('.ui-panel-titlebar:first');
        if (panelHeader.length) {
            const isCollapsed = panelHeader.attr('aria-expanded') === 'false';

            if (isCollapsed) {
                // Expand the panel by clicking the header
                panelHeader.trigger('click');

                // Wait for the panel to expand before scrolling
                setTimeout(function () {
                    scrollAfterExpand(selector);
                }, 300); // Adjust delay to match the panel's animation duration
            } else {
                // Panel is already expanded, scroll directly
                scrollAfterExpand(selector);
            }
        }
    }
}

function scrollAfterExpand(selector) {
    const panel = $(selector);
    $('#flowContent').animate(
        {scrollTop: panel.position().top + $('#flowContent').scrollTop()},
        {duration: 500}
    );
}


// ********** PANEL PROGRESS BAR
// Function to show the progress bar with the custom color
function showProgressBar(widgetlet, color) {
    const progressBarValue = widgetlet.jq.find('.ui-progressbar-value');
    progressBarValue.css('background-color', color).show();
}

// Function to hide the progress bar and reset its color
function hideProgressBar(widgetlet) {
    const progressBarValue = widgetlet.jq.find('.ui-progressbar-value');
    progressBarValue.css('background-color', 'transparent').hide();
}

function loadPanel(t, data, s, xhr) {
    console.log("Original Response:", t, data, s, xhr);

    const updates = data.getElementsByTagName("update");
    for (let update of updates) {
        if (update.getAttribute("id") !== "flow-panels") {
            continue;
        }

        const cdataNode = update.childNodes[0];
        if (!cdataNode || cdataNode.nodeType !== 4) { // CDATA_SECTION_NODE
            update.parentNode.removeChild(update);
            break;
        }

        const html = cdataNode.nodeValue;
        const divParser = new DOMParser();
        const htmlDoc = divParser.parseFromString(html, "text/html");
        const container = htmlDoc.body;
        const allDivs = container.querySelectorAll("div");

        if (allDivs.length === 0) {
            update.parentNode.removeChild(update);
            break;
        }

        const firstDiv = allDivs[0];
        cdataNode.nodeValue = "";

        const flowPanelsElement = document.getElementById("flow-panels");
        if (flowPanelsElement) {
            flowPanelsElement.innerHTML = firstDiv.outerHTML;
        }

        update.parentNode.removeChild(update);
        break;
    }

    console.log("Modified Response:", data);
    return true;
}

function onCompleteCallback(panelId) {

    return;

    const flowPanels = document.getElementById("flow-panels");
    if (flowPanels) {

        // Remove existing div with same id (if any)
        const existing = document.getElementById(panelId);
        if (existing) {
            existing.remove();
        }

        const newDiv = document.createElement("div");
        newDiv.id = panelId;

        flowPanels.insertBefore(newDiv, flowPanels.firstChild);

        // Scroll container to top
        flowPanels.scrollIntoView({behavior: 'smooth', block: 'start'});

        PrimeFaces.ajax.Request.handle({
            source: panelId,
            process: "flowContent",
            update: panelId,
            onsuccess: function (data) {
                console.log("AJAX update for " + panelId + " completed");
            },
            onerror: function (xhr, status, error) {
                console.error("AJAX error for " + panelId + ": ", status, error);
            }
        });
    }
}

function showSideview(panelId, base64RootUri, base64OverviewUri) {
    const container = document.getElementById("panel-" + panelId);
    if (container) {
        const sideview = container.querySelector("#panel-splitter-" + panelId);
        if (sideview) {

            // Find and show the #overview_3 element inside the splitter
            const children = sideview.children;
            if (children.length >= 3) {
                children[2].style.display = "block"; // Third child (index 2)
            }

            // Find and show the element with class .ui-splitter-gutter inside the splitter
            const splitterGutter = sideview.querySelector(".ui-splitter-gutter");
            if (splitterGutter) {
                splitterGutter.style.display = "block";
            }
        }
    }
    // Construct the new URL
// Construct the new URL with the application context
    const newUrl = `${APP_CTX}/focus/${base64RootUri}?s=${base64OverviewUri}`;

// Push the new state
    globalThis.history.pushState(
        {
            root: base64RootUri,
            overview: base64OverviewUri,
            isCustomState: true
        },
        '',
        newUrl
    );
}

function hideSideview(panelId) {
    const container = document.getElementById("panel-" + panelId);
    if (container) {
        const sideview = container.querySelector("#panel-splitter-" + panelId);
        if (sideview) {
            // Hide the #overview_3 element inside the splitter

            const children = sideview.children;
            if (children.length >= 3) {
                children[2].style.display = "none"; // Third child (index 2)
            }

            // Hide the element with class .ui-splitter-gutter inside the splitter
            const splitterGutter = sideview.querySelector(".ui-splitter-gutter");
            if (splitterGutter) {
                splitterGutter.style.display = "none";
            }
        }
    }
}

$(globalThis).on("popstate", function(e) {

        location.reload()

});

// Only one rum overlay (expand or edit, on any row/field) may be open at a time.
let rumOpenWidgetVar = null;

function rumCloseCurrentOverlay() {
    if (rumOpenWidgetVar) {
        let w = PF(rumOpenWidgetVar);
        if (w) w.hide();
        rumOpenWidgetVar = null;
    }
}

// Called from each overlay's onHide callback so outside-click dismissal
// (or another overlay taking over) keeps the tracked state correct.
function rumOnOverlayHide(widgetVar) {
    if (rumOpenWidgetVar === widgetVar) {
        rumOpenWidgetVar = null;
    }
}

// Edit overlay's onHide: refresh the view cell no matter how the overlay
// closed (explicit close button, outside click, or another overlay taking
// over), so the chip list always reflects what was just edited.
function rumOnEditOverlayHide(id, clientId, viewId) {
    rumOnOverlayHide(id + '_editOverlay');
    PrimeFaces.ab({source: clientId, process: '@none', update: viewId});
}

// anchorId is passed explicitly to show() because the anchor used in the
// overlay's "for" attribute (the composite's outputPanel) is rendered with
// style="display:contents" and therefore has no layout box of its own to
// position against; the rum_v_<id> div does, so we align against that instead.
function rumToggleOverlay(widgetVar, anchorId) {
    if (rumOpenWidgetVar === widgetVar) {
        rumCloseCurrentOverlay();
        return;
    }
    rumCloseCurrentOverlay();
    let w = PF(widgetVar);
    if (w) {
        w.show(anchorId);
        rumOpenWidgetVar = widgetVar;
    }
}

function rumExpand(id) {
    rumToggleOverlay(id + '_expandOverlay', 'rum_v_' + id);
}

function rumEdit(id) {
    rumToggleOverlay(id + '_editOverlay', 'rum_v_' + id);
    setTimeout(function() {
        let w = PF(id + '_editOverlay');
        if (!w || !w.isVisible() || !w.jq) return;
        let input = w.jq.find('input[type="text"]').get(0);
        if (input) input.focus();
    }, 150);
}

function rumWrapperClick(event, id) {
    let wrapper = document.getElementById('rum_v_' + id);
    if (!wrapper || !wrapper.classList.contains('rum-editable')) return;
    if (event.target.closest('.rum-action-btn')) return;
    if (event.target.closest('a')) return;
    rumEdit(id);
}

function rumClose(id) {
    let w = PF(id + '_editOverlay');
    if (w) w.hide();
}

function ruInplaceOnBlur(clientId) {
    let _id = clientId.replace(/:/g, '_');
    console.log('[ruInplaceOnBlur] id=' + _id + ' justShown=' + window['ruJustShown_' + _id]);
    setTimeout(function() {
        let justShown = window['ruJustShown_' + _id];
        let panelOpen = $('.ui-autocomplete-panel:visible').length > 0;
        let ov = PF(_id + '_ruOverlay');
        let overlayOpen = ov != null && ov.isVisible();
        console.log('[ruInplaceOnBlur] after timeout — justShown=' + justShown + ' panelOpen=' + panelOpen + ' overlayOpen=' + overlayOpen);
        if (!justShown && !panelOpen && !overlayOpen) {
            PF('inplace_' + _id).save();
        }
    }, 150);
}

function updateSelectedCountChip(chipClientId, args) {
    if (!args) return;

    const total = args.totalRecords !== undefined ? args.totalRecords : args.newRowCount;
    if (total === undefined) return;

    const chip = document.getElementById(chipClientId);
    const label = chip ? chip.querySelector('.ui-chip-text') : null;
    if (!label) return;

    const selected = (label.textContent || '').split('/')[0].trim();
    label.textContent = `${selected}/${total}`;
}

// ═══════════════════════════════════════════════════════════════════════
// Shared table cell-edit overlay (see entityTable.xhtml #cellEditOverlay
// and fieldCore.xhtml's isOptimizedLightType cells). One overlay instance
// per table serves every row/column instead of each cell building its own
// edit widget; its content is swapped server-side (activateCell) before
// this shows it positioned over whichever cell was clicked.
// ═══════════════════════════════════════════════════════════════════════

/**
 * Element the user actually clicked into (set by the cell's own onclick, before the ajax
 * request that activates it even starts) — passing the live element to PrimeFaces' overlay
 * .show() sidesteps building/escaping a client-id string for positioning entirely.
 */
let siaLastClickedCell = null;

/**
 * Shows the overlay immediately on click — before its ajax request has even reached the server,
 * let alone come back with the real editor widget for the newly active cell — so opening a cell
 * never feels blocked while that round trip happens. At this point the overlay's content is
 * whatever was left over from the last cell that used it (or empty, the first time); adding
 * sia-loading dims that stale content and shows a spinner in its place (see chip.scss) until
 * siaShowCellOverlay below swaps it out. No corresponding "remove sia-loading" is needed: the
 * ajax "update" replaces this whole overlay's DOM outright, taking the class with it.
 */
function siaShowCellOverlayLoading(compositeClientId) {
    let w = PF(compositeClientId + '_cellEditOverlay');
    if (!w || !siaLastClickedCell) return;
    w.show(siaLastClickedCell);
    if (w.jq) w.jq.addClass('sia-loading');
}

/**
 * Shows the table's shared cell-edit overlay anchored on the cell just activated.
 * Called from the ajax "click" listener's oncomplete, after the overlay's content
 * has already been re-rendered server-side for the newly active cell.
 */
function siaShowCellOverlay(compositeClientId) {
    let w = PF(compositeClientId + '_cellEditOverlay');
    if (!w || !siaLastClickedCell) return;
    w.show(siaLastClickedCell);
    // The ajax "update" just replaced this whole overlay's DOM with the freshly rendered editor
    // for the newly active cell, so whatever siaShowCellOverlayLoading (below) put on the old node
    // is already gone with it — nothing to clean up here.
    setTimeout(function () {
        if (!w.jq) return;
        let input = w.jq.find('input[type="text"]').get(0);
        if (!input) return;
        input.focus();
        siaLaunchAutocompleteIfAny(input);
    }, 150);
}

/**
 * If the just-shown overlay's input is a PrimeFaces AutoComplete (the concept field), launch its
 * search immediately instead of waiting for the user to type or click the dropdown themselves:
 * an empty field gets a blank query (the same default suggestion list a dropdown click would
 * show), a field that already holds a value searches on that value instead of blanking it out —
 * calling search() directly with the input's own current text rather than searchWithDropdown()
 * (which always runs a blank query under this app's default dropdownMode="blank", ignoring
 * whatever the field already held). Calling the widget method directly rather than dispatching a
 * click on the (hidden, see chip.scss) dropdown button, since PrimeFaces binds that button's
 * handler to "mouseup", not "click" — a simulated .click() never reaches it.
 */
function siaLaunchAutocompleteIfAny(input) {
    let widgetId = $(input).data(PrimeFaces.CLIENT_ID_DATA);
    let widget = widgetId && PrimeFaces.getWidgetById(widgetId);
    if (widget && typeof widget.search === 'function') {
        widget.search(widget.input.val());
    }
}

/** No-op placeholder for the overlay's onHide — kept so a stray close never errors. */
function siaOnCellOverlayHide() {
    // The server-side "active cell" pointer (tableModel.activeCellItemId/activeCellColumn) is
    // simply overwritten the next time a cell is clicked, so nothing needs cleaning up here.
}

// ═══════════════════════════════════════════════════════════════════════
// Shared single-entity form field-edit overlay (see #fieldEditOverlay in
// singleUnitPanel.xhtml and fieldCore.xhtml's isLightTableCell fields).
// One overlay per open panel serves every field on that panel's form
// instead of each field building its own edit widget — the same idea as
// the table's cellEditOverlay above, just keyed by field instead of by
// row/column. A page can have several such overlays open at once (a main
// panel and its overview both showing a form), each with its own
// widgetVar ("fieldEditOverlay_" + panelIndex), so they never collide.
// ═══════════════════════════════════════════════════════════════════════

/** Per-panel counterpart of siaLastClickedCell — see its own doc comment above. */
let siaLastClickedFieldCell = null;

/** Per-panel counterpart of siaShowCellOverlayLoading — see its own doc comment above. */
function siaShowFieldEditOverlayLoading(widgetVar) {
    let w = PF(widgetVar);
    if (!w || !siaLastClickedFieldCell) return;
    w.show(siaLastClickedFieldCell);
    if (w.jq) w.jq.addClass('sia-loading');
}

/** Per-panel counterpart of siaShowCellOverlay — see its own doc comment above. */
function siaShowFieldEditOverlay(widgetVar) {
    let w = PF(widgetVar);
    if (!w || !siaLastClickedFieldCell) return;
    w.show(siaLastClickedFieldCell);
    setTimeout(function () {
        if (!w.jq) return;
        let input = w.jq.find('input[type="text"]').get(0);
        if (!input) return;
        input.focus();
        siaLaunchAutocompleteIfAny(input);
    }, 150);
}

/** No-op placeholder for the field overlay's onHide — kept so a stray close never errors. */
function siaOnFieldEditOverlayHide() {
    // The server-side "active field" pointer (EntityFormContext.activeColumn) is simply
    // overwritten the next time a field is clicked, so nothing needs cleaning up here.
}

// ═══════════════════════════════════════════════════════════════════════
// Shared single-entity form field-info overlay (see #fieldInfoOverlay in
// singleUnitPanel.xhtml and panelField.xhtml). One overlay per open panel
// serves every field's "Documentation"/"Supprimer" popover instead of each
// field building its own p:menu — same idea as the edit overlay above.
// ═══════════════════════════════════════════════════════════════════════

/** Per-panel counterpart of siaLastClickedFieldCell, for the info overlay. */
let siaLastClickedInfoButton = null;

/** Shows the shared field-info overlay next to the button that was just clicked. */
function siaShowFieldInfoOverlay(widgetVar) {
    let w = PF(widgetVar);
    if (!w || !siaLastClickedInfoButton) return;
    w.show(siaLastClickedInfoButton);
}
