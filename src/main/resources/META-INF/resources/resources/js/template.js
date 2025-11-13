const sidebar = document.getElementsByClassName("sidebar")[0];
const toggleBtn = document.getElementById("toggle-btn");
const logoutForm = document.getElementById("logout-form");


function isMobile() {
    return window.matchMedia("(max-width: 768px)").matches;
}

if (toggleBtn && sidebar) {
    document.addEventListener("DOMContentLoaded", function () {
        const sidebar   = document.getElementsByClassName("sidebar")[0];
        const toggleBtn = document.getElementById("toggle-btn");
        // const logoutForm = document.getElementById("logout-form"); // pas utilisé ici

        if (toggleBtn && sidebar) {
            toggleBtn.addEventListener("click", function () {
                if (isMobile()) {
                    // En mobile : collapse + cacher
                    sidebar.classList.add("collapsed");
                    sidebar.classList.remove("open");
                } else {
                    // En desktop : juste collapse/uncollapse
                    sidebar.classList.toggle("collapsed");
                }
            });
        }
    });

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