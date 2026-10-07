export const API_BASE = "http://localhost:8080";

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

export async function apiRequest(path, { method = "GET", body, useAuth = false, accessToken } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (useAuth) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (err) {
    throw new ApiError("Could not reach backend. Is it running?", 0);
  }

  if (response.status === 204) {
    return null;
  }

  const data = await response.json();

  if (!response.ok) {
    throw new ApiError(data.message || `Request failed (${response.status})`, response.status);
  }

  return data;
}

export function getCandles(symbol, accessToken) {
  return apiRequest(`/api/v1/market-data/candles?symbol=${encodeURIComponent(symbol)}&timeframe=1d`, {
    useAuth: true,
    accessToken,
  });
}

export function getTechnicalAnalysis(symbol, accessToken) {
  return apiRequest(`/api/v1/technical-analysis?symbol=${encodeURIComponent(symbol)}&timeframe=1d`, {
    useAuth: true,
    accessToken,
  });
}

export function getSignals(symbol, accessToken) {
  return apiRequest(`/api/v1/signals?symbol=${encodeURIComponent(symbol)}&timeframe=1d`, {
    useAuth: true,
    accessToken,
  });
}

export function getFundamentals(symbol, accessToken) {
  return apiRequest(`/api/v1/fundamentals?symbol=${encodeURIComponent(symbol)}`, {
    useAuth: true,
    accessToken,
  });
}
