import { api } from "../api.js";
import { getLastEmail } from "../session.js?v=3";
import { esc, readImage, submitForm, withState } from "../ui.js?v=3";

export function renderProfile(root, ctx) {
  root.innerHTML = `<div id="profile"></div>`;
  const target = root.querySelector("#profile");

  withState(target, () => api.get("/profile"), (data) => {
    queueMicrotask(() => bind(target, data, ctx));
    return template(data);
  });
}

function template({ profile }) {
  const email = getLastEmail() || profile.email;
  return `
    <section><h2>Contacts</h2>
      <form class="form panel" id="contacts" novalidate>
        <label>Full name<input name="name" value="${esc(profile.name)}" required></label>
        <label>University email<input name="email" type="email" autocomplete="email" value="${esc(email)}" required></label>
        <div class="row2"><label>Phone<input name="phone" type="tel" value="${esc(profile.phone)}"></label>
        <label>Telegram<input name="telegram" value="${esc(profile.telegram)}" placeholder="@username"></label></div>
        <label>Photo<input name="photo" type="file" accept="image/*"></label>
        <p class="err-text" role="alert"></p><button class="btn cta" type="submit">Save changes</button>
      </form></section>`;
}

function bind(target, _data, { onProfileChange }) {
  target.querySelector("#contacts").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.checkValidity()) { form.querySelector(".err-text").textContent = "Name and a valid email are required."; return; }
    const saved = await submitForm(form, async (values) => {
      const { photo, ...fields } = values;
      return api.put("/profile", photo?.size ? { ...fields, photo: await readImage(photo) } : fields);
    }, "Profile saved");
    if (saved) onProfileChange(saved);
  });
}
