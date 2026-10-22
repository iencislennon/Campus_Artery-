import { api } from "../api.js";
import { empty, esc, readAttachment, statusChip, submitForm, toast, withState } from "../ui.js?v=2";

export async function renderIssues(root) {
  const [rooms, categories] = await Promise.all([api.get("/rooms"), api.get("/issue-categories")]).catch(() => [[], []]);
  root.innerHTML = `
    <div class="head"><div><h1>FixMyCampus</h1><p>Report a broken thing and follow it until it is fixed.</p></div></div>
    <div class="cols"><section><h2>New report</h2>
      <form class="form panel" novalidate>
        <div class="row2"><label>Room<select name="roomId" required>${rooms.map((r) => `<option>${esc(r.id)}</option>`).join("")}</select></label>
        <label>Category<select name="category" required>${categories.map((c) => `<option>${esc(c)}</option>`).join("")}</select></label></div>
        <label>What is wrong?<textarea name="description" required minlength="10" placeholder="Describe the problem so it can be fixed on the first visit."></textarea></label>
        <label>Evidence file <input name="attachment" type="file" accept=".pdf,.png,.jpg,.jpeg,.webp,.txt"></label>
        <p class="meta">AI checks that the report is meaningful and validates the optional evidence file.</p>
        <p class="err-text" role="alert"></p><button class="btn cta" type="submit">Submit report</button>
      </form></section>
    <section><h2>Your reports</h2><div id="history"></div></section></div>`;

  const history = root.querySelector("#history");
  const loadHistory = () => withState(history, () => api.get("/issues"), (list) => list.length
    ? `<div class="list">${list.map((i, k) => `<div class="item" style="--i:${k}"><div><b>${esc(i.category)}</b> · <span class="mono">${esc(i.roomId)}</span></div>
      ${statusChip(i.status)}<span class="meta">${esc(i.description)} · <time>${esc(i.createdAt)}</time></span></div>`).join("")}</div>`
    : empty("Nothing reported", "Reports you submit will appear here."));

  const form = root.querySelector("form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!form.checkValidity()) { form.querySelector(".err-text").textContent = "Describe the problem in at least 10 characters."; return; }
    const saved = await submitForm(form, async (v) => {
      const attachment = await readAttachment(form.elements.attachment.files[0]);
      return api.post("/issues", { ...v, attachment });
    }, "Report submitted");
    if (!saved) return;
    if (saved.warning) toast(saved.warning);
    form.reset();
    loadHistory();
  });
  loadHistory();
}
