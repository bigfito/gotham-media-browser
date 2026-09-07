/* Shared header/footer + full-text attribute field toggles */
(function () {
  const page = document.body.dataset.page || "home";

  function headerHtml() {
    return `
<header class="site-header">
  <div class="site-header__inner">
    <div class="brand">
      <a class="brand__mark" href="index.html">Gotham News <span>&amp;</span> Media Browser</a>
      <div class="brand__tag">Prototype search desk</div>
    </div>
    <nav class="header-nav" aria-label="Primary">
      <a class="nav-link${page === "home" ? " is-active" : ""}" href="index.html">Search</a>
      <a class="nav-link${page === "results-articles" ? " is-active" : ""}" href="results-articles.html">Article results</a>
      <a class="nav-link${page === "results-multimedia" ? " is-active" : ""}" href="results-multimedia.html">Media results</a>
      <a class="nav-link" href="#admin">Admin</a>
    </nav>
  </div>
</header>`;
  }

  function footerHtml() {
    return `
<footer class="site-footer">
  <div class="site-footer__inner">
    <div>Gotham News &amp; Media Browser · prototype UI mockup</div>
    <div>
      <a href="index.html">Search</a> ·
      <a href="#admin">Admin</a> ·
      Light pastel theme
    </div>
  </div>
</footer>`;
  }

  const headerMount = document.getElementById("site-header");
  const footerMount = document.getElementById("site-footer");
  if (headerMount) headerMount.outerHTML = headerHtml();
  if (footerMount) footerMount.outerHTML = footerHtml();

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
})();
