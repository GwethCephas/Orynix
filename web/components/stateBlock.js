import { ApiError } from "../api.js";

export function createSectionState() {
  return { status: "idle", data: null, error: null };
}

export async function loadInto(sectionRef, renderFn, fetcher) {
  sectionRef.value = { status: "loading", data: null, error: null };
  renderFn();
  try {
    const data = await fetcher();
    sectionRef.value = { status: "ok", data, error: null };
  } catch (error) {
    sectionRef.value = { status: "error", data: null, error };
  }
  renderFn();
}

export function skeletonHtml(lines = 3) {
  return `<div class="skeleton-block">${Array.from({ length: lines })
    .map(() => `<div class="skeleton-line"></div>`)
    .join("")}</div>`;
}

function describeError(error) {
  const status = error instanceof ApiError ? error.status : null;

  if (status === 422) {
    return {
      title: "Not enough history yet",
      message: error.message || "This symbol doesn't have enough historical price data yet.",
      showRetry: false,
    };
  }
  if (status === 404) {
    return {
      title: "No data found",
      message: error.message || "Nothing found for this symbol.",
      showRetry: false,
    };
  }
  if (status === 400) {
    return {
      title: "Invalid request",
      message: error.message || "That request was invalid.",
      showRetry: false,
    };
  }
  if (status === 502) {
    return {
      title: "Data provider unavailable",
      message: error.message || "The upstream data provider is temporarily unavailable. Please try again shortly.",
      showRetry: true,
    };
  }
  return {
    title: "Something went wrong",
    message: error?.message || "Unexpected error.",
    showRetry: true,
  };
}

export function renderStateBlock(container, sectionState, { loadingLines = 3, emptyText = "", renderContent, onRetry }) {
  switch (sectionState.status) {
    case "idle":
      container.innerHTML = emptyText ? `<p class="section-status">${emptyText}</p>` : "";
      return;
    case "loading":
      container.innerHTML = skeletonHtml(loadingLines);
      return;
    case "error": {
      const { title, message, showRetry } = describeError(sectionState.error);
      container.innerHTML = `
        <div class="state-error">
          <p class="state-error-title">${title}</p>
          <p class="state-error-message">${message}</p>
          ${showRetry ? `<button type="button" class="retry-btn">Retry</button>` : ""}
        </div>`;
      if (showRetry && onRetry) {
        container.querySelector(".retry-btn")?.addEventListener("click", onRetry);
      }
      return;
    }
    case "ok":
      renderContent(sectionState.data, container);
      return;
    default:
      container.innerHTML = "";
  }
}
