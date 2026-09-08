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

  /**
   * Opens the original IMAGE or VIDEO bytes in a child browser window sized to
   * the stored pixel dimensions (scrollbars if the file is larger than the screen).
   */
  function openOriginalMedia(url, width, height, kind) {
    if (!url) {
      return;
    }
    var isVideo = kind === "VIDEO";
    var availW = window.screen.availWidth || 1200;
    var availH = window.screen.availHeight || 800;
    var mediaW = parseInt(width, 10);
    var mediaH = parseInt(height, 10);
    var winW = mediaW > 0 ? mediaW : (isVideo ? 854 : 800);
    var winH = mediaH > 0 ? mediaH : (isVideo ? 480 : 600);
    winW = Math.min(Math.max(winW, 200), availW);
    winH = Math.min(Math.max(winH, 200), availH);
    var windowName = isVideo ? "gothamOriginalVideo" : "gothamOriginalImage";
    var features = "popup=yes,width=" + winW + ",height=" + winH + ",resizable=yes,scrollbars=yes";
    var child = window.open("", windowName, features);
    if (!child) {
      window.open(url, windowName);
      return;
    }
    child.opener = null;
    var safeUrl = String(url)
        .replace(/&/g, "&amp;")
        .replace(/"/g, "&quot;")
        .replace(/</g, "&lt;");
    var player = isVideo
        ? "<video src=\"" + safeUrl + "\" controls=\"controls\" autoplay=\"autoplay\" playsinline=\"playsinline\" style=\"display:block;width:auto;height:auto;max-width:none;\"></video>"
        : "<img src=\"" + safeUrl + "\" alt=\"\">";
    var title = isVideo ? "Original video" : "Original image";
    child.document.open();
    child.document.write(
        "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + title + "</title>" +
        "<style>html,body{margin:0;background:#111;}img,video{display:block;width:auto;height:auto;max-width:none;}</style>" +
        "</head><body>" + player + "</body></html>");
    child.document.close();
    child.focus();
  }

  function bindOriginalMediaLinks(root) {
    root.addEventListener("click", function (event) {
      var link = event.target.closest("a.media-card__original");
      if (!link) {
        return;
      }
      event.preventDefault();
      openOriginalMedia(
          link.href,
          link.getAttribute("data-width"),
          link.getAttribute("data-height"),
          link.getAttribute("data-kind"));
    });
  }

  window.GothamMedia = {
    openOriginalMedia: openOriginalMedia,
    openOriginalImage: function (url, width, height) {
      openOriginalMedia(url, width, height, "IMAGE");
    }
  };

  function init() {
    document.querySelectorAll("[data-search-panel]").forEach(setupModePanels);
    bindOriginalMediaLinks(document);
    refresh();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();

