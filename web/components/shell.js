import { getCurrentUser, getCurrentSymbol, getRefreshToken, isAuthenticated, clearSession, subscribe, getEffectiveTheme, setTheme } from "../state.js";
import { navigate, navigateToCurrentSymbol, onRouteChange } from "../router.js";
import { createSymbolSearch } from "./symbolSearch.js";
import { logout as apiLogout, ApiError } from "../api.js";
import { escapeHtml } from "../format.js";

const SIDEBAR_COLLAPSED_STORAGE_KEY = "orynix.sidebarCollapsed";

// Small, hand-authored stroke icons (24x24, currentColor) - no icon-font or library dependency.
const NAV_ICONS = {
  overview: `<path d="M3 11.5 12 4l9 7.5"/><path d="M5.5 10v10h13V10"/>`,
  analysis: `<polyline points="3 17 9 11 13 15 21 7"/><polyline points="15 7 21 7 21 13"/>`,
  signals: `<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>`,
  news: `<path d="M6 3h9l5 5v12a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z"/><path d="M15 3v5h5"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="13" y2="17"/>`,
  company: `<rect x="3" y="8" width="18" height="13" rx="1.5"/><path d="M8 8V5.5A1.5 1.5 0 0 1 9.5 4h5A1.5 1.5 0 0 1 16 5.5V8"/>`,
  backtests: `<circle cx="12" cy="13" r="8.5"/><polyline points="12 9 12 13 15 15"/><path d="M8.5 3.5 5 6"/><path d="M19 6l-3.5-2.5"/>`,
  "trading-accounts": `<rect x="2" y="5" width="20" height="14" rx="1.5"/><line x1="2" y1="10" x2="22" y2="10"/>`,
  "ai-analysis": `<rect x="5" y="5" width="14" height="14" rx="2"/><rect x="9" y="9" width="6" height="6"/><line x1="9" y1="1.5" x2="9" y2="5"/><line x1="15" y1="1.5" x2="15" y2="5"/><line x1="9" y1="19" x2="9" y2="22.5"/><line x1="15" y1="19" x2="15" y2="22.5"/><line x1="19" y1="9" x2="22.5" y2="9"/><line x1="19" y1="15" x2="22.5" y2="15"/><line x1="1.5" y1="9" x2="5" y2="9"/><line x1="1.5" y1="15" x2="5" y2="15"/>`,
  "paper-trading": `<rect x="4" y="3" width="16" height="18" rx="1.5"/><line x1="8" y1="8" x2="16" y2="8"/><line x1="8" y1="12" x2="16" y2="12"/><line x1="8" y1="16" x2="12.5" y2="16"/>`,
};

function navIcon(path) {
  return `<span class="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${NAV_ICONS[path] || ""}</svg></span>`;
}

const NAV_ITEMS = [
  { path: "overview", label: "Overview" },
  { path: "analysis", label: "Market Analysis" },
  { path: "signals", label: "Signals" },
  { path: "news", label: "News & Sentiment" },
  { path: "company", label: "Company Info" },
  { path: "backtests", label: "Backtesting" },
  { path: "trading-accounts", label: "Trading Accounts" },
  { path: "ai-analysis", label: "AI Market Analysis" },
  { path: "paper-trading", label: "Paper Trading" },
];

function readSidebarCollapsed() {
  try {
    return localStorage.getItem(SIDEBAR_COLLAPSED_STORAGE_KEY) === "1";
  } catch {
    return false;
  }
}

function writeSidebarCollapsed(collapsed) {
  try {
    localStorage.setItem(SIDEBAR_COLLAPSED_STORAGE_KEY, collapsed ? "1" : "0");
  } catch {
    // Non-fatal: the preference just won't survive a reload.
  }
}

export function renderShell(root) {
  root.innerHTML = `
    <div class="app-shell">
      <button type="button" class="mobile-nav-toggle" aria-label="Toggle navigation">&#9776;</button>
      <aside class="sidebar">
        <div class="sidebar-brand">
          <span class="sidebar-brand-text">Orynix</span>
          <button type="button" class="sidebar-collapse-btn" aria-label="Collapse sidebar" aria-expanded="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="15 18 9 12 15 6"/></svg>
          </button>
        </div>
        <nav class="sidebar-nav">
          ${NAV_ITEMS.map(
            (item) =>
              `<a href="#" class="sidebar-nav-item" data-path="${item.path}" aria-label="${escapeHtml(item.label)}" title="${escapeHtml(item.label)}">${navIcon(item.path)}<span class="sidebar-nav-item-label">${escapeHtml(item.label)}</span></a>`
          ).join("")}
        </nav>
      </aside>
      <div class="sidebar-backdrop" hidden></div>
      <div class="app-main">
        <header class="topbar">
          <div class="topbar-search"></div>
          <div class="topbar-title"></div>
          <div class="topbar-user">
            <button type="button" class="theme-toggle-btn" aria-label="Switch to light theme">
              <svg class="icon-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4.5"/><path d="M12 2.5v3M12 18.5v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2.5 12h3M18.5 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1"/></svg>
              <svg class="icon-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 14.5A8.5 8.5 0 1 1 9.5 4a7 7 0 0 0 10.5 10.5Z"/></svg>
            </button>
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
  const collapseBtn = root.querySelector(".sidebar-collapse-btn");
  const themeToggleBtn = root.querySelector(".theme-toggle-btn");
  const pageRoot = root.querySelector(".page-root");

  // Keyed off a [data-sidebar] attribute on <html>, not a class on .app-shell - the matching
  // style.css rules target that same root attribute (set synchronously in index.html before this
  // module even runs) specifically so there is one single source of truth for the collapsed
  // state, applied identically whether it's set pre-paint or toggled live by this button.
  function syncCollapseUi(collapsed) {
    if (collapsed) {
      document.documentElement.setAttribute("data-sidebar", "collapsed");
    } else {
      document.documentElement.removeAttribute("data-sidebar");
    }
    collapseBtn.setAttribute("aria-expanded", String(!collapsed));
    collapseBtn.setAttribute("aria-label", collapsed ? "Expand sidebar" : "Collapse sidebar");
  }
  syncCollapseUi(readSidebarCollapsed());

  collapseBtn.addEventListener("click", () => {
    const next = document.documentElement.getAttribute("data-sidebar") !== "collapsed";
    syncCollapseUi(next);
    writeSidebarCollapsed(next);
  });

  function syncThemeToggleUi() {
    const isLight = getEffectiveTheme() === "light";
    themeToggleBtn.setAttribute("aria-label", isLight ? "Switch to dark theme" : "Switch to light theme");
  }
  syncThemeToggleUi();

  themeToggleBtn.addEventListener("click", () => {
    setTheme(getEffectiveTheme() === "dark" ? "light" : "dark");
    syncThemeToggleUi();
  });

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
      const isActive = link.dataset.path === path;
      link.classList.toggle("active", isActive);
      if (isActive) {
        link.setAttribute("aria-current", "page");
      } else {
        link.removeAttribute("aria-current");
      }
    });
    const navItem = NAV_ITEMS.find((item) => item.path === path);
    titleEl.textContent = navItem ? navItem.label : "";
    search.setValue(getCurrentSymbol() || "");
  });

  return pageRoot;
}
