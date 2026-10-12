import { login, register, ApiError } from "../api.js";
import { setSession } from "../state.js";

export function mount(container) {
  container.innerHTML = `
    <div class="auth-screen">
      <div class="auth-card">
        <h1 class="auth-brand">Orynix</h1>
        <p class="auth-tagline">Technical analysis, deterministic signals, company fundamentals, news, and sentiment — in one place.</p>
        <form class="auth-form">
          <label class="auth-field">
            <span>Email</span>
            <input type="email" name="email" required autocomplete="email">
          </label>
          <label class="auth-field">
            <span>Password</span>
            <input type="password" name="password" required autocomplete="current-password">
          </label>
          <div class="auth-actions">
            <button type="submit" data-action="login" class="auth-primary-btn">Log in</button>
            <button type="button" data-action="register" class="auth-secondary-btn">Create account</button>
          </div>
        </form>
        <p class="auth-message" hidden></p>
      </div>
    </div>`;

  const form = container.querySelector(".auth-form");
  const emailInput = form.querySelector('[name="email"]');
  const passwordInput = form.querySelector('[name="password"]');
  const messageEl = container.querySelector(".auth-message");
  const loginBtn = container.querySelector('[data-action="login"]');
  const registerBtn = container.querySelector('[data-action="register"]');

  function showMessage(text, isError) {
    messageEl.textContent = text;
    messageEl.className = `auth-message ${isError ? "error" : "success"}`;
    messageEl.hidden = false;
  }

  function setBusy(busy) {
    loginBtn.disabled = busy;
    registerBtn.disabled = busy;
  }

  async function handle(action) {
    const email = emailInput.value.trim();
    const password = passwordInput.value;
    if (!email || !password) {
      showMessage("Enter an email and password.", true);
      return;
    }

    setBusy(true);
    try {
      const auth = action === "register" ? await register(email, password) : await login(email, password);
      setSession({ accessToken: auth.accessToken, refreshToken: auth.refreshToken, user: auth.user });
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Unexpected error. Please try again.";
      showMessage(message, true);
    } finally {
      setBusy(false);
    }
  }

  function onSubmit(event) {
    event.preventDefault();
    handle("login");
  }

  function onRegisterClick() {
    handle("register");
  }

  form.addEventListener("submit", onSubmit);
  registerBtn.addEventListener("click", onRegisterClick);

  return () => {
    form.removeEventListener("submit", onSubmit);
    registerBtn.removeEventListener("click", onRegisterClick);
  };
}
