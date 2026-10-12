import { escapeHtml } from "../format.js";

// Renders one ContextSectionResponse (status: AVAILABLE/NO_DATA/ERROR + a fixed backend message,
// see CLAUDE.md Section 17). Distinct from stateBlock.js's renderStateBlock, which maps HTTP
// errors (422/404/502/etc.) from a single-feature request — this endpoint never surfaces those
// per section, only this three-way status on an otherwise-200 response. A section's own ERROR
// has no section-level retry endpoint, so onRetry re-runs the whole context fetch.
export function renderContextSection(container, section, { renderContent, emptyText = "No data available for this symbol.", onRetry }) {
  switch (section.status) {
    case "AVAILABLE":
      renderContent(section.data, container);
      return;
    case "NO_DATA":
      container.innerHTML = `<p class="section-status">${escapeHtml(section.message || emptyText)}</p>`;
      return;
    case "ERROR": {
      container.innerHTML = `
        <div class="state-error">
          <p class="state-error-title">Section unavailable</p>
          <p class="state-error-message">${escapeHtml(section.message || "This section could not be loaded.")}</p>
          ${onRetry ? `<button type="button" class="retry-btn">Retry</button>` : ""}
        </div>`;
      if (onRetry) {
        container.querySelector(".retry-btn")?.addEventListener("click", onRetry);
      }
      return;
    }
    default:
      container.innerHTML = "";
  }
}
