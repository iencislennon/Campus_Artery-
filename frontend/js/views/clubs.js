import { api } from "../api.js";
import { empty, esc, readAttachment, submitForm, toast, withState } from "../ui.js?v=3";

const today = () => new Date().toLocaleDateString("sv");
const upcomingEvents = (clubs) => clubs
  .filter((c) => c.event.name && (c.event.end || c.event.start || "9999-12-31") >= today())
  .sort((a, b) => (a.event.start || "9999").localeCompare(b.event.start || "9999"));

const eventWhen = ({ start, end, startTime, endTime }) => [[start, end], [startTime, endTime]]
  .map((pair) => pair.filter(Boolean).join(" – ")).filter(Boolean).join(" · ");

const eventCard = (c, i) => `<a class="item event-item" style="--i:${i}" href="#/clubs/manage?id=${esc(c.id)}">
  <div><b>${esc(c.event.name)}</b> <span class="meta">by ${esc(c.name)}</span></div>
  <span class="side-r"><span class="btn sm cta">${c.owned ? "Manage" : "Take a shot"}</span></span>
  ${eventWhen(c.event) ? `<span class="meta num">${esc(eventWhen(c.event))}</span>` : ""}</a>`;

const eventsSection = (clubs) => {
  const events = upcomingEvents(clubs);
  return events.length ? `<h2 class="section-title">Upcoming events</h2><div class="list">${events.map(eventCard).join("")}</div>` : "";
};

export function renderClubs(root) {
  root.innerHTML = `<div class="head"><div><h1>Clubs &amp; deadlines</h1><p>Join a club and keep its next deadline in sight.</p></div><button class="btn cta" data-create-club>Register your club</button></div><div id="clubs"></div>`;
  const target = root.querySelector("#clubs");
  const load = () => withState(target, () => api.get("/clubs"), (clubs) => clubs.length
    ? `${eventsSection(clubs)}<h2 class="section-title">All clubs</h2><div class="list">${[...clubs].sort((a, b) => a.deadline.date.localeCompare(b.deadline.date)).map((c, i) => `
      <div class="item" style="--i:${i}"><div><a href="#/clubs/manage?id=${esc(c.id)}"><b>${esc(c.name)}</b></a> <span class="meta num">${c.members} members</span></div>
      <span class="side-r"><button class="btn sm ${c.joined ? "" : "cta"}" data-toggle="${esc(c.id)}">${c.joined ? "Leave" : "Join"}</button>${c.owned ? `<a class="btn sm" href="#/clubs/manage?id=${esc(c.id)}">Manage</a>` : ""}</span>
      <span class="meta">${esc(c.deadline.title)} · <time>${esc(c.deadline.date)}</time></span></div>`).join("")}</div>`
    : empty("No clubs yet"));
  root.querySelector("[data-create-club]").addEventListener("click", () => {
    if (root.querySelector("[data-club-form]")) return;
    target.insertAdjacentHTML("afterbegin", `<form class="form panel" data-club-form novalidate>
      <h2>Register your club</h2>
      <label>Club name<input name="name" required maxlength="80" placeholder="Robotics Club"></label>
      <label>Registration document <input name="document" type="file" required accept=".pdf,.png,.jpg,.jpeg,.webp,.txt"></label>
      <p class="meta">AI will check the document format and integrity before the club is created. Maximum 5 MB.</p>
      <p class="err-text" role="alert"></p><div class="tags"><button class="btn cta" type="submit">Create club</button><button class="btn" type="button" data-cancel-club>Cancel</button></div>
    </form>`);
    target.querySelector("[data-cancel-club]").addEventListener("click", () => target.querySelector("[data-club-form]").remove());
    target.querySelector("[data-club-form]").addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const saved = await submitForm(form, async (values) => {
        const document = await readAttachment(form.elements.document.files[0]);
        return api.post("/clubs", { ...values, document });
      }, "Club registered");
      if (saved) location.hash = `#/clubs/manage?id=${saved.id}`;
    });
  });
  target.addEventListener("click", async (event) => {
    const id = event.target.dataset?.toggle;
    if (!id) return;
    try { await api.post("/clubs/toggle", { id }); load(); } catch (error) { toast(error.message, "bad"); }
  });
  load();
}
