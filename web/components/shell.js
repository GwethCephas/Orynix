import { getCurrentUser, getCurrentSymbol, getRefreshToken, isAuthenticated, clearSession, subscribe } from "../state.js";
import { navigate, navigateToCurrentSymbol, onRouteChange } from "../router.js";
import { createSymbolSearch } from "./symbolSearch.js";
import { logout as apiLogout, ApiError } from "../api.js";

const NAV_ITEMS = [
  { path: "overview", label: "Overview" },
  { path: "analysis", label: "Market Analysis" },
  { path: "signals", label: "Signals" },
  { path: "news", label: "News & Sentiment" },
  { path: "company", label: "Company Info" },
  { path: "backtests", label: "Backtesting" },
  { path: "trading-accounts", label: "Trading Accounts" },
  { path: "ai-analysis", label: "AI Market Analysis" },
];

export function renderShell(root) {
  root.innerHTML = `
    <div class="app-shell">
      <button type="button" class="mobile-nav-toggle" aria-label="Toggle navigation">&#9776;</button>
      <aside class="sidebar">
        <div class="sidebar-brand">Orynix</div>
        <nav class="sidebar-nav">
          ${NAV_ITEMS.map((item) => `<a href="#" class="sidebar-nav-item" data-path="${item.path}">${item.label}</a>`).join("")}
        </nav>
      </aside>
      <div class="sidebar-backdrop" hidden></div>
      <div class="app-main">
        <header class="topbar">
          <div class="topbar-search"></div>
          <div class="topbar-title"></div>
          <div class="topbar-user">
            <span class="topbar-email"></span>
            <button type="button" class="logout-btn">Log out</button>
          </div>
        </header>
        <main class="page-root"></main>
      </div>
    </div>
  `;

  const shellEl = root.querySelector(".app-shell");
  const sidebar = root.querySelector(".sidebar");
  const backdrop = root.querySelector(".sidebar-backdrop");
  const toggle = root.querySelector(".mobile-nav-toggle");
  const pageRoot = root.querySelector(".page-root");

  function closeMobileNav() {
    sidebar.classList.remove("open");
    backdrop.hidden = true;
  }

  toggle.addEventListener("click", () => {
    sidebar.classList.toggle("open");
    backdrop.hidden = !sidebar.classList.contains("open");
  });
  backdrop.addEventListener("click", closeMobileNav);

  root.querySelectorAll(".sidebar-nav-item").forEach((link) => {
    link.addEventListener("click", (event) => {
      event.preventDefault();
      navigateToCurrentSymbol(link.dataset.path);
      closeMobileNav();
    });
  });

  const searchContainer = root.querySelector(".topbar-search");
  const search = createSymbolSearch({
    placeholder: "Symbol",
    withSignalFilter: true,
    withStockPicker: true,
    onSubmit: (symbol) => {
      const activeLink = root.querySelector(".sidebar-nav-item.active");
      navigate(activeLink?.dataset.path || "analysis", { symbol });
    },
  });
  searchContainer.appendChild(search.element);

  const titleEl = root.querySelector(".topbar-title");
  const emailEl = root.querySelector(".topbar-email");
  const logoutBtn = root.querySelector(".logout-btn");

  logoutBtn.addEventListener("click", async () => {
    logoutBtn.disabled = true;
    try {
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        await apiLogout(refreshToken);
      }
    } catch (err) {
      if (!(err instanceof ApiError)) {
        console.error("Logout request failed", err);
      }
    } finally {
      clearSession();
      logoutBtn.disabled = false;
    }
  });

  function syncAuthUi() {
    const authenticated = isAuthenticated();
    shellEl.classList.toggle("guest", !authenticated);
    const user = getCurrentUser();
    emailEl.textContent = user ? user.email : "";
  }
  subscribe(syncAuthUi);
  syncAuthUi();

  onRouteChange((path) => {
    root.querySelectorAll(".sidebar-nav-item").forEach((link) => {
      link.classList.toggle("active", link.dataset.path === path);
    });
    const navItem = NAV_ITEMS.find((item) => item.path === path);
    titleEl.textContent = navItem ? navItem.label : "";
    search.setValue(getCurrentSymbol() || "");
  });

  return pageRoot;
}
