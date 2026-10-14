import { getAccessToken, getRefreshToken, setSession, clearSession } from "./state.js";

export const API_BASE = "http://localhost:8080";

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export class SessionExpiredError extends ApiError {
  constructor() {
    super("Your session has expired. Please log in again.", 401);
  }
}

let refreshInFlight = null;

async function rawRequest(path, { method = "GET", body, accessToken } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError("Could not reach backend. Is it running?", 0);
  }

  if (response.status === 204) {
    return { ok: true, data: null, status: 204 };
  }

  const data = await response.json();
  return { ok: response.ok, data, status: response.status };
}

async function attemptRefresh(refreshToken) {
  return rawRequest("/api/v1/auth/refresh", { method: "POST", body: { refreshToken } });
}

async function refreshAccessToken() {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new SessionExpiredError();
  }

  refreshInFlight = (async () => {
    let { ok, data } = await attemptRefresh(refreshToken);

    // A rejected refresh token might mean another tab already rotated it (the storage-sync
    // listener in state.js would have picked up the new value) — retry once with whatever is
    // current before concluding the session is genuinely dead.
    if (!ok) {
      const latestToken = getRefreshToken();
      if (latestToken && latestToken !== refreshToken) {
        ({ ok, data } = await attemptRefresh(latestToken));
      }
    }

    if (!ok) {
      clearSession();
      throw new SessionExpiredError();
    }
    setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken });
    return data.accessToken;
  })();

  try {
    return await refreshInFlight;
  } finally {
    refreshInFlight = null;
  }
}

export async function apiRequest(path, { method = "GET", body, useAuth = false } = {}) {
  const accessToken = useAuth ? getAccessToken() : undefined;
  let { ok, data, status } = await rawRequest(path, { method, body, accessToken });

  if (!ok && status === 401 && useAuth) {
    const newAccessToken = await refreshAccessToken();
    ({ ok, data, status } = await rawRequest(path, { method, body, accessToken: newAccessToken }));
  }

  if (!ok) {
    throw new ApiError(data?.message || `Request failed (${status})`, status);
  }

  return data;
}

export function register(email, password) {
  return apiRequest("/api/v1/auth/register", { method: "POST", body: { email, password } });
}

export function login(email, password) {
  return apiRequest("/api/v1/auth/login", { method: "POST", body: { email, password } });
}

export function logout(refreshToken) {
  return apiRequest("/api/v1/auth/logout", { method: "POST", body: { refreshToken }, useAuth: true });
}

export function getMe() {
  return apiRequest("/api/v1/users/me", { useAuth: true });
}

export function getCandles(symbol) {
  return apiRequest(`/api/v1/market-data/candles?symbol=${encodeURIComponent(symbol)}&timeframe=1d`, {
    useAuth: true,
  });
}

export function getArticleSentiment(newsId) {
  return apiRequest(`/api/v1/news/${encodeURIComponent(newsId)}/sentiment`, { useAuth: true });
}

export function getMarketIntelligence(symbol) {
  return apiRequest(`/api/v1/market-intelligence?symbol=${encodeURIComponent(symbol)}`, {
    useAuth: true,
  });
}

export function createBacktest(request) {
  return apiRequest("/api/v1/backtests", { method: "POST", body: request, useAuth: true });
}

export function listBacktests() {
  return apiRequest("/api/v1/backtests", { useAuth: true });
}

export function getBacktest(backtestId) {
  return apiRequest(`/api/v1/backtests/${encodeURIComponent(backtestId)}`, { useAuth: true });
}

export function getBacktestTrades(backtestId) {
  return apiRequest(`/api/v1/backtests/${encodeURIComponent(backtestId)}/trades`, { useAuth: true });
}

export function createTradingAccountProfile(request) {
  return apiRequest("/api/v1/trading-accounts", { method: "POST", body: request, useAuth: true });
}

export function listTradingAccountProfiles() {
  return apiRequest("/api/v1/trading-accounts", { useAuth: true });
}

export function getTradingAccountProfile(profileId) {
  return apiRequest(`/api/v1/trading-accounts/${encodeURIComponent(profileId)}`, { useAuth: true });
}

export function updateTradingAccountProfile(profileId, request) {
  return apiRequest(`/api/v1/trading-accounts/${encodeURIComponent(profileId)}`, { method: "PUT", body: request, useAuth: true });
}

export function archiveTradingAccountProfile(profileId) {
  return apiRequest(`/api/v1/trading-accounts/${encodeURIComponent(profileId)}`, { method: "DELETE", useAuth: true });
}

export function calculateRisk(request) {
  return apiRequest("/api/v1/risk-calculations", { method: "POST", body: request, useAuth: true });
}
