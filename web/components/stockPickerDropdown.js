import { STOCK_UNIVERSE } from "../stockUniverse.js";
import { FOREX_UNIVERSE } from "../forexUniverse.js";
import { escapeHtml } from "../format.js";

let instanceCount = 0;

const ASSET_CLASSES = [
  { key: "STOCK", label: "Stocks", universe: STOCK_UNIVERSE, scope: "A short list of well-known symbols to get started with, searchable by ticker or company name — not a live search across every stock on the market. Any valid ticker still works in the search box above." },
  { key: "FOREX", label: "Forex", universe: FOREX_UNIVERSE, scope: "A short list of major currency pairs to get started with, searchable by pair or name — not every pair Massive may have data for. Any correctly formatted pair (e.g. EUR/USD) still works in the search box above." },
];

// Trigger + accessible popover for browsing/selecting an instrument, anchored next to the symbol
// search control. STOCK_UNIVERSE/FOREX_UNIVERSE are static, hand-curated lists (see
// stockUniverse.js/forexUniverse.js) — there is no backend symbol-search endpoint to call, so
// opening/closing, switching the asset-class tab, and typing in the name filter never hit the
// network. Mirrors signalFilterDropdown.js's structure (built-once skeleton, only the list's
// innerHTML is patched on each keystroke/tab switch) so neither dropdown drops keyboard focus out
// of its own search input while the user is typing.
export function createStockPickerDropdown({ onSelect, onOpen }) {
  const panelId = `stockPickerPanel-${++instanceCount}`;
  let filterText = "";
  let activeClassKey = ASSET_CLASSES[0].key;

  const trigger = document.createElement("button");
  trigger.type = "button";
  trigger.className = "search-dropdown-toggle stock-picker-toggle";
  trigger.setAttribute("aria-haspopup", "true");
  trigger.setAttribute("aria-expanded", "false");
  trigger.setAttribute("aria-controls", panelId);
  trigger.setAttribute("aria-label", "Browse stocks or forex pairs");
  trigger.innerHTML = `Browse <span aria-hidden="true">&#9662;</span>`;

  const panel = document.createElement("div");
  panel.className = "stock-picker-panel";
  panel.id = panelId;
  panel.hidden = true;
  // No role="dialog": see the identical note in signalFilterDropdown.js — non-modal disclosure
  // popover, no focus trap, so dialog's implied modal semantics would overpromise.
  panel.setAttribute("aria-label", "Browse stocks or forex pairs");
  panel.innerHTML = `
    <div class="stock-picker-header">
      <h3>Browse instruments</h3>
      <button type="button" class="stock-picker-close" aria-label="Close">&times;</button>
    </div>
    <div class="stock-picker-tabs" role="tablist" aria-label="Asset class">
      ${ASSET_CLASSES.map(
        (cls) => `<button type="button" class="stock-picker-tab" role="tab" data-class="${cls.key}">${escapeHtml(cls.label)}</button>`
      ).join("")}
    </div>
    <p class="stock-picker-scope"></p>
    <input type="text" class="stock-picker-search" placeholder="Search symbol or name" aria-label="Search symbol or name" autocomplete="off">
    <div class="stock-picker-list" role="group" aria-label="Instruments"></div>
  `;

  const scopeEl = panel.querySelector(".stock-picker-scope");
  const tabsEl = panel.querySelector(".stock-picker-tabs");
  const searchInput = panel.querySelector(".stock-picker-search");
  const listEl = panel.querySelector(".stock-picker-list");
  const closeBtn = panel.querySelector(".stock-picker-close");

  function activeClass() {
    return ASSET_CLASSES.find((cls) => cls.key === activeClassKey);
  }

  function matchingItems() {
    const universe = activeClass().universe;
    const query = filterText.trim().toLowerCase();
    if (!query) return universe;
    return universe.filter(
      (item) => item.symbol.toLowerCase().includes(query) || item.name.toLowerCase().includes(query)
    );
  }

  function renderTabs() {
    tabsEl.querySelectorAll(".stock-picker-tab").forEach((btn) => {
      const isActive = btn.dataset.class === activeClassKey;
      btn.classList.toggle("active", isActive);
      btn.setAttribute("aria-selected", String(isActive));
    });
  }

  function renderList() {
    scopeEl.textContent = activeClass().scope;
    const items = matchingItems();
    listEl.innerHTML = items.length
      ? items
          .map(
            (item) => `
          <button type="button" class="stock-picker-item" data-symbol="${escapeHtml(item.symbol)}">
            <span class="stock-picker-item-symbol">${escapeHtml(item.symbol)}</span>
            <span class="stock-picker-item-name">${escapeHtml(item.name)}</span>
          </button>`
          )
          .join("")
      : `<p class="section-status">No matches for &ldquo;${escapeHtml(filterText)}&rdquo;.</p>`;

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
      renderTabs();
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

  tabsEl.querySelectorAll(".stock-picker-tab").forEach((btn) => {
    btn.addEventListener("click", () => {
      if (btn.dataset.class === activeClassKey) return;
      activeClassKey = btn.dataset.class;
      filterText = "";
      searchInput.value = "";
      renderTabs();
      renderList();
      searchInput.focus();
    });
  });

  searchInput.addEventListener("input", () => {
    filterText = searchInput.value;
    renderList();
  });

  return { trigger, panel, close: () => setOpen(false) };
}
