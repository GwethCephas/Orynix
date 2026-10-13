import { getMe, SessionExpiredError, ApiError } from "./api.js";
import { getRefreshToken, setUser, clearSession } from "./state.js";
import { renderShell } from "./components/shell.js";
import { registerRoute, setAuthPage, setDefaultRoute, startRouter } from "./router.js";
import * as authPage from "./pages/auth.js";
import * as overviewPage from "./pages/overview.js";
import * as analysisPage from "./pages/analysis.js";
import * as signalsPage from "./pages/signals.js";
import * as newsPage from "./pages/news.js";
import * as companyPage from "./pages/company.js";
import * as backtestsPage from "./pages/backtests.js";

const root = document.getElementById("app-root");
const pageRoot = renderShell(root);

setAuthPage(authPage);
registerRoute("overview", overviewPage);
registerRoute("analysis", analysisPage);
registerRoute("signals", signalsPage);
registerRoute("news", newsPage);
registerRoute("company", companyPage);
registerRoute("backtests", backtestsPage);
setDefaultRoute("overview");

async function restoreSession() {
  if (!getRefreshToken()) return;
  try {
    const user = await getMe();
    setUser(user);
  } catch (err) {
    if (err instanceof SessionExpiredError) {
      // apiRequest's 401-retry path already cleared the session.
      return;
    }
    if (!(err instanceof ApiError)) {
      console.error("Unexpected error restoring session", err);
    }
    // Network failures / provider errors at boot shouldn't discard a still-valid
    // refresh token — leave it in place so the next load can try again.
  }
}

restoreSession().finally(() => {
  startRouter(pageRoot);
});
