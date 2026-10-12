import { isValidSymbol } from "../format.js";
import { createSignalFilterDropdown } from "./signalFilterDropdown.js";
import { createStockPickerDropdown } from "./stockPickerDropdown.js";

export function createSymbolSearch({
  initialValue = "",
  placeholder = "Symbol, e.g. AAPL",
  onSubmit,
  withSignalFilter = false,
  withStockPicker = false,
}) {
  const wrapper = document.createElement("div");
  wrapper.className = "symbol-search-wrapper";

  const form = document.createElement("form");
  form.className = "symbol-search-form";
  form.innerHTML = `
    <input type="text" class="symbol-search-input" placeholder="${placeholder}" autocomplete="off" value="${initialValue}">
    <button type="submit" class="symbol-search-btn">Search</button>
    <span class="symbol-search-error" hidden></span>
  `;

  const input = form.querySelector(".symbol-search-input");
  const errorEl = form.querySelector(".symbol-search-error");

  // The one path that actually submits a symbol, shared by the text form and the stock picker's
  // selection — picking "AAPL" from the list and typing "AAPL" then hitting Search must behave
  // identically, not run two parallel implementations of "select a symbol."
  function submitSymbol(rawSymbol) {
    const symbol = rawSymbol.trim().toUpperCase();
    if (!isValidSymbol(symbol)) {
      errorEl.textContent = "Enter a valid symbol (letters/periods, up to 10 characters).";
      errorEl.hidden = false;
      return;
    }
    errorEl.hidden = true;
    input.value = symbol;
    onSubmit(symbol);
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    submitSymbol(input.value);
  });

  input.addEventListener("input", () => {
    errorEl.hidden = true;
  });

  wrapper.appendChild(form);

  // The dropdown triggers/panels below are siblings of <form>, never children of it — a
  // dropdown-internal <input> living inside the same <form> as the symbol search would risk an
  // implicit Enter-key submit of the symbol search, which must stay a fully separate action (see
  // CLAUDE.md).
  let stockPicker = null;
  let signalFilter = null;

  if (withStockPicker) {
    // onOpen closes the sibling dropdown explicitly, rather than relying on the other panel's own
    // outside-click handling to catch it — correct either way for a real click, but explicit here
    // means "only one of these two is ever open" holds regardless of how either is triggered.
    stockPicker = createStockPickerDropdown({ onSelect: submitSymbol, onOpen: () => signalFilter?.close() });
    wrapper.appendChild(stockPicker.trigger);
    wrapper.appendChild(stockPicker.panel);
  }

  if (withSignalFilter) {
    signalFilter = createSignalFilterDropdown({ onOpen: () => stockPicker?.close() });
    wrapper.appendChild(signalFilter.trigger);
    wrapper.appendChild(signalFilter.panel);
  }

  return {
    element: wrapper,
    setValue(value) {
      input.value = value || "";
    },
  };
}
