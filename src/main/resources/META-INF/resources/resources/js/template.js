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
    const panel = document.getElementById(`panel-${panelId}`);
    const spinner = panel.querySelector('#spinner');
    spinner.style.display = 'inline-block'; // or 'flex' if using flexbox
}

// Hide the spinner
function hideSpinner(panelId) {
    const panel = document.getElementById(`panel-${panelId}`);
    const spinner = panel.querySelector('#spinner');
    spinner.style.display = 'none';
}

// Handle AJAX errors
function handleAutoSaveError(xhr, status, panelId) {
    hideSpinner(panelId);
    // Show error message (e.g., growl)
    PF('templateGrowlVar').renderMessage({
        summary: 'Error',
        detail: 'Failed to save changes. Please try again.',
        severity: 'error'
    });
}

// --------------- TAB MENU
$(document).ready(function () {
    $(document).on('mouseenter', '.panel-menu', function () {
        $(this).addClass('panel-menu-hover');
    }).on('mouseleave', '.panel-menu', function () {
        $(this).removeClass('panel-menu-hover');
    });
});

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
function showProgressBar(widgetVar, color) {
    const progressBarValue = widgetVar.jq.find('.ui-progressbar-value');
    progressBarValue.css('background-color', color).show();
}

// Function to hide the progress bar and reset its color
function hideProgressBar(widgetVar) {
    const progressBarValue = widgetVar.jq.find('.ui-progressbar-value');
    progressBarValue.css('background-color', 'transparent').hide();
}

(function (XHR) {
    "use strict";

    var open = XHR.prototype.open;
    var send = XHR.prototype.send;

    XHR.prototype.open = function (method, url, async, user, pass) {
        this._url = url;
        open.call(this, method, url, async, user, pass);
    };

    XHR.prototype.send = function (data) {
        var self = this;
        var oldOnReadyStateChange;
        var url = this._url;

        function onReadyStateChange() {
            if (self.readyState == 4 /* complete */) {
                console.log(data)
            }

            if (oldOnReadyStateChange) {
                oldOnReadyStateChange();
            }
        }

        /* Set xhr.noIntercept to true to disable the interceptor for a particular call */
        if (!this.noIntercept) {
            if (this.addEventListener) {
                this.addEventListener("readystatechange", onReadyStateChange, false);
            } else {
                oldOnReadyStateChange = this.onreadystatechange;
                this.onreadystatechange = onReadyStateChange;
            }
        }

        send.call(this, data);
    }
})(XMLHttpRequest);

function loadPanel(t, data, s, xhr) {
    console.log("Original Response:", t, data, s, xhr);

    // Find the update for flow-panels
    const updates = data.getElementsByTagName("update");
    for (let update of updates) {
        if (update.getAttribute("id") === "flow-panels") {
            // Extract CDATA content (if any)
            const cdataNode = update.childNodes[0];
            if (cdataNode && cdataNode.nodeType === 4) { // CDATA_SECTION_NODE
                const html = cdataNode.nodeValue;

                // Parse the HTML to manipulate it
                const divParser = new DOMParser();
                const htmlDoc = divParser.parseFromString(html, "text/html");

                // Get all direct child divs
                const container = htmlDoc.body;
                const allDivs = container.querySelectorAll("div");
                if (allDivs.length > 0) {
                    // Keep only the first div
                    const firstDiv = allDivs[0];

                    // Strip all content from flow-panels in the response
                    cdataNode.nodeValue = "";

                    // Manually insert the first div into the DOM
                    const flowPanelsElement = document.getElementById("flow-panels");
                    if (flowPanelsElement) {
                        flowPanelsElement.innerHTML = firstDiv.outerHTML;
                    }
                }
            }
            // Prevent PrimeFaces from updating flow-panels (since we did it manually)
            // by removing the update element from the response
            update.parentNode.removeChild(update)
            break;
        }

    }



    // Log the modified response (optional)
    console.log("Modified Response:", data);

    // Let PrimeFaces handle the rest of the response
    return true;
}








