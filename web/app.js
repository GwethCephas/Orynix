const API_BASE = "http://localhost:8080";

let accessToken = null;
let refreshToken = null;

const messageEl = document.getElementById("message");
const userInfoEl = document.getElementById("userInfo");

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

async function apiRequest(path, method, body, useAuth) {
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
    showMessage("Could not reach backend. Is it running?", true);
    throw err;
  }

  if (response.status === 204) {
    return null;
  }

  const data = await response.json();

  if (!response.ok) {
    showMessage(data.message || `Request failed (${response.status})`, true);
    throw new Error(data.message);
  }

  return data;
}

async function fetchMe() {
  const user = await apiRequest("/api/v1/users/me", "GET", null, true);
  renderUser(user);
}

async function register() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  const auth = await apiRequest("/api/v1/auth/register", "POST", { email, password }, false);
  accessToken = auth.accessToken;
  refreshToken = auth.refreshToken;
  showMessage("Registered successfully.", false);
  await fetchMe();
}

async function login() {
  const email = document.getElementById("email").value;
  const password = document.getElementById("password").value;

  const auth = await apiRequest("/api/v1/auth/login", "POST", { email, password }, false);
  accessToken = auth.accessToken;
  refreshToken = auth.refreshToken;
  showMessage("Logged in successfully.", false);
  await fetchMe();
}

async function logout() {
  await apiRequest("/api/v1/auth/logout", "POST", { refreshToken }, true);
  accessToken = null;
  refreshToken = null;
  clearUser();
  showMessage("Logged out successfully.", false);
}

document.getElementById("registerBtn").addEventListener("click", () => register().catch(() => {}));
document.getElementById("loginBtn").addEventListener("click", () => login().catch(() => {}));
document.getElementById("logoutBtn").addEventListener("click", () => logout().catch(() => {}));
