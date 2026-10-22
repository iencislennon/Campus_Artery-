import { api } from "../api.js";
import { empty, esc, submitForm, toast, withState } from "../ui.js";

const CATEGORIES = ["Project", "Event", "Party", "Research"];

export function renderInitiatives(root) {
  root.innerHTML = `
    <div class="head"><div><h1>Initiatives</h1><p>Projects, events and research run by students and teachers.</p></div>
      <a class="btn cta" href="#/initiatives/new">Start an initiative</a></div>
    <div class="filters"><label>Search<input type="search" id="q" placeholder="Title"></label>
      <label>Category<select id="cat"><option value="">All</option>${CATEGORIES.map((c) => `<option>${c}</option>`).join("")}</select></label>
      <label>Recommend by<select id="strategy"><option value="popularity">Popularity</option><option value="recent">Recent activity</option><option value="category">Categories I follow</option></select></label></div>
    <h2 style="margin-bottom:10px">Recommended</h2><div id="picks" style="margin-bottom:28px"></div>
    <h2 style="margin-bottom:10px">All initiatives</h2><div id="all"></div>`;

  const q = root.querySelector("#q"), cat = root.querySelector("#cat"), strategy = root.querySelector("#strategy");
  const card = (n, i) => `<div class="item" style="--i:${i}"><div><b>${esc(n.title)}</b> <span class="chip">${esc(n.category)}</span></div>
    <span class="side-r"><span class="meta num">${n.followers}</span><button class="btn sm" data-follow="${esc(n.id)}" aria-pressed="${n.following}">${n.following ? "Following" : "Follow"}</button></span>
    <span class="meta">${esc(n.summary)} · <a href="mailto:${esc(n.contact)}" style="color:var(--link)">${esc(n.organizer)}</a></span></div>`;
  const list = (items) => items.length ? `<div class="list">${items.map(card).join("")}</div>` : empty("No initiatives found", "Try another category or start your own.");

  const loadAll = () => withState(root.querySelector("#all"), () => api.get("/initiatives", { q: q.value, category: cat.value }), list);
  const loadPicks = () => withState(root.querySelector("#picks"), () => api.get("/recommendations", { strategy: strategy.value }), list);
  const reload = () => { loadAll(); loadPicks(); };

  q.addEventListener("input", loadAll); cat.addEventListener("change", loadAll); strategy.addEventListener("change", loadPicks);
  root.addEventListener("click", async (event) => {
    const id = event.target.dataset?.follow;
    if (!id) return;
    try { await api.post("/initiatives/follow", { id }); reload(); } catch (error) { toast(error.message, "bad"); }
  });
  reload();
}

export function renderNewInitiative(root) {
  root.innerHTML = `<div class="new-initiative-page"><a class="link" href="#/initiatives">← Back to initiatives</a><form class="form panel" id="create"><h2>New initiative</h2>
      <label>Title<input name="title" required></label>
      <label>Category<select name="category">${CATEGORIES.map((c) => `<option>${c}</option>`).join("")}</select></label>
      <label>Summary<textarea name="summary" required></textarea></label>
      <label>Contact email<input name="contact" type="email" required></label>
      <p class="err-text" role="alert"></p><button class="btn cta" type="submit">Publish</button></form></div>`;
  const form = root.querySelector("#create");
  form.querySelector("input").focus();
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.checkValidity()) { form.querySelector(".err-text").textContent = "Title, summary and a valid contact email are required."; return; }
    if (await submitForm(form, (v) => api.post("/initiatives", v), "Initiative published")) location.hash = "#/initiatives";
  });
}
