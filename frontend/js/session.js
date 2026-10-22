const KEY = "campusos.session";
const DEVICE_KEY = "sdu.bagdar.device";
const TRUSTED_KEY = "sdu.bagdar.trusted-emails";
const LAST_EMAIL_KEY = "sdu.bagdar.last-email";

export function getUser() {
  try { return JSON.parse(sessionStorage.getItem(KEY)); } catch { return null; }
}
export function setUser(user) {
  try { sessionStorage.setItem(KEY, JSON.stringify(user)); } catch { /* storage unavailable */ }
}
export function clearUser() {
  try { sessionStorage.removeItem(KEY); } catch { /* storage unavailable */ }
}

export function getLastEmail() {
  try { return localStorage.getItem(LAST_EMAIL_KEY) ?? ""; } catch { return ""; }
}

export function getDeviceId() {
  try {
    let id = localStorage.getItem(DEVICE_KEY);
    if (!id) {
      id = globalThis.crypto?.randomUUID?.() ?? `device-${Date.now()}-${Math.random().toString(36).slice(2)}`;
      localStorage.setItem(DEVICE_KEY, id);
    }
    return id;
  } catch { return `session-device-${location.host}`; }
}

export function isTrustedEmail(email) {
  try { return JSON.parse(localStorage.getItem(TRUSTED_KEY) ?? "[]").includes(email.trim().toLowerCase()); }
  catch { return false; }
}

export function rememberTrustedEmail(email) {
  try {
    const emails = JSON.parse(localStorage.getItem(TRUSTED_KEY) ?? "[]");
    const normalized = email.trim().toLowerCase();
    if (!emails.includes(normalized)) localStorage.setItem(TRUSTED_KEY, JSON.stringify([...emails, normalized]));
  } catch { /* storage unavailable */ }
}
