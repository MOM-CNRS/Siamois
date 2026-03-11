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

function showSideview(panelId) {
    const container = document.getElementById("panel-" + panelId);
    if (container) {
        const sideview = container.querySelector("#panel-splitter");
        if (sideview) {

            // Find and show the #overview_3 element inside the splitter
            const overview3 = sideview.querySelector("#overview_3");
            if (overview3) {
                overview3.style.display = "block";
            }

            // Find and show the element with class .ui-splitter-gutter inside the splitter
            const splitterGutter = sideview.querySelector(".ui-splitter-gutter");
            if (splitterGutter) {
                splitterGutter.style.display = "block";
            }
        }
    }
}









