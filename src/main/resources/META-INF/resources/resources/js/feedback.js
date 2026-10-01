/* Floating feedback button: lets the user select a region of the current page,
 * capture it as an image, then attach a description (and optional identity) and send it to the server. */

let siaFeedbackImageData = null;

function siaFeedbackStartCapture() {
    const btn = document.getElementById('siaFeedbackButton');
    if (btn) btn.style.display = 'none';

    const overlay = document.createElement('div');
    overlay.id = 'siaFeedbackOverlay';
    overlay.style.position = 'fixed';
    overlay.style.top = '0';
    overlay.style.left = '0';
    overlay.style.right = '0';
    overlay.style.bottom = '0';
    overlay.style.zIndex = '99999';
    overlay.style.cursor = 'crosshair';
    overlay.style.background = 'rgba(0,0,0,0.15)';

    const hint = document.createElement('div');
    hint.textContent = 'Dessinez un rectangle pour capturer une zone — Échap pour annuler';
    hint.style.position = 'fixed';
    hint.style.top = '1rem';
    hint.style.left = '50%';
    hint.style.transform = 'translateX(-50%)';
    hint.style.background = '#212529';
    hint.style.color = '#fff';
    hint.style.padding = '0.4rem 1rem';
    hint.style.borderRadius = '6px';
    hint.style.fontSize = '0.9rem';
    hint.style.pointerEvents = 'none';
    overlay.appendChild(hint);

    const sel = document.createElement('div');
    sel.id = 'siaFeedbackSelection';
    sel.style.position = 'fixed';
    sel.style.border = '2px dashed #2e7d32';
    sel.style.background = 'rgba(46,125,50,0.15)';
    sel.style.display = 'none';
    overlay.appendChild(sel);

    document.body.appendChild(overlay);

    let startX = 0, startY = 0, dragging = false;

    function onMouseDown(e) {
        dragging = true;
        startX = e.clientX;
        startY = e.clientY;
        sel.style.left = startX + 'px';
        sel.style.top = startY + 'px';
        sel.style.width = '0px';
        sel.style.height = '0px';
        sel.style.display = 'block';
    }

    function onMouseMove(e) {
        if (!dragging) return;
        const x = Math.min(e.clientX, startX);
        const y = Math.min(e.clientY, startY);
        const w = Math.abs(e.clientX - startX);
        const h = Math.abs(e.clientY - startY);
        sel.style.left = x + 'px';
        sel.style.top = y + 'px';
        sel.style.width = w + 'px';
        sel.style.height = h + 'px';
    }

    function cleanup() {
        overlay.removeEventListener('mousedown', onMouseDown);
        overlay.removeEventListener('mousemove', onMouseMove);
        overlay.removeEventListener('mouseup', onMouseUp);
        document.removeEventListener('keydown', onKeyDown);
        if (overlay.parentNode) overlay.parentNode.removeChild(overlay);
        if (btn) btn.style.display = '';
    }

    function onMouseUp() {
        dragging = false;
        const rect = sel.getBoundingClientRect();
        cleanup();
        if (rect.width < 5 || rect.height < 5) return;
        siaFeedbackCaptureRegion(rect);
    }

    function onKeyDown(e) {
        if (e.key === 'Escape') cleanup();
    }

    overlay.addEventListener('mousedown', onMouseDown);
    overlay.addEventListener('mousemove', onMouseMove);
    overlay.addEventListener('mouseup', onMouseUp);
    document.addEventListener('keydown', onKeyDown);
}

function siaFeedbackCaptureRegion(rect) {
    if (typeof html2canvas !== 'function') {
        console.error('Feedback: html2canvas library failed to load');
        alert('La capture d\'écran a échoué (bibliothèque non chargée). Veuillez réessayer ou recharger la page.');
        return;
    }

    html2canvas(document.body, {
        x: rect.left + window.scrollX,
        y: rect.top + window.scrollY,
        width: rect.width,
        height: rect.height,
        windowWidth: document.documentElement.scrollWidth,
        windowHeight: document.documentElement.scrollHeight,
        useCORS: true
    }).then(function (canvas) {
        siaFeedbackImageData = canvas.toDataURL('image/png');
        PF('feedbackCreateDiag').show();
    }).catch(function (err) {
        console.error('Feedback capture failed', err);
        alert('La capture d\'écran a échoué. Veuillez réessayer.');
    });
}

function siaFeedbackSubmit() {
    const nameInput = document.getElementById('feedbackCreateForm:feedbackNameInput');
    const descriptionInput = document.getElementById('feedbackCreateForm:feedbackDescriptionInput');
    const description = descriptionInput ? descriptionInput.value.trim() : '';

    if (!description) {
        descriptionInput.focus();
        return;
    }
    if (!siaFeedbackImageData) {
        alert('Aucune capture d\'écran n\'est associée à ce retour. Veuillez recommencer.');
        return;
    }

    const sendBtn = document.getElementById('feedbackSendBtn');
    const cancelBtn = document.getElementById('feedbackCancelBtn');
    if (sendBtn) {
        sendBtn.disabled = true;
        sendBtn.innerHTML = '<i class="bi bi-arrow-repeat sia-feedback-spin" style="margin-right:0.4em;"></i>Envoi en cours...';
    }
    if (cancelBtn) cancelBtn.disabled = true;

    siaSubmitFeedbackRemote([
        {name: 'feedbackName', value: nameInput ? nameInput.value.trim() : ''},
        {name: 'feedbackDescription', value: description},
        {name: 'feedbackImage', value: siaFeedbackImageData},
        {name: 'feedbackPageUrl', value: window.location.href}
    ]);
}

/* Called once the save ajax call completes (success or failure) — the growl has already been
 * updated by then, so closing the dialog now reveals the result immediately, no dead air. */
function siaFeedbackOnSendComplete() {
    const sendBtn = document.getElementById('feedbackSendBtn');
    const cancelBtn = document.getElementById('feedbackCancelBtn');
    if (sendBtn) {
        sendBtn.disabled = false;
        sendBtn.innerHTML = '<i class="bi bi-send" style="margin-right:0.4em;"></i>Envoyer';
    }
    if (cancelBtn) cancelBtn.disabled = false;

    siaFeedbackImageData = null;
    PF('feedbackCreateDiag').hide();
}

function siaFeedbackOpenList() {
    if (typeof SIA_FEEDBACK_ISSUES_URL === 'string' && SIA_FEEDBACK_ISSUES_URL) {
        window.open(SIA_FEEDBACK_ISSUES_URL, '_blank');
    }
}
