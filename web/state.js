const REFRESH_TOKEN_STORAGE_KEY = "orynix.refreshToken";
const CACHE_TTL_MS = 60_000;

const listeners = new Set();

const session = {
  accessToken: null,
  refreshToken: localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY),
  user: null,
};

let currentSymbol = null;
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
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, refreshToken);
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
  localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  cache.clear();
  currentSymbol = null;
  notify();
}

export function getCurrentSymbol() {
  return currentSymbol;
}

export function setCurrentSymbol(symbol) {
  currentSymbol = symbol;
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
