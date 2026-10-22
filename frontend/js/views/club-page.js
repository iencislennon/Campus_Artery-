import { api } from "../api.js";
import { empty, esc, readImage, submitForm, toast, withState } from "../ui.js?v=3";

const formatDate = (iso) => (iso ? new Date(`${iso}T00:00`).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "");
const dateRange = ({ start, end }) => [start, end].filter(Boolean).map(formatDate).join(" – ");
const timeRange = ({ startTime, endTime }) => [startTime, endTime].filter(Boolean).join(" – ");

const eventCard = (club) => `<article class="panel club-cover">
  ${club.event.photo ? `<img src="${esc(club.event.photo)}" alt="" class="cover-img">` : ""}
  <h2>${esc(club.event.name || "No event yet")}</h2>
  ${dateRange(club.event) ? `<p class="meta num">${esc(dateRange(club.event))}</p>` : ""}
  ${timeRange(club.event) ? `<p class="meta num">${esc(timeRange(club.event))}</p>` : ""}
  ${club.event.description ? `<p>${esc(club.event.description)}</p>` : ""}</article>`;

const eventForm = (club) => `<form class="form panel" data-event-form novalidate>
  <h2>Event details</h2>
  <label>Photo<input name="photo" type="file" accept="image/*"></label>
  <label>Event name<input name="eventName" maxlength="120" value="${esc(club.event.name)}" placeholder="Robotics Open Day"></label>
  <div class="row2"><label>Start date<input name="startDate" type="date" value="${esc(club.event.start)}"></label>
  <label>End date<input name="endDate" type="date" value="${esc(club.event.end)}"></label></div>
  <div class="row2"><label>From time (optional)<input name="startTime" type="time" value="${esc(club.event.startTime)}"></label>
  <label>To time (optional)<input name="endTime" type="time" value="${esc(club.event.endTime)}"></label></div>
  <label>Description<textarea name="description" maxlength="2000" placeholder="What is happening and who should come?">${esc(club.event.description)}</textarea></label>
  <p class="err-text" role="alert"></p><div class="tags"><button class="btn cta" type="submit">Save event</button></div></form>`;

const surveyRow = (club, survey, i) => `<div class="item" style="--i:${i}">
  <div><b>${esc(survey.title)}</b> <span class="meta num">${survey.questions} questions · ${survey.responses} responses</span></div>
  <span class="side-r">${club.owned
    ? `<a class="btn sm" href="#/surveys/results?id=${esc(survey.id)}">Results</a>
       <a class="btn sm" href="#/surveys/edit?id=${esc(survey.id)}">Edit</a>
       <button class="btn sm" data-delete-survey="${esc(survey.id)}">Delete</button>`
    : `<a class="btn sm cta" href="#/surveys/fill?id=${esc(survey.id)}">${survey.responded ? "Edit answers" : "Fill in"}</a>`}</span>
  ${survey.description ? `<span class="meta">${esc(survey.description)}</span>` : ""}</div>`;

const surveySection = (club) => `<section><div class="head"><div><h2>Surveys</h2></div>
  ${club.owned ? `<a class="btn cta" href="#/surveys/edit?club=${esc(club.id)}">Create survey</a>` : ""}</div>
  ${club.surveys.length ? `<div class="list">${club.surveys.map((s, i) => surveyRow(club, s, i)).join("")}</div>` : empty("No surveys yet")}</section>`;

export function renderClubPage(root, { query }) {
  root.innerHTML = `<div id="club-page"></div>`;
  const target = root.querySelector("#club-page");
  const load = () => withState(target, () => api.get("/clubs/manage", { id: query.get("id") }), (club) => `
    <div class="head"><div><h1>${esc(club.name)}</h1><p>${club.owned ? "You run this club. Set up its event and surveys." : "Club event and surveys."}</p></div>
    <a class="btn" href="#/clubs">All clubs</a></div>
    <div class="stack">${club.owned ? eventForm(club) : eventCard(club)}${surveySection(club)}</div>`);
  load().then(() => bind(target, query.get("id"), load));
}

function bind(target, clubId, reload) {
  target.querySelector("[data-event-form]")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const saved = await submitForm(form, async ({ photo, ...values }) => {
      const body = { id: clubId, ...values };
      if (photo?.size) body.photo = await readImage(photo);
      return api.put("/clubs/manage", body);
    }, "Event saved");
    if (saved) reload().then(() => bind(target, clubId, reload));
  });
  target.addEventListener("click", async (event) => {
    const id = event.target.dataset?.deleteSurvey;
    if (!id || !confirm("Delete this survey and all its responses?")) return;
    try { await api.post("/surveys/delete", { id }); toast("Survey deleted"); reload().then(() => bind(target, clubId, reload)); }
    catch (error) { toast(error.message, "bad"); }
  });
}
