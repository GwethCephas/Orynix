import { apiRequest, ApiError, getCandles, getTechnicalAnalysis, getSignals, getFundamentals } from "./api.js";
import { renderAssetHeader, renderPriceChart, renderTechnicalAnalysis, renderSignals, renderFundamentals } from "./render.js";
import { initChart } from "./chart.js";

let accessToken = null;
let refreshToken = null;
let currentSymbol = null;

const messageEl = document.getElementById("message");
const userInfoEl = document.getElementById("userInfo");
const dashboardEl = document.getElementById("dashboard");
const symbolForm = document.getElementById("symbolForm");
const symbolInput = document.getElementById("symbolInput");
const refreshBtn = document.getElementById("refreshBtn");

const assetHeaderEl = document.getElementById("assetHeader");
const priceChartCanvas = document.getElementById("priceChart");
const priceChartStatusEl = document.getElementById("priceChartStatus");
const technicalAnalysisEl = document.getElementById("technicalAnalysis");
const signalsEl = document.getElementById("signals");
const fundamentalsEl = document.getElementById("fundamentals");

const idleSection = () => ({ status: "idle", data: null, error: null });

const dashboardState = {
  candles: idleSection(),
  technicalAnalysis: idleSection(),
  signals: idleSection(),
  fundamentals: idleSection(),
};

initChart(priceChartCanvas);

function showMessage(text, isError) {
  messageEl.textContent = text;
  messageEl.className = isError ? "error" : "success";
}

function renderUser(user) {
  userInfoEl.innerHTML = `
    <h2>Current user</h2>
    <p>id: ${user.id}</p>
    <p>email: ${user.email}</p>
    <p>createdAt: ${user.createdAt}</p>
    <p>updatedAt: ${user.updatedAt}</p>
  `;
}

function clearUser() {
  userInfoEl.innerHTML = "<h2>Current user</h2><p>Not logged in.</p>";
}

function renderDashboard() {
  renderAssetHeader(assetHeaderEl, dashboardState.candles, dashboardState.fundamentals, currentSymbol);
  renderPriceChart(priceChartCanvas, priceChartStatusEl, dashboardState.candles);
  renderTechnicalAnalysis(technicalAnalysisEl, dashboardState.technicalAnalysis);
  renderSignals(signalsEl, dashboardState.signals);
  renderFundamentals(fundamentalsEl, dashboardState.fundamentals);
}

async function loadSection(key, fetcher) {
  dashboardState[key] = { status: "loading", data: null, error: null };
  renderDashboard();
  try {
    const data = await fetcher();
    dashboardState[key] = { status: "ok", data, error: null };
  } catch (err) {
    dashboardState[key] = { status: "error", data: null, error: err };
  }
  renderDashboard();
}

async function loadDashboard(symbol) {
  currentSymbol = symbol;
  await Promise.allSettled([
    loadSection("candles", () => getCandles(symbol, accessToken)),
    loadSection("technicalAnalysis", () => getTechnicalAnalysis(symbol, accessToken)),
    loadSection("signals", () => getSignals(symbol, accessToken)),
    loadSection("fundamentals", () => getFundamentals(symbol, accessToken)),
  ]);
}

async function fetchMe() {
  const user = await apiRequest("/api/v1/users/me", { useAuth: true, accessToken });
  renderUser(user);
  dashboardEl.hidden = false;
}

async function register() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  const auth = await apiRequest("/api/v1/auth/register", {
    method: "POST",
    body: { email, password },
  });
  accessToken = auth.accessToken;
  refreshToken = auth.refreshToken;
  showMessage("Registered successfully.", false);
  await fetchMe();
}

async function login() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  const auth = await apiRequest("/api/v1/auth/login", {
    method: "POST",
    body: { email, password },
  });
  accessToken = auth.accessToken;
  refreshToken = auth.refreshToken;
  showMessage("Logged in successfully.", false);
  await fetchMe();
}

async function logout() {
  await apiRequest("/api/v1/auth/logout", {
    method: "POST",
    body: { refreshToken },
    useAuth: true,
    accessToken,
  });
  accessToken = null;
  refreshToken = null;
  currentSymbol = null;
  clearUser();
  dashboardEl.hidden = true;
  showMessage("Logged out successfully.", false);
}

function handleAuthError(err) {
  const message = err instanceof ApiError ? err.message : "Unexpected error.";
  showMessage(message, true);
}

document.getElementById("registerBtn").addEventListener("click", () => register().catch(handleAuthError));
document.getElementById("loginBtn").addEventListener("click", () => login().catch(handleAuthError));
document.getElementById("logoutBtn").addEventListener("click", () => logout().catch(handleAuthError));

symbolForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const symbol = symbolInput.value.trim().toUpperCase();
  if (!symbol) return;
  loadDashboard(symbol);
});

refreshBtn.addEventListener("click", () => {
  if (currentSymbol) {
    loadDashboard(currentSymbol);
  }
});
