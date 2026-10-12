import { STOCK_UNIVERSE } from "../stockUniverse.js";
import { escapeHtml } from "../format.js";

let instanceCount = 0;

// Trigger + accessible popover for browsing/selecting a stock, anchored next to the symbol search
// control. STOCK_UNIVERSE is a static, hand-curated list (see stockUniverse.js) — there is no
// backend symbol-search endpoint to call, so opening/closing and typing in the name filter never
// hit the network. Mirrors signalFilterDropdown.js's structure (built-once skeleton, only the
// list's innerHTML is patched on each keystroke) so neither dropdown drops keyboard focus out of
// its own search input while the user is typing.
export function createStockPickerDropdown({ onSelect, onOpen }) {
  const panelId = `stockPickerPanel-${++instanceCount}`;
  let filterText = "";

  const trigger = document.createElement("button");
  trigger.type = "button";
  trigger.className = "search-dropdown-toggle stock-picker-toggle";
  trigger.setAttribute("aria-haspopup", "true");
  trigger.setAttribute("aria-expanded", "false");
  trigger.setAttribute("aria-controls", panelId);
  trigger.setAttribute("aria-label", "Browse stocks");
  trigger.innerHTML = `Stocks <span aria-hidden="true">&#9662;</span>`;

  const panel = document.createElement("div");
  panel.className = "stock-picker-panel";
  panel.id = panelId;
  panel.hidden = true;
  // No role="dialog": see the identical note in signalFilterDropdown.js — non-modal disclosure
  // popover, no focus trap, so dialog's implied modal semantics would overpromise.
  panel.setAttribute("aria-label", "Browse stocks");
  panel.innerHTML = `
    <div class="stock-picker-header">
      <h3>Browse stocks</h3>
      <button type="button" class="stock-picker-close" aria-label="Close">&times;</button>
    </div>
    <p class="stock-picker-scope">
      A short list of well-known symbols to get started with, searchable by ticker or company
      name — not a live search across every stock on the market. Any valid ticker still works in
      the search box above.
    </p>
    <input type="text" class="stock-picker-search" placeholder="Search symbol or company name" aria-label="Search symbol or company name" autocomplete="off">
    <div class="stock-picker-list" role="group" aria-label="Stocks"></div>
  `;

  const searchInput = panel.querySelector(".stock-picker-search");
  const listEl = panel.querySelector(".stock-picker-list");
  const closeBtn = panel.querySelector(".stock-picker-close");

  function matchingStocks() {
    const query = filterText.trim().toLowerCase();
    if (!query) return STOCK_UNIVERSE;
    return STOCK_UNIVERSE.filter(
      (stock) => stock.symbol.toLowerCase().includes(query) || stock.name.toLowerCase().includes(query)
    );
  }

  function renderList() {
    const items = matchingStocks();
    listEl.innerHTML = items.length
      ? items
          .map(
            (stock) => `
          <button type="button" class="stock-picker-item" data-symbol="${escapeHtml(stock.symbol)}">
            <span class="stock-picker-item-symbol">${escapeHtml(stock.symbol)}</span>
            <span class="stock-picker-item-name">${escapeHtml(stock.name)}</span>
          </button>`
          )
          .join("")
      : `<p class="section-status">No stocks match &ldquo;${escapeHtml(filterText)}&rdquo;.</p>`;

    listEl.querySelectorAll(".stock-picker-item").forEach((button) => {
      button.addEventListener("click", () => {
        const symbol = button.dataset.symbol;
        setOpen(false);
        onSelect(symbol);
        trigger.focus();
      });
    });
  }

  function isOpen() {
    return !panel.hidden;
  }

  function setOpen(nextOpen) {
    if (nextOpen === isOpen()) return;
    panel.hidden = !nextOpen;
    trigger.setAttribute("aria-expanded", String(nextOpen));

    if (nextOpen) {
      // Explicit mutual exclusion rather than relying on the other dropdown's own
      // outside-click handling to catch this click — correct for a real mouse/keyboard click
      // (both fire a genuine bubbling "click" the other's capture-phase listener would see), but
      // making the invariant explicit here means it keeps holding even if a trigger is ever
      // swapped for something that isn't a native <button>.
      onOpen?.();
      filterText = "";
      searchInput.value = "";
      renderList();
      document.addEventListener("keydown", onDocumentKeydown);
      document.addEventListener("click", onDocumentClick, true);
      searchInput.focus();
    } else {
      document.removeEventListener("keydown", onDocumentKeydown);
      document.removeEventListener("click", onDocumentClick, true);
    }
  }

  function onDocumentKeydown(event) {
    if (event.key === "Escape") {
      setOpen(false);
      trigger.focus();
    }
  }

  function onDocumentClick(event) {
    if (panel.contains(event.target) || trigger.contains(event.target)) return;
    setOpen(false);
  }

  trigger.addEventListener("click", () => setOpen(!isOpen()));
  closeBtn.addEventListener("click", () => {
    setOpen(false);
    trigger.focus();
  });

  searchInput.addEventListener("input", () => {
    filterText = searchInput.value;
    renderList();
  });

  return { trigger, panel, close: () => setOpen(false) };
}
