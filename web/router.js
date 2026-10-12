import { isAuthenticated, getCurrentSymbol, setCurrentSymbol, subscribe as subscribeState } from "./state.js";

const routes = new Map();
let container = null;
let authPage = null;
let defaultRoute = "overview";
let currentCleanup = null;
let routeChangeListeners = [];

export function registerRoute(path, pageModule) {
  routes.set(path, pageModule);
}

export function setAuthPage(pageModule) {
  authPage = pageModule;
}

export function setDefaultRoute(path) {
  defaultRoute = path;
}

export function onRouteChange(listener) {
  routeChangeListeners.push(listener);
  return () => {
    routeChangeListeners = routeChangeListeners.filter((l) => l !== listener);
  };
}

function parseHash() {
  const hash = window.location.hash.replace(/^#\/?/, "");
  const [path, queryString] = hash.split("?");
  const params = new URLSearchParams(queryString || "");
  return { path: path || defaultRoute, params };
}

export function navigate(path, params = {}) {
  const cleanParams = Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== null && value !== undefined && value !== "")
  );
  const query = new URLSearchParams(cleanParams).toString();
  window.location.hash = `/${path}${query ? `?${query}` : ""}`;
}

export function navigateToCurrentSymbol(path) {
  navigate(path, { symbol: getCurrentSymbol() });
}

async function render() {
  if (!container) return;

  if (currentCleanup) {
    try {
      currentCleanup();
    } catch {
      // page cleanup should never block navigation
    }
    currentCleanup = null;
  }

  if (!isAuthenticated()) {
    routeChangeListeners.forEach((listener) => listener(null));
    currentCleanup = (await authPage.mount(container)) || null;
    return;
  }

  const { path, params } = parseHash();
  const symbolParam = params.get("symbol");
  if (symbolParam) {
    const normalized = symbolParam.trim().toUpperCase();
    if (normalized !== getCurrentSymbol()) {
      setCurrentSymbol(normalized);
      return;
    }
  }

  const resolvedPath = routes.has(path) ? path : defaultRoute;
  const pageModule = routes.get(resolvedPath);
  routeChangeListeners.forEach((listener) => listener(resolvedPath));
  currentCleanup = (await pageModule.mount(container, params)) || null;
}

export function startRouter(rootContainer) {
  container = rootContainer;
  window.addEventListener("hashchange", render);
  subscribeState(render);
  render();
}
