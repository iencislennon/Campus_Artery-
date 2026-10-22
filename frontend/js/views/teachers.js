import { api } from "../api.js";
import { empty, esc, submitForm, toast, withState } from "../ui.js";

const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri"];
const STRATEGIES = [
  ["first", "First available"],
  ["deadline", "Before my deadline"],
  ["preferred", "Closest to my preferred time"],
];

const options = (items) => items.map((v) => `<option>${v}</option>`).join("");

export function renderTeachers(root, { user }) {
  const isTeacher = user.role === "TEACHER";
  if (isTeacher) return renderTeacherOffice(root);
  root.innerHTML = `
    <div class="head"><div><h1>Teachers &amp; office hours</h1><p>Find an office and book a consultation.</p></div></div>
    <label style="max-width:320px;margin-bottom:20px">Search teachers<input type="search" id="q" placeholder="Name"></label>
    <div class="cols"><section><h2>Directory</h2><div id="dir"></div></section>
    <section><h2>Office hours</h2><div id="hours"></div>
    ${studentAutoBook()}
    </section></div>`;

  const dir = root.querySelector("#dir");
  const hours = root.querySelector("#hours");
  const teacherSelect = root.querySelector("#auto select[name=teacherId]");

  const loadDir = () => withState(dir, () => api.get("/teachers", { q: root.querySelector("#q").value }), (list) => {
    if (teacherSelect) fillTeachers(teacherSelect, list);
    return list.length ? `<div class="list">${list.map((t, i) => `<div class="item" style="--i:${i}"><div><b>${esc(t.name)}</b></div>
      <span class="chip mono">${esc(t.office)}</span><span class="meta">${esc(t.department)}</span></div>`).join("")}</div>` : empty("No teachers found");
  });
  root.querySelector("#q").addEventListener("input", loadDir);

  const loadHours = () => withState(hours, () => api.get("/office-hours"), (list) => list.length ? `<div class="list">${list.map((h, i) => {
    const full = h.booked >= h.capacity;
    const label = h.bookedByMe ? "Booked" : full ? "Full" : "Book";
    return `<div class="item" style="--i:${i}"><div><b>${esc(h.teacher)}</b></div>
      <span class="side-r"><span class="chip ${full ? "bad" : "ok"}">${h.booked}/${h.capacity}</span>
      <button class="btn sm" data-book="${esc(h.id)}" ${full || h.bookedByMe || isTeacher ? "disabled" : ""}>${label}</button></span>
      <span class="meta mono">${esc(h.day)} ${esc(h.start)}–${esc(h.end)}</span></div>`;
  }).join("")}</div>` : empty("No office hours yet", isTeacher ? "Add a slot below." : "Teachers have not published any."));

  hours.addEventListener("click", async (event) => {
    const id = event.target.dataset?.book;
    if (!id) return;
    event.target.disabled = true;
    try { await api.post("/office-hours/book", { id }); toast("Booked"); }
    catch (error) { toast(error.message, "bad"); }
    loadHours();
  });

  root.querySelector("#slot")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (await submitForm(event.currentTarget, (v) => api.post("/office-hours", v), "Office hour created")) loadHours();
  });
  root.querySelector("#office")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (await submitForm(event.currentTarget, (v) => api.put("/teachers/me", v), "Office saved")) loadDir();
  });
  root.querySelector("#auto")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const booked = await submitForm(event.currentTarget, (v) => api.post("/office-hours/book", v), "Booked automatically");
    if (booked) { toast(`${booked.day} ${booked.start}, ${booked.teacher}`); loadHours(); }
  });
  loadDir(); loadHours();
}

function renderTeacherOffice(root) {
  root.innerHTML = `<div class="teacher-office-only"><h2>Your office</h2><form class="form panel" id="office" novalidate>
      <div class="row2"><label>Department<input name="department" required></label><label>Room<input name="office" placeholder="e.g. E301" required></label></div>
      <p class="err-text" role="alert"></p><button class="btn" type="submit">Save office</button></form>
    <h2>Add a slot</h2><form class="form panel" id="slot">
      <label>Day<select name="day">${options(DAYS)}</select></label>
      <div class="row2"><label>Start<input name="start" type="time" required></label><label>End<input name="end" type="time" required></label></div>
      <label>Capacity<input name="capacity" type="number" min="1" max="20" value="4" required></label>
      <p class="err-text" role="alert"></p><button class="btn cta" type="submit">Create slot</button></form></div>`;
  root.querySelector("#slot").addEventListener("submit", async (event) => {
    event.preventDefault();
    await submitForm(event.currentTarget, (values) => api.post("/office-hours", values), "Office hour created");
  });
  root.querySelector("#office").addEventListener("submit", async (event) => {
    event.preventDefault();
    await submitForm(event.currentTarget, (values) => api.put("/teachers/me", values), "Office saved");
  });
}

function fillTeachers(select, teachers) {
  select.innerHTML = teachers.map((t) => `<option value="${esc(t.id)}">${esc(t.name)}</option>`).join("");
  select.dispatchEvent(new Event("reset-select"));
}

function teacherForms() {
  return `
    <h2 style="margin-top:24px">Your office</h2><form class="form panel" id="office" novalidate>
      <div class="row2"><label>Department<input name="department" required></label><label>Room<input name="office" placeholder="e.g. E301" required></label></div>
      <p class="err-text" role="alert"></p><button class="btn" type="submit">Save office</button></form>
    <h2 style="margin-top:24px">Add a slot</h2><form class="form panel" id="slot">
      <label>Day<select name="day">${options(DAYS)}</select></label>
      <div class="row2"><label>Start<input name="start" type="time" required></label><label>End<input name="end" type="time" required></label></div>
      <label>Capacity<input name="capacity" type="number" min="1" max="20" value="4" required></label>
      <p class="err-text" role="alert"></p><button class="btn cta" type="submit">Create slot</button></form>`;
}

function studentAutoBook() {
  return `
    <h2 style="margin-top:24px">Let SDU Bağdar pick a slot</h2><form class="form panel" id="auto">
      <label>Teacher<select name="teacherId"></select></label>
      <label>Policy<select name="strategy">${STRATEGIES.map(([v, l]) => `<option value="${v}">${l}</option>`).join("")}</select></label>
      <div class="row2"><label>Deadline<input name="deadline" type="date"></label><label>Preferred time<input name="preferredTime" type="time"></label></div>
      <p class="err-text" role="alert"></p><button class="btn cta" type="submit">Find and book</button></form>`;
}
