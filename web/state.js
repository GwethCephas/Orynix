const REFRESH_TOKEN_STORAGE_KEY = "orynix.refreshToken";
const CACHE_TTL_MS = 60_000;

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
