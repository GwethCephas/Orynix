import { isValidSymbol } from "../format.js";

export function createSymbolSearch({ initialValue = "", placeholder = "Symbol, e.g. AAPL", onSubmit }) {
  const form = document.createElement("form");
  form.className = "symbol-search-form";
  form.innerHTML = `
    <input type="text" class="symbol-search-input" placeholder="${placeholder}" autocomplete="off" value="${initialValue}">
    <button type="submit" class="symbol-search-btn">Search</button>
    <span class="symbol-search-error" hidden></span>
  `;

  const input = form.querySelector(".symbol-search-input");
  const errorEl = form.querySelector(".symbol-search-error");

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const symbol = input.value.trim().toUpperCase();
    if (!isValidSymbol(symbol)) {
      errorEl.textContent = "Enter a valid symbol (letters/periods, up to 10 characters).";
      errorEl.hidden = false;
      return;
    }
    errorEl.hidden = true;
    onSubmit(symbol);
  });

  input.addEventListener("input", () => {
    errorEl.hidden = true;
  });

  return {
    element: form,
    setValue(value) {
      input.value = value || "";
    },
  };
}
