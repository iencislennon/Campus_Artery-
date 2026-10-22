const escapeMap = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
export const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => escapeMap[c]);

const ICONS = {
  home: '<path d="M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/>',
  door: '<path d="M5 21V4a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v17M3 21h18M15 12v.01"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  wrench: '<path d="M14 6a4 4 0 0 0 5 5l-9 9a2.1 2.1 0 0 1-3-3l9-9a4 4 0 0 0-2-2z"/>',
  spark: '<path d="M12 3l2 6 6 2-6 2-2 6-2-6-6-2 6-2z"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2 20a7 7 0 0 1 14 0M17 4.5a3.5 3.5 0 0 1 0 7M22 20a6 6 0 0 0-4-5.6"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  id: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="11" r="2.5"/><path d="M5.5 17a4 4 0 0 1 7 0M15 9h4M15 13h4"/>',
  empty: '<circle cx="12" cy="12" r="9"/><path d="M12 3v18M3 12h18"/>',
};
export const icon = (name) =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;

export const shanyrak = (cls = "") => `
<svg class="${cls}" viewBox="0 0 120 120" fill="none" aria-hidden="true">
  <g class="spin">
    <circle cx="60" cy="60" r="52" stroke="#F3A367" stroke-width="3"/>
    <circle cx="60" cy="60" r="20" stroke="#F3A367" stroke-width="2"/>
    <g stroke="#F3A367" stroke-width="2"><path d="M60 8v104M8 60h104M23 23l74 74M97 23l-74 74"/></g>
    <circle cx="60" cy="60" r="6" fill="#fff"/>
  </g>
</svg>`;

export const logo = (cls = "logo") => `<img class="${cls}" src="assets/logo.png" alt="SDU University">`;

export const loading = () => '<div class="list" aria-busy="true"><div class="skeleton"></div><div class="skeleton"></div><div class="skeleton"></div></div>';
export const empty = (title, hint = "") =>
  `<div class="state">${icon("empty")}<h3>${esc(title)}</h3><p>${esc(hint)}</p></div>`;
export const failure = (message) =>
  `<div class="state error" role="alert"><h3>Could not load this page</h3><p>${esc(message)}</p><button class="btn" data-retry>Retry</button></div>`;

export function toast(message, tone = "") {
  const node = Object.assign(document.createElement("div"), { className: `toast ${tone}`, textContent: message });
  document.getElementById("toasts").append(node);
  setTimeout(() => node.remove(), 3500);
}

const STATUS_TONE = { OPEN: "warn", IN_PROGRESS: "blue", RESOLVED: "ok" };
export const statusChip = (status) =>
  `<span class="chip ${STATUS_TONE[status] ?? ""}">${esc(status.replace("_", " "))}</span>`;

/** Runs `render` into `target` with loading, error and retry handled in one place. */
export async function withState(target, load, render) {
  target.innerHTML = loading();
  try {
    target.innerHTML = render(await load());
  } catch (error) {
    console.error("[ui] load failed:", error);
    target.innerHTML = failure(error.message);
    target.querySelector("[data-retry]")?.addEventListener("click", () => withState(target, load, render));
  }
}

export function formValues(form) {
  return Object.fromEntries(new FormData(form));
}

export function readAttachment(file) {
  if (!file || !file.size) return null;
  if (file.size > 5_000_000) throw new Error("The file must be smaller than 5 MB.");
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve({ filename: file.name, mimeType: file.type || "application/octet-stream",
      contentBase64: String(reader.result).split(",")[1] ?? "" });
    reader.onerror = () => reject(new Error("Could not read the file."));
    reader.readAsDataURL(file);
  });
}

const MAX_PHOTO_BYTES = 1_000_000;
export const readImage = (file) => new Promise((resolve, reject) => {
  if (file.size > MAX_PHOTO_BYTES) return reject(new Error("Photo is too large. Use an image under 1 MB."));
  const reader = new FileReader();
  reader.onload = () => resolve(reader.result);
  reader.onerror = () => reject(new Error("Could not read that image."));
  reader.readAsDataURL(file);
});

export async function submitForm(form, action, successMessage) {
  const button = form.querySelector("[type=submit]");
  const errorBox = form.querySelector(".err-text");
  button.disabled = true;
  errorBox.textContent = "";
  try {
    const result = await action(formValues(form));
    toast(successMessage);
    return result;
  } catch (error) {
    errorBox.textContent = error.message;
    return null;
  } finally {
    button.disabled = false;
  }
}
