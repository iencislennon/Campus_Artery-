import { api } from "../api.js";
import { esc, statusChip, withState } from "../ui.js";

export function renderDashboard(root, { user }) {
  root.innerHTML = `
    <div class="head"><div><h1>Hello, ${esc(user.name.split(" ")[0])}</h1><p>Here is what is happening on campus right now.</p></div></div>
    <div id="dash"></div>`;

  const load = () => user.role === "TEACHER"
    ? Promise.all([api.get("/recommendations", { strategy: "popularity" }), api.get("/office-hours")])
    : Promise.all([api.get("/rooms"), api.get("/issues"), api.get("/recommendations", { strategy: "popularity" }), api.get("/clubs")]);
  withState(root.querySelector("#dash"), load, (data) => {
    const picks = user.role === "TEACHER" ? data[0] : data[2];
    const initiatives = `<section><h2>Popular initiatives</h2><div class="list">${picks.map((n, i) => `
      <a class="item" style="--i:${i};text-decoration:none;color:inherit" href="#/initiatives"><div><b>${esc(n.title)}</b></div>
      <span class="chip">${esc(n.category)}</span><span class="meta">${n.followers} followers</span></a>`).join("")}</div></section>`;
    if (user.role === "TEACHER") return `<div class="stats"><a class="stat" href="#/teachers"><span class="num">${data[1].length}</span><span>published office hours</span></a><a class="stat" href="#/initiatives"><span class="num">${picks.length}</span><span>recommended initiatives</span></a></div><div class="teacher-dashboard">${initiatives}<section><h2>Teacher tools</h2><p class="muted">Set your office hours so students can book a consultation.</p><a class="btn cta" href="#/teachers">Set office hours</a></section></div>`;
    const [rooms, issues, , clubs] = data;
    return `<div class="stats"><a class="stat" href="#/rooms"><span class="num">${rooms.length}</span><span>rooms free now</span></a><a class="stat" href="#/issues"><span class="num">${issues.filter((i) => i.status !== "RESOLVED").length}</span><span>of your reports still open</span></a><a class="stat" href="#/clubs"><span class="num">${clubs.filter((c) => c.joined).length}</span><span>clubs you belong to</span></a></div><div class="cols">${initiatives}<section><h2>Your latest reports</h2><div class="list">${issues.slice(0, 3).map((i, k) => `<div class="item" style="--i:${k}"><div><b>${esc(i.category)}</b> · <span class="mono">${esc(i.roomId)}</span></div>${statusChip(i.status)}<span class="meta">${esc(i.description)}</span></div>`).join("") || '<p class="muted">No reports yet.</p>'}</div></section></div>`;
  });
}
