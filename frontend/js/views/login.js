import { api } from "../api.js";
import { config } from "../config.js?v=4";
import { getDeviceId, getLastEmail, isTrustedEmail, rememberTrustedEmail, setUser } from "../session.js?v=3";
import { esc, submitForm, toast } from "../ui.js";

function rememberEmail(email) {
  try { localStorage.setItem("sdu.bagdar.last-email", email.trim().toLowerCase()); } catch { /* storage unavailable */ }
}

function randomPassword() {
  return globalThis.crypto?.randomUUID?.() ?? `sdu-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function renderAuth(root, onAuthenticated) {
  let mode = "login";

  const drawTeacherRegistration = () => {
    const savedEmail = getLastEmail();
    root.innerHTML = `
      <div class="auth">
        <div class="auth-art auth-art-photo"><img class="auth-campus-photo" src="assets/sdu-campus.jpg" alt="SDU University campus building"></div>
        <main class="auth-form" id="main">
          <div><h1>Register as teacher</h1><p class="muted">Create a teacher account for SDU Bağdar.</p></div>
          <form class="form" novalidate>
            <label>Full name<input name="name" required autocomplete="name" placeholder="Your full name"></label>
            <label>SDU email<input name="email" type="email" required autocomplete="email" placeholder="teacher@sdu.edu.kz" value="${esc(savedEmail)}"></label>
            ${config.skipTeacherInvite ? "" : '<label>Teacher invite code<input name="inviteCode" required autocomplete="off" placeholder="Invite code"></label>'}
            <p class="err-text" role="alert"></p>
            <button class="btn cta" type="submit">Register as teacher</button>
          </form>
          <button class="link" type="button" data-back-sign-in>Back to sign in</button>
        </main>
      </div>`;

    root.querySelector("[data-back-sign-in]").addEventListener("click", () => { mode = "login"; draw(); });
    root.querySelector("form").addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      if (!form.checkValidity()) {
        form.querySelector(".err-text").textContent = "Complete all teacher registration fields.";
        return;
      }
      const result = await submitForm(form, (values) => api.post("/auth/register", {
        name: values.name,
        email: values.email.trim().toLowerCase(),
        role: "TEACHER",
        inviteCode: values.inviteCode ?? "",
        password: randomPassword(),
      }), "Teacher account created");
      if (result) {
        const email = form.elements.email.value.trim().toLowerCase();
        rememberEmail(email);
        rememberTrustedEmail(email);
        setUser({ ...result.user, token: result.token });
        onAuthenticated();
      }
    });
  };

  const draw = () => {
    if (mode === "teacher") return drawTeacherRegistration();
    const skipVerification = config.skipVerification;
    const savedEmail = getLastEmail();
    const trusted = skipVerification || isTrustedEmail(savedEmail);
    root.innerHTML = `
      <div class="auth">
        <div class="auth-art auth-art-photo"><img class="auth-campus-photo" src="assets/sdu-campus.jpg" alt="SDU University campus building"></div>
        <main class="auth-form" id="main">
          <div><h1>Sign in to SDU Bağdar</h1><p class="muted">Use your SDU email to continue.</p></div>
          <form class="form" novalidate>
            <label>SDU email<input name="email" type="email" required autocomplete="email" placeholder="student@sdu.edu.kz" value="${esc(savedEmail)}"></label>
            <div class="verification-field ${skipVerification || trusted ? "hidden" : ""}">
              <label class="hidden" data-code-label>Verification code<input name="verificationCode" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" minlength="6" maxlength="6" placeholder="6-digit code" ${trusted ? "" : "required"}></label>
              <button class="btn sm" type="button" data-send-code ${skipVerification || trusted ? "hidden" : ""}>Send verification code</button>
            </div>
            <p class="auth-hint muted" data-auth-hint>${skipVerification ? "Verification code is temporarily disabled." : trusted ? "This device is registered. You can sign in without a code." : "We will send a code to your SDU email when you register this device."}</p>
            <p class="err-text" role="alert"></p><button class="btn cta" type="submit">Sign in</button>
          </form>
          <button class="link" type="button" data-teacher-register>Register as teacher</button>
        </main>
      </div>`;

    const form = root.querySelector("form");
    const emailInput = form.elements.email;
    const codeField = root.querySelector(".verification-field");
    const codeInput = form.elements.verificationCode;
    const sendButton = root.querySelector("[data-send-code]");
    const hint = root.querySelector("[data-auth-hint]");
    const errorBox = form.querySelector(".err-text");

    const codeLabel = root.querySelector("[data-code-label]");
    let codeSent = false;
    const syncDeviceState = () => {
      const email = emailInput.value.trim().toLowerCase();
      const known = skipVerification || isTrustedEmail(email);
      codeField.classList.toggle("hidden", known);
      sendButton.classList.toggle("hidden", known);
      codeLabel.classList.toggle("hidden", known || !codeSent);
      codeInput.required = !known;
      hint.textContent = skipVerification ? "Verification code is temporarily disabled."
        : known ? "This device is registered. You can sign in without a code."
        : "We will send a code to your SDU email when you register this device.";
    };

    root.querySelector("[data-teacher-register]").addEventListener("click", () => { mode = "teacher"; draw(); });
    emailInput.addEventListener("input", () => { codeSent = false; syncDeviceState(); });
    sendButton.addEventListener("click", async () => {
      const email = emailInput.value.trim().toLowerCase();
      if (!emailInput.checkValidity()) { errorBox.textContent = "Enter a valid SDU email first."; emailInput.focus(); return; }
      sendButton.disabled = true;
      errorBox.textContent = "";
      try {
        const result = await api.post("/auth/request-code", { email, deviceId: getDeviceId() });
        rememberEmail(email);
        if (result.trusted) { rememberTrustedEmail(email); syncDeviceState(); toast("This device is already registered"); }
        else { codeSent = true; syncDeviceState(); toast("Verification code sent to your SDU email"); codeInput.focus(); }
      } catch (error) { errorBox.textContent = error.message; }
      finally { sendButton.disabled = false; }
    });

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      syncDeviceState();
      if (!form.checkValidity()) { errorBox.textContent = "Enter your SDU email and 6-digit verification code."; return; }
      const email = emailInput.value.trim().toLowerCase();
      rememberEmail(email);
      const result = await submitForm(form, (values) => api.post("/auth/login", {
        email, verificationCode: values.verificationCode, deviceId: getDeviceId(),
      }), "Welcome to SDU Bağdar");
      if (result) { rememberTrustedEmail(email); setUser({ ...result.user, token: result.token }); onAuthenticated(); }
    });
  };
  draw();
}
