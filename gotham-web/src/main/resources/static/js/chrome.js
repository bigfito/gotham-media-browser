/*
 * Header service legends: probe the same-origin health bridges and flip the
 * ImageBind / Elasticsearch legends between Checking… / Available / Unavailable.
 * The markup is server-rendered by fragments/chrome.html; this only updates state.
 */
(function () {
  function setStatus(el, state) {
    if (!el) return;
    el.classList.remove("service-status--up", "service-status--down", "service-status--checking");
    el.classList.add("service-status--" + state);
    var label = el.querySelector(".service-status__state");
    if (label) {
      label.textContent =
        state === "up" ? "Available" :
        state === "down" ? "Unavailable" :
        "Checking…";
    }
  }

  async function probe(url) {
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, 2500);
    try {
      var res = await fetch(url, { method: "GET", signal: controller.signal, cache: "no-store" });
      return res.ok;
    } catch (_) {
      return false;
    } finally {
      clearTimeout(timer);
    }
  }

  async function refresh() {
    var imagebind = document.getElementById("status-imagebind");
    var elasticsearch = document.getElementById("status-elasticsearch");
    setStatus(imagebind, "checking");
    setStatus(elasticsearch, "checking");

    var results = await Promise.all([
      probe("/api/health/imagebind"),
      probe("/api/health/elasticsearch")
    ]);
    setStatus(imagebind, results[0] ? "up" : "down");
    setStatus(elasticsearch, results[1] ? "up" : "down");
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", refresh);
  } else {
    refresh();
  }
})();
