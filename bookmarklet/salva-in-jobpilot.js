// Bookmarklet "Salva in JobPilot".
// Come installarlo: crea un segnalibro nel browser e incolla come indirizzo il contenuto di
// salva-in-jobpilot.min.txt (generato da build.mjs). Con un annuncio LinkedIn/Indeed aperto, clicca il segnalibro:
// la pagina viene inviata a JobPilot (http://127.0.0.1:8080) che la salva come nuova candidatura.
(function () {
    var API = "http://127.0.0.1:8080/api/applications/import";

    function testo(sel) {
        var el = document.querySelector(sel);
        return el ? el.innerText.trim() : "";
    }

    var payload = {
        url: location.href,
        text: document.documentElement.outerHTML,
        title: testo("h1") || document.title
    };

    var badge = document.createElement("div");
    badge.style.cssText = "position:fixed;top:16px;right:16px;z-index:2147483647;background:#1f4e79;color:#fff;" +
        "font:14px/1.4 Segoe UI,Arial,sans-serif;padding:12px 16px;border-radius:8px;box-shadow:0 4px 16px rgba(0,0,0,.3)";
    badge.textContent = "JobPilot: invio in corso…";
    document.body.appendChild(badge);

    fetch(API, {
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Requested-With": "JobPilot" },
        body: JSON.stringify(payload)
    }).then(function (r) {
        return r.json().then(function (j) { return { ok: r.ok, status: r.status, body: j }; });
    }).then(function (res) {
        if (res.ok) {
            badge.style.background = "#2e7d32";
            badge.textContent = "JobPilot: salvata \"" + (res.body.job && res.body.job.title) + "\"";
        } else {
            badge.style.background = "#c62828";
            badge.textContent = "JobPilot: " + (res.body.message || ("errore " + res.status));
        }
    }).catch(function (e) {
        badge.style.background = "#c62828";
        badge.textContent = "JobPilot non raggiungibile: l'app è avviata? (" + e.message + ")";
    }).finally(function () {
        setTimeout(function () { badge.remove(); }, 6000);
    });
})();
