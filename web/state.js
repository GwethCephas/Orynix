const REFRESH_TOKEN_STORAGE_KEY = "orynix.refreshToken";
const THEME_STORAGE_KEY = "orynix.theme";
const RECENT_SYMBOLS_STORAGE_KEY = "orynix.recentSymbols";
const MAX_RECENT_SYMBOLS = 6;
const CACHE_TTL_MS = 60_000;

// A manual theme override needs canvas-based charts (chart.js/equityCurveChart.js) to redraw with
// the new theme colors, but must NOT go through notify()/router.js's full current-route
// re-render - that would remount whatever page is open and discard any in-page-only state (a
// filled-in form, AI analysis results, etc.) just because the user flipped a visual preference.
// This dedicated event lets only the chart modules react; see setTheme() below.
export const THEME_CHANGE_EVENT = "orynix:theme-change";

const listeners = new Set();

function readStoredRefreshToken() {
  try {
    return localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
  } catch {
    console.warn("localStorage is unavailable — sessions will not survive a page reload.");
    return null;
  }
}

function writeStoredRefreshToken(value) {
  try {
    if (value) {
      localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, value);
    } else {
      localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
    }
  } catch {
    // Already warned in readStoredRefreshToken; the session still works in-memory for this tab.
  }
}

function readStoredJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function writeStoredJson(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    // Non-fatal: the preference just won't survive a reload for this browser/privacy mode.
  }
}

// Manual theme override. Absent entirely (never written) means "follow the OS
// prefers-color-scheme", preserving this app's original system-driven behavior for anyone who
// never touches the toggle. index.html applies this synchronously before first paint to avoid a
// flash of the wrong theme.
function readStoredTheme() {
  try {
    const value = localStorage.getItem(THEME_STORAGE_KEY);
    return value === "dark" || value === "light" ? value : null;
  } catch {
    return null;
  }
}

export function getTheme() {
  return document.documentElement.getAttribute("data-theme") || readStoredTheme();
}

// The theme actually rendered right now: the manual override if one is set, otherwise whatever
// the OS reports. Used only to decide which way the toggle flips next - getTheme() above stays
// the "is there a manual override" check other code may still want.
export function getEffectiveTheme() {
  const override = getTheme();
  if (override) return override;
  return window.matchMedia && window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

export function setTheme(theme) {
  if (theme === "dark" || theme === "light") {
    document.documentElement.setAttribute("data-theme", theme);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, theme);
    } catch {
      // Still applied for this page view via the attribute set above.
    }
  } else {
    document.documentElement.removeAttribute("data-theme");
    try {
      localStorage.removeItem(THEME_STORAGE_KEY);
    } catch {
      // Attribute removal above already restored system-driven behavior for this page view.
    }
  }
  // Deliberately not notify() - see THEME_CHANGE_EVENT's comment above.
  window.dispatchEvent(new Event(THEME_CHANGE_EVENT));
}

let recentSymbols = readStoredJson(RECENT_SYMBOLS_STORAGE_KEY, []);

// Real symbols the user actually navigated to - a client-side convenience list, not a backend
// watchlist feature (no such endpoint exists). Most-recent-first, deduplicated, capped small.
export function getRecentSymbols() {
  return recentSymbols;
}

function pushRecentSymbol(symbol) {
  if (!symbol) return;
  recentSymbols = [symbol, ...recentSymbols.filter((s) => s !== symbol)].slice(0, MAX_RECENT_SYMBOLS);
  writeStoredJson(RECENT_SYMBOLS_STORAGE_KEY, recentSymbols);
}

const session = {
  accessToken: null,
  refreshToken: readStoredRefreshToken(),
  user: null,
};

// Keep this tab's in-memory refresh token in sync with other tabs on the same origin —
// without this, a token rotated in tab A looks "stale" to tab B's next refresh attempt,
// which the backend correctly rejects as reuse of an already-rotated token, wiping tab B's
// session even though it was never actually invalid.
if (typeof window !== "undefined") {
  window.addEventListener("storage", (event) => {
    if (event.key !== REFRESH_TOKEN_STORAGE_KEY) return;
    session.refreshToken = event.newValue;
    if (!event.newValue) {
      session.accessToken = null;
      session.user = null;
    }
    notify();
  });
}

let currentSymbol = null;
let selectedSignalTypes = [];
const cache = new Map();

function notify() {
  for (const listener of listeners) listener();
}

export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getAccessToken() {
  return session.accessToken;
}

export function getRefreshToken() {
  return session.refreshToken;
}

export function getCurrentUser() {
  return session.user;
}

export function isAuthenticated() {
  return session.accessToken !== null;
}

export function setSession({ accessToken, refreshToken, user }) {
  session.accessToken = accessToken;
  session.refreshToken = refreshToken;
  if (user !== undefined) {
    session.user = user;
  }
  if (refreshToken) {
    writeStoredRefreshToken(refreshToken);
  }
  notify();
}

export function setUser(user) {
  session.user = user;
  notify();
}

export function clearSession() {
  session.accessToken = null;
  session.refreshToken = null;
  session.user = null;
  writeStoredRefreshToken(null);
  cache.clear();
  currentSymbol = null;
  selectedSignalTypes = [];
  notify();
}

export function getCurrentSymbol() {
  return currentSymbol;
}

export function setCurrentSymbol(symbol) {
  currentSymbol = symbol;
  pushRecentSymbol(symbol);
  notify();
}

// Client-side-only signal-type filter (see signalCatalogue.js) — the backend has no catalogue or
// market-wide discovery endpoint, so this only controls which of the current symbol's own signals
// are displayed on the Signals page. Not persisted across reloads; resets on logout like
// currentSymbol.
export function getSelectedSignalTypes() {
  return selectedSignalTypes;
}

export function setSelectedSignalTypes(types) {
  selectedSignalTypes = [...types];
  notify();
}

function cacheKey(symbol, endpoint) {
  return `${symbol}::${endpoint}`;
}

export function getCached(symbol, endpoint) {
  const entry = cache.get(cacheKey(symbol, endpoint));
  if (!entry) return undefined;
  if (Date.now() - entry.storedAt > CACHE_TTL_MS) {
    cache.delete(cacheKey(symbol, endpoint));
    return undefined;
  }
  return entry.value;
}

export function setCached(symbol, endpoint, value) {
  cache.set(cacheKey(symbol, endpoint), { value, storedAt: Date.now() });
}

export function invalidateCached(symbol, endpoint) {
  if (endpoint) {
    cache.delete(cacheKey(symbol, endpoint));
    return;
  }
  for (const key of cache.keys()) {
    if (key.startsWith(`${symbol}::`)) cache.delete(key);
  }
}

export async function withCache(symbol, endpoint, fetcher) {
  const cached = getCached(symbol, endpoint);
  if (cached !== undefined) return cached;
  const value = await fetcher();
  setCached(symbol, endpoint, value);
  return value;
}
