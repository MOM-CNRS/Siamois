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

function checkDesync() {
    const institutionId =
        document.getElementById("contextForm:currentInstitutionId")?.value;

    if (!institutionId) {
        location.reload(true);
        return;
    }

    fetch(APP_CTX + `/api/context/check?institutionId=${encodeURIComponent(institutionId)}`, {
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
