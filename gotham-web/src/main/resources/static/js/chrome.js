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

  function setupModePanels(root) {
    var modeGroup = root.querySelector("[data-methods]");
    if (!modeGroup) return;
    var attrBox = root.querySelector("[data-attrs]");
    var journalist = root.querySelector("[data-journalist-field]");
    var drop = root.querySelector("[data-drop]");
    var form = root.closest("form") || root;

    function sync() {
      var checked = modeGroup.querySelector('input[name="mode"]:checked');
      var mode = checked ? checked.value : "";
      var isFulltext = mode === "fulltext";
      var isVector = mode === "vector";
      if (attrBox) attrBox.classList.toggle("is-visible", isFulltext);
      if (journalist) journalist.style.display = isFulltext ? "flex" : "none";
      if (drop) drop.classList.toggle("is-visible", isVector);
      if (form && form.tagName === "FORM") {
        form.method = isVector ? "post" : "get";
        if (isVector) {
          form.enctype = "multipart/form-data";
        } else {
          form.removeAttribute("enctype");
        }
      }
    }

    modeGroup.addEventListener("change", sync);
    sync();
  }

  function init() {
    document.querySelectorAll("[data-search-panel]").forEach(setupModePanels);
    refresh();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

