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

async function refreshAccessToken() {
  if (refreshInFlight) {
    return refreshInFlight;
  }

  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new SessionExpiredError();
  }

  refreshInFlight = (async () => {
    const { ok, data } = await rawRequest("/api/v1/auth/refresh", {
      method: "POST",
      body: { refreshToken },
    });
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

export function getTechnicalAnalysis(symbol) {
  return apiRequest(`/api/v1/technical-analysis?symbol=${encodeURIComponent(symbol)}&timeframe=1d`, {
    useAuth: true,
  });
}

export function getSignals(symbol) {
  return apiRequest(`/api/v1/signals?symbol=${encodeURIComponent(symbol)}&timeframe=1d`, {
    useAuth: true,
  });
}

export function getFundamentals(symbol) {
  return apiRequest(`/api/v1/fundamentals?symbol=${encodeURIComponent(symbol)}`, {
    useAuth: true,
  });
}

export function getNews(symbol, limit = 20) {
  return apiRequest(`/api/v1/news?symbol=${encodeURIComponent(symbol)}&limit=${limit}`, {
    useAuth: true,
  });
}

export function getArticleSentiment(newsId) {
  return apiRequest(`/api/v1/news/${encodeURIComponent(newsId)}/sentiment`, { useAuth: true });
}

export function getSentimentAggregate(symbol, limit = 20) {
  return apiRequest(`/api/v1/news/sentiment?symbol=${encodeURIComponent(symbol)}&limit=${limit}`, {
    useAuth: true,
  });
}
