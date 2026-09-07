/* Shared header/footer + full-text attribute field toggles */
(function () {
  const page = document.body.dataset.page || "home";

  /** @returns {"up"|"down"|"checking"} */
  function initialStatus(key) {
    const raw = (document.body.dataset[key] || "").toLowerCase();
    if (raw === "up" || raw === "available" || raw === "true") return "up";
    if (raw === "down" || raw === "unavailable" || raw === "false") return "down";
    return "checking";
  }

  function statusLegend(id, label, state) {
    const text =
      state === "up" ? "Available" :
      state === "down" ? "Unavailable" :
      "Checking…";
    return `
<span class="service-status service-status--${state}" id="${id}" role="status" aria-live="polite">
  <span class="service-status__dot" aria-hidden="true"></span>
  <span class="service-status__label">${label}</span>
  <span class="service-status__state">${text}</span>
</span>`;
  }

  function headerHtml(imagebindState, elasticsearchState) {
    return `
<header class="site-header">
  <div class="site-header__inner">
    <div class="brand">
      <a class="brand__mark" href="index.html">Gotham News <span>&amp;</span> Media Browser</a>
      <div class="brand__tag">Prototype search desk</div>
    </div>
    <div class="header-aside">
      <div class="service-legends" aria-label="Dependency availability">
        ${statusLegend("status-imagebind", "ImageBind", imagebindState)}
        ${statusLegend("status-elasticsearch", "Elasticsearch", elasticsearchState)}
      </div>
      <nav class="header-nav" aria-label="Primary">
        <a class="nav-link${page === "home" ? " is-active" : ""}" href="index.html">Search</a>
        <a class="nav-link${page === "results-articles" ? " is-active" : ""}" href="results-articles.html">Article results</a>
        <a class="nav-link${page === "results-multimedia" ? " is-active" : ""}" href="results-multimedia.html">Media results</a>
        <a class="nav-link${page === "journalist" ? " is-active" : ""}" href="journalist.html">Journalists</a>
        <a class="nav-link${page === "article" ? " is-active" : ""}" href="article.html">Articles</a>
      </nav>
    </div>
  </div>
</header>`;
  }

  function footerHtml() {
    return `
<footer class="site-footer">
  <div class="site-footer__inner">
    <div class="site-footer__legal">
      <p class="site-footer__copy">
        Copyright &copy; 2020 Packt.
        Copyright &copy; 2026 Gotham News &amp; Media Browser contributors.
        All rights reserved where applicable.
      </p>
      <p class="site-footer__license">
        This project is licensed under the
        <a href="https://opensource.org/licenses/MIT" rel="license">MIT License</a>
        (see repository <code>LICENSE</code>).
        Year <time datetime="2026">2026</time>.
      </p>
    </div>
    <div class="site-footer__nav">
      <a href="index.html">Search</a> ·
      <a href="journalist.html">Journalists</a> ·
      <a href="article.html">Articles</a> ·
      <a href="https://opensource.org/licenses/MIT" rel="license">MIT</a>
    </div>
  </div>
</footer>`;
  }

  function setStatus(el, state) {
    if (!el) return;
    el.classList.remove("service-status--up", "service-status--down", "service-status--checking");
    el.classList.add(`service-status--${state}`);
    const stateEl = el.querySelector(".service-status__state");
    if (stateEl) {
      stateEl.textContent =
        state === "up" ? "Available" :
        state === "down" ? "Unavailable" :
        "Checking…";
    }
  }

  async function probe(url, timeoutMs) {
    const ctrl = new AbortController();
    const timer = setTimeout(() => ctrl.abort(), timeoutMs);
    try {
      const res = await fetch(url, { method: "GET", signal: ctrl.signal, cache: "no-store" });
      return res.ok;
    } catch (_) {
      return false;
    } finally {
      clearTimeout(timer);
    }
  }

  async function refreshServiceStatuses() {
    const imagebindEl = document.getElementById("status-imagebind");
    const esEl = document.getElementById("status-elasticsearch");

    const forcedIb = initialStatus("imagebindStatus");
    const forcedEs = initialStatus("elasticsearchStatus");

    /* Body data-* wins when explicitly set (mock / Thymeleaf injection). */
    if (forcedIb !== "checking") setStatus(imagebindEl, forcedIb);
    else {
      setStatus(imagebindEl, "checking");
      const ok = await probe("http://127.0.0.1:8081/health", 1200);
      setStatus(imagebindEl, ok ? "up" : "down");
    }

    if (forcedEs !== "checking") setStatus(esEl, forcedEs);
    else {
      setStatus(esEl, "checking");
      /* Same-origin health bridge the Spring app will expose later. */
      const ok = await probe("/api/health/elasticsearch", 1200);
      setStatus(esEl, ok ? "up" : "down");
    }
  }

  const headerMount = document.getElementById("site-header");
  const footerMount = document.getElementById("site-footer");
  if (headerMount) {
    headerMount.outerHTML = headerHtml(
      initialStatus("imagebindStatus") === "checking" ? "checking" : initialStatus("imagebindStatus"),
      initialStatus("elasticsearchStatus") === "checking" ? "checking" : initialStatus("elasticsearchStatus")
    );
  }
  if (footerMount) footerMount.outerHTML = footerHtml();
  refreshServiceStatuses();

  function setupModePanels(root) {
    const modeGroup = root.querySelector("[data-methods]");
    if (!modeGroup) return;
    const attrBox = root.querySelector("[data-attrs]");
    const journalist = root.querySelector("[data-journalist-field]");
    const drop = root.querySelector("[data-drop]");
    const form = root.closest("form") || root;

    function sync() {
      const mode = modeGroup.querySelector('input[name="mode"]:checked')?.value;
      const isFulltext = mode === "fulltext";
      const isVector = mode === "vector";
      if (attrBox) attrBox.classList.toggle("is-visible", isFulltext);
      if (journalist) journalist.style.display = isFulltext ? "flex" : "none";
      if (drop) drop.classList.toggle("is-visible", isVector);
      if (form && form.tagName === "FORM") form.method = isVector ? "post" : "get";
    }

    modeGroup.addEventListener("change", sync);
    sync();
  }

  document.querySelectorAll("[data-search-panel]").forEach(setupModePanels);

  /* Keep pagination mock labels in sync with size ∈ {25,50,100} → ES from/size */
  const sizeSelect = document.getElementById("size");
  const esHint = document.querySelector(".results-meta__es");
  const showing = document.querySelector("[data-showing-range]");
  if (sizeSelect && (esHint || showing)) {
    function syncSize() {
      const size = Number(sizeSelect.value);
      if (![25, 50, 100].includes(size)) return;
      const page = 1;
      const from = (page - 1) * size;
      const end = size; /* mock first page */
      if (esHint) {
        esHint.innerHTML = `ES <code>from=${from}</code> · <code>size=${size}</code> · <code>track_total_hits</code>`;
      }
      if (showing) showing.textContent = `1–${end}`;
      const links = document.querySelectorAll(".pagination__pages a");
      links.forEach((a) => {
        try {
          const url = new URL(a.getAttribute("href"), window.location.href);
          url.searchParams.set("size", String(size));
          a.setAttribute("href", url.pathname + url.search);
        } catch (_) { /* ignore */ }
      });
    }
    sizeSelect.addEventListener("change", syncSize);
  }
})();
