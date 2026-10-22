import { api } from "../api.js";
import { esc, failure, loading, readAttachment, submitForm, toast } from "../ui.js?v=3";

const KINDS = { short: "Short answer", file: "Document upload", choice: "Multiple choice", checkbox: "Checkboxes" };
const hasOptions = (kind) => kind === "choice" || kind === "checkbox";
const blankQuestion = () => ({ id: "", text: "", kind: "short", options: [], required: false });
const back = (clubId) => { location.hash = `#/clubs/manage?id=${clubId}`; };

async function loadInto(target, load, render) {
  target.innerHTML = loading();
  try { render(await load()); } catch (error) { target.innerHTML = failure(error.message); }
}

/* ---------- editor (create and edit) ---------- */

const optionRow = (option, i) => `<div class="opt-row"><input data-opt="${i}" value="${esc(option)}" placeholder="Option ${i + 1}" aria-label="Option ${i + 1}">
  <button class="btn sm" type="button" data-act="remove-option" data-opt="${i}" aria-label="Remove option">×</button></div>`;

const questionCard = (q, i, total) => `<fieldset class="panel qcard" data-q="${i}">
  <div class="row2"><label>Question<input data-field="text" value="${esc(q.text)}" maxlength="200" placeholder="Untitled question"></label>
  <label>Type<select data-field="kind">${Object.entries(KINDS).map(([k, label]) => `<option value="${k}"${k === q.kind ? " selected" : ""}>${label}</option>`).join("")}</select></label></div>
  ${hasOptions(q.kind) ? `<div class="opts">${q.options.map(optionRow).join("")}<button class="btn sm" type="button" data-act="add-option">Add option</button></div>` : ""}
  <div class="qbar"><label class="switch">Required<input type="checkbox" data-field="required"${q.required ? " checked" : ""}></label>
  <span class="side-r"><button class="btn sm" type="button" data-act="up"${i === 0 ? " disabled" : ""}>Up</button>
  <button class="btn sm" type="button" data-act="down"${i === total - 1 ? " disabled" : ""}>Down</button>
  <button class="btn sm" type="button" data-act="copy">Duplicate</button>
  <button class="btn sm" type="button" data-act="delete"${total === 1 ? " disabled" : ""}>Delete</button></span></div></fieldset>`;

export function renderSurveyEditor(root, { query }) {
  root.innerHTML = `<div id="editor"></div>`;
  const target = root.querySelector("#editor");
  const editingId = query.get("id");
  const load = () => (editingId ? api.get("/surveys", { id: editingId })
    : Promise.resolve({ clubId: query.get("club"), title: "", description: "", questions: [blankQuestion()] }));
  loadInto(target, load, (survey) => mountEditor(target, survey, editingId));
}

function mountEditor(target, survey, editingId) {
  const state = { title: survey.title, description: survey.description, questions: survey.questions.map((q) => ({ ...q })) };
  const paint = () => {
    target.innerHTML = `<div class="head"><div><h1>${editingId ? "Edit survey" : "Create survey"}</h1><p>Build it like a form: add questions, pick their type, reorder them.</p></div>
      <a class="btn" href="#/clubs/manage?id=${esc(survey.clubId)}">Cancel</a></div>
      <form class="stack" data-survey-form novalidate>
      <div class="panel form"><label>Survey title<input data-meta="title" maxlength="120" value="${esc(state.title)}" placeholder="Untitled survey"></label>
      <label>Description<textarea data-meta="description" placeholder="Optional">${esc(state.description)}</textarea></label></div>
      ${state.questions.map((q, i) => questionCard(q, i, state.questions.length)).join("")}
      <button class="btn" type="button" data-act="add-question">Add question</button>
      <p class="err-text" role="alert"></p><div class="tags"><button class="btn cta" type="submit">Save survey</button></div></form>`;
  };
  const withQuestion = (event) => {
    const card = event.target.closest("[data-q]");
    return card ? { index: Number(card.dataset.q), q: state.questions[Number(card.dataset.q)] } : {};
  };
  const swap = (a, b) => ([state.questions[a], state.questions[b]] = [state.questions[b], state.questions[a]]);
  const actions = {
    "add-question": () => state.questions.push(blankQuestion()),
    "add-option": ({ q }) => q.options.push(""),
    "remove-option": ({ q }, el) => q.options.splice(Number(el.dataset.opt), 1),
    up: ({ index }) => swap(index, index - 1),
    down: ({ index }) => swap(index, index + 1),
    copy: ({ index, q }) => state.questions.splice(index + 1, 0, { ...q, id: "", options: [...q.options] }),
    delete: ({ index }) => state.questions.splice(index, 1),
  };

  target.addEventListener("input", (event) => {
    const { q } = withQuestion(event);
    const el = event.target;
    if (el.dataset.meta) state[el.dataset.meta] = el.value;
    else if (q && el.dataset.opt !== undefined) q.options[Number(el.dataset.opt)] = el.value;
    else if (q && el.dataset.field === "kind") {
      q.kind = el.value;
      if (hasOptions(q.kind) && q.options.length < 2) q.options = ["Option 1", "Option 2"];
      paint();
    } else if (q && el.dataset.field === "required") q.required = el.checked;
    else if (q) q[el.dataset.field] = el.value;
  });
  target.addEventListener("click", (event) => {
    const button = event.target.closest("[data-act]");
    if (!button) return;
    actions[button.dataset.act](withQuestion(event), button);
    paint();
  });
  target.addEventListener("submit", async (event) => {
    event.preventDefault();
    const body = { title: state.title, description: state.description, questions: state.questions };
    const saved = await submitForm(event.target, () => (editingId
      ? api.put("/surveys", { ...body, id: editingId })
      : api.post("/surveys", { ...body, clubId: survey.clubId })), "Survey saved");
    if (saved) back(survey.clubId);
  });
  paint();
}

/* ---------- respondent form ---------- */

const answerField = (q) => {
  const label = `${esc(q.text)}${q.required ? ' <span class="req" aria-hidden="true">*</span>' : ""}`;
  if (q.kind === "file") return `<label>${label}<input name="${esc(q.id)}" type="file" accept=".pdf,.png,.jpg,.jpeg,.webp,.txt"></label><p class="meta">PDF, image or TXT, up to 5 MB.</p>`;
  if (q.kind === "short") return `<label>${label}<input name="${esc(q.id)}" maxlength="2000"></label>`;
  const type = q.kind === "choice" ? "radio" : "checkbox";
  return `<fieldset class="choices"><legend>${label}</legend>${q.options.map((o) => `<label class="choice"><input type="${type}" name="${esc(q.id)}" value="${esc(o)}">${esc(o)}</label>`).join("")}</fieldset>`;
};

export function renderSurveyFill(root, { query }) {
  root.innerHTML = `<div id="fill"></div>`;
  const target = root.querySelector("#fill");
  loadInto(target, () => api.get("/surveys", { id: query.get("id") }), (survey) => {
    target.innerHTML = `<div class="head"><div><h1>${esc(survey.title)}</h1>${survey.description ? `<p>${esc(survey.description)}</p>` : ""}</div>
      <a class="btn" href="#/clubs/manage?id=${esc(survey.clubId)}">Back</a></div>
      <form class="stack" data-fill-form novalidate>${survey.questions.map((q) => `<div class="panel form">${answerField(q)}</div>`).join("")}
      <p class="err-text" role="alert"></p><div class="tags"><button class="btn cta" type="submit">Submit</button></div></form>`;
    target.querySelector("form").addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget;
      const saved = await submitForm(form, async () => {
        const data = new FormData(form);
        const entries = await Promise.all(survey.questions.map(async (q) => {
          const values = data.getAll(q.id);
          if (q.kind === "file") return [q.id, await readAttachment(values[0]) ?? ""];
          return [q.id, q.kind === "checkbox" ? values : values[0] ?? ""];
        }));
        return api.post("/surveys/respond", { id: survey.id, answers: Object.fromEntries(entries) });
      }, "Response sent");
      if (saved) back(survey.clubId);
    });
  });
}

/* ---------- owner results ---------- */

const bar = (label, count, total) => `<div class="bar-row"><span>${esc(label)}</span>
  <span class="bar" style="--w:${total ? Math.round((count / total) * 100) : 0}%"></span><span class="num">${count}</span></div>`;

const fileList = (q) => q.files.length
  ? `<ul class="texts">${q.files.map((f) => `<li><button class="btn sm" data-download data-q="${esc(q.id)}" data-n="${f.n}">${esc(f.filename)}</button></li>`).join("")}</ul>`
  : '<p class="meta">No files yet.</p>';

const resultBlock = (q, total) => `<div class="panel"><h2>${esc(q.text)}</h2>${q.counts
  ? Object.entries(q.counts).map(([option, count]) => bar(option, count, total)).join("")
  : q.files ? fileList(q)
  : q.texts.length ? `<ul class="texts">${q.texts.map((t) => `<li>${esc(t)}</li>`).join("")}</ul>` : '<p class="meta">No answers yet.</p>'}</div>`;

export function renderSurveyResults(root, { query }) {
  root.innerHTML = `<div id="results"></div>`;
  const target = root.querySelector("#results");
  loadInto(target, async () => {
    const [results, survey] = await Promise.all([api.get("/surveys/results", { id: query.get("id") }), api.get("/surveys", { id: query.get("id") })]);
    return { ...results, clubId: survey.clubId };
  }, (results) => {
    target.innerHTML = `<div class="head"><div><h1>${esc(results.title)}</h1><p><span class="num">${results.total}</span> responses</p></div>
      <a class="btn" href="#/clubs/manage?id=${esc(results.clubId)}">Back</a></div><div class="stack">${results.questions.map((q) => resultBlock(q, results.total)).join("")}</div>`;
    target.addEventListener("click", (event) => downloadAnswer(event, query.get("id")));
  });
}

async function downloadAnswer(event, surveyId) {
  const button = event.target.closest("[data-download]");
  if (!button) return;
  try {
    const file = await api.get("/surveys/file", { id: surveyId, question: button.dataset.q, n: button.dataset.n });
    const bytes = Uint8Array.from(atob(file.contentBase64), (c) => c.charCodeAt(0));
    const link = Object.assign(document.createElement("a"), { href: URL.createObjectURL(new Blob([bytes], { type: file.mimeType })), download: file.filename });
    link.click();
    URL.revokeObjectURL(link.href);
  } catch (error) { toast(error.message, "bad"); }
}
