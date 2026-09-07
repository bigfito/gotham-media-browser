/* Shared header + footer + multi-term full-text helpers */
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
      Pastel light theme
    </div>
  </div>
</footer>`;
  }

  const headerMount = document.getElementById("site-header");
  const footerMount = document.getElementById("site-footer");
  if (headerMount) headerMount.outerHTML = headerHtml();
  if (footerMount) footerMount.outerHTML = footerHtml();

  function setupTerms(root) {
    if (!root) return;
    const modeGroup = root.querySelector("[data-methods]");
    const termsBox = root.querySelector("[data-terms]");
    const addInput = root.querySelector("[data-term-input]");
    const addBtn = root.querySelector("[data-term-add]");
    const list = root.querySelector("[data-term-list]");
    const hiddenQ = root.querySelector("[data-terms-query]");

    if (!modeGroup || !termsBox || !list) return;

    function selectedTerms() {
      return [...list.querySelectorAll('input[type="checkbox"]:checked')].map((el) => el.value);
    }

    function syncQuery() {
      if (hiddenQ) hiddenQ.value = selectedTerms().join(" ");
    }

    function syncMode() {
      const mode = modeGroup.querySelector('input[name="mode"]:checked')?.value;
      const isFulltext = mode === "fulltext";
      termsBox.classList.toggle("is-visible", isFulltext);
      const journalist = root.querySelector("[data-journalist-field]");
      if (journalist) journalist.style.display = isFulltext ? "flex" : "none";
      const queryField = root.querySelector("[data-single-query]");
      if (queryField) queryField.style.display = isFulltext ? "none" : "flex";
    }

    function addTerm(raw) {
      const term = (raw || "").trim();
      if (!term) return;
      const exists = [...list.querySelectorAll("input")].some(
        (el) => el.value.toLowerCase() === term.toLowerCase()
      );
      if (exists) return;
      const id = `term-${Math.random().toString(36).slice(2, 9)}`;
      const li = document.createElement("li");
      li.innerHTML = `<label for="${id}"><input id="${id}" type="checkbox" name="term" value="${term.replace(/"/g, "&quot;")}" checked /> <span>${term}</span></label>`;
      list.appendChild(li);
      if (addInput) addInput.value = "";
      syncQuery();
    }

    modeGroup.addEventListener("change", syncMode);
    list.addEventListener("change", syncQuery);
    if (addBtn && addInput) {
      addBtn.addEventListener("click", (e) => {
        e.preventDefault();
        addTerm(addInput.value);
      });
      addInput.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
          e.preventDefault();
          addTerm(addInput.value);
        }
      });
    }

    // seed demo terms if empty and data-seed present
    const seed = root.getAttribute("data-seed-terms");
    if (seed && list.children.length === 0) {
      seed.split("|").forEach(addTerm);
    }

    syncMode();
    syncQuery();
  }

  document.querySelectorAll("[data-search-panel]").forEach(setupTerms);

  // multimedia vector dropzone
  document.querySelectorAll("[data-media-panel]").forEach((root) => {
    const modeGroup = root.querySelector("[data-methods]");
    const drop = root.querySelector("[data-drop]");
    const form = root.closest("form") || root;
    if (!modeGroup || !drop) return;
    function sync() {
      const mode = modeGroup.querySelector('input[name="mode"]:checked')?.value;
      const isVector = mode === "vector";
      drop.classList.toggle("is-visible", isVector);
      if (form && form.tagName === "FORM") form.method = isVector ? "post" : "get";
    }
    modeGroup.addEventListener("change", sync);
    sync();
  });
})();
