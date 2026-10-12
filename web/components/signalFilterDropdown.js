import { SIGNAL_CATALOGUE } from "../signalCatalogue.js";
import { getCurrentSymbol, getSelectedSignalTypes, setSelectedSignalTypes } from "../state.js";
import { navigate } from "../router.js";
import { escapeHtml } from "../format.js";

let instanceCount = 0;

// Chevron trigger + accessible popover anchored to the stock search control. The catalogue is
// static (see signalCatalogue.js) so there is no network call to make here — opening/closing and
// typing in the name filter never hit the backend. The panel's own search input, checkboxes, and
// buttons are built once and only their inner lists/attributes are patched afterward, specifically
// so re-filtering by name or toggling a checkbox never recreates the input/checkbox the user is
// actively focused on (a full innerHTML re-render would drop keyboard focus mid-interaction).
export function createSignalFilterDropdown({ onOpen } = {}) {
  const panelId = `signalFilterPanel-${++instanceCount}`;
  let pendingSelected = new Set(getSelectedSignalTypes());
  let filterText = "";

  const trigger = document.createElement("button");
  trigger.type = "button";
  // Text, not a bare chevron: once the stock picker dropdown sits right next to this one (see
  // stockPickerDropdown.js), two identical unlabeled chevrons would be visually indistinguishable
  // — exactly what "the stock dropdown and signal filter must not be confused" rules out.
  trigger.className = "search-dropdown-toggle signal-filter-toggle";
  trigger.setAttribute("aria-haspopup", "true");
  trigger.setAttribute("aria-expanded", "false");
  trigger.setAttribute("aria-controls", panelId);
  trigger.setAttribute("aria-label", "Filter by signals");
  trigger.innerHTML = `Filters <span aria-hidden="true">&#9662;</span>`;

  const panel = document.createElement("div");
  panel.className = "signal-filter-panel";
  panel.id = panelId;
  panel.hidden = true;
  // No role="dialog": this is a non-modal disclosure popover (aria-haspopup/aria-expanded on the
  // trigger already describe that relationship) with no focus trap, so dialog's implied modal
  // semantics would overpromise what's actually implemented.
  panel.setAttribute("aria-label", "Filter by signals");
  panel.innerHTML = `
    <div class="signal-filter-header">
      <h3>Filter by signals</h3>
      <button type="button" class="signal-filter-close" aria-label="Close">&times;</button>
    </div>
    <p class="signal-filter-scope">
      Shows these conditions for the currently selected symbol. Orynix does not search other
      stocks for matches.
    </p>
    <input type="text" class="signal-filter-search" placeholder="Search signal names" aria-label="Search signal names" autocomplete="off">
    <div class="signal-filter-list" role="group" aria-label="Signal types"></div>
    <div class="signal-filter-actions">
      <button type="button" class="signal-filter-clear">Clear filters</button>
      <button type="button" class="signal-filter-apply">Apply</button>
    </div>
    <div class="signal-filter-goto-slot"></div>
  `;

  const searchInput = panel.querySelector(".signal-filter-search");
  const listEl = panel.querySelector(".signal-filter-list");
  const clearBtn = panel.querySelector(".signal-filter-clear");
  const applyBtn = panel.querySelector(".signal-filter-apply");
  const closeBtn = panel.querySelector(".signal-filter-close");
  const gotoSlot = panel.querySelector(".signal-filter-goto-slot");

  function matchingCatalogue() {
    const query = filterText.trim().toLowerCase();
    if (!query) return SIGNAL_CATALOGUE;
    return SIGNAL_CATALOGUE.filter(
      (signal) => signal.label.toLowerCase().includes(query) || signal.type.toLowerCase().includes(query)
    );
  }

  function renderList() {
    const items = matchingCatalogue();
    listEl.innerHTML = items.length
      ? items
          .map(
            (signal) => `
          <label class="signal-filter-item">
            <input type="checkbox" value="${signal.type}" ${pendingSelected.has(signal.type) ? "checked" : ""}>
            <span class="signal-filter-item-text">
              <span class="signal-filter-item-label">${escapeHtml(signal.label)}</span>
              <span class="signal-filter-item-desc">${escapeHtml(signal.description)}</span>
            </span>
          </label>`
          )
          .join("")
      : `<p class="section-status">No signal types match &ldquo;${escapeHtml(filterText)}&rdquo;.</p>`;

    listEl.querySelectorAll('input[type="checkbox"]').forEach((checkbox) => {
      checkbox.addEventListener("change", () => {
        if (checkbox.checked) {
          pendingSelected.add(checkbox.value);
        } else {
          pendingSelected.delete(checkbox.value);
        }
        syncClearButton();
      });
    });
  }

  function syncClearButton() {
    clearBtn.disabled = pendingSelected.size === 0 && getSelectedSignalTypes().length === 0;
  }

  function renderGoto() {
    const symbol = getCurrentSymbol();
    gotoSlot.innerHTML = symbol
      ? `<a href="#" class="signal-filter-goto">View signals for ${escapeHtml(symbol)} &rarr;</a>`
      : `<p class="section-status">Search for a symbol to view its signals.</p>`;

    const link = gotoSlot.querySelector(".signal-filter-goto");
    if (link) {
      link.addEventListener("click", (event) => {
        event.preventDefault();
        setOpen(false);
        navigate("signals", { symbol });
      });
    }
  }

  function isOpen() {
    return !panel.hidden;
  }

  function setOpen(nextOpen) {
    if (nextOpen === isOpen()) return;
    panel.hidden = !nextOpen;
    trigger.setAttribute("aria-expanded", String(nextOpen));

    if (nextOpen) {
      // See the matching comment in stockPickerDropdown.js: explicit mutual exclusion with the
      // sibling dropdown, not left to implicit click-event-phase ordering.
      onOpen?.();
      // Re-sync to the last *applied* selection, not whatever was left pending from a previous
      // close-without-apply — closing the panel without clicking Apply discards pending edits.
      pendingSelected = new Set(getSelectedSignalTypes());
      filterText = "";
      searchInput.value = "";
      renderList();
      renderGoto();
      syncClearButton();
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

  clearBtn.addEventListener("click", () => {
    pendingSelected = new Set();
    setSelectedSignalTypes([]);
    renderList();
    syncClearButton();
  });

  applyBtn.addEventListener("click", () => {
    setSelectedSignalTypes(Array.from(pendingSelected));
    setOpen(false);
    trigger.focus();
  });

  return { trigger, panel, close: () => setOpen(false) };
}
