import { api } from "../api.js";
import { empty, esc, withState } from "../ui.js";

const KINDS = ["Classroom", "Barrel"];
const FLOORS = ["1", "2", "3"];

const blockBadge = (building, blocks) => {
  const block = blocks.find((b) => b.id === building);
  return `<span class="block-badge" style="--block:${esc(block.color)};--ink:${esc(block.ink)}" title="${esc(block.name)}">${esc(block.id)}</span>`;
};

export async function renderRooms(root) {
  const blocks = await api.get("/buildings").catch(() => []);
  root.innerHTML = `
    <div class="head"><div><h1>Free rooms</h1><p>Rooms with no class scheduled right now, from the SDU timetable.</p></div></div>
    <form class="filters" aria-label="Room filters">
      <label>Room<input name="q" type="search" placeholder="e.g. D117" autocomplete="off"></label>
      <label>Block<select name="building"><option value="">Any</option>${blocks.map((b) => `<option value="${esc(b.id)}">${esc(b.name)}</option>`).join("")}</select></label>
      <label>Floor<select name="floor"><option value="">Any</option>${FLOORS.map((f) => `<option>${f}</option>`).join("")}</select></label>
      <label>Type<select name="kind"><option value="">Any</option>${KINDS.map((k) => `<option>${k}</option>`).join("")}</select></label>
      <label>Min. capacity<input name="minCapacity" type="number" min="1" inputmode="numeric" placeholder="e.g. 30"></label>
    </form>
    <div id="results"></div>`;

  const form = root.querySelector("form");
  const search = () => withState(root.querySelector("#results"),
    () => api.get("/rooms", Object.fromEntries(new FormData(form))),
    (rooms) => rooms.length ? `<p class="muted count">${rooms.length} rooms</p><div class="list">${rooms.map((r, i) => `
      <div class="item" style="--i:${Math.min(i, 12)}"><div class="room-title">${blockBadge(r.building, blocks)}<b class="mono">${esc(r.id)}</b> <span class="muted">${esc(r.kind)}</span></div>
      <span class="chip ok">free until ${esc(r.freeUntil)}</span>
      <span class="meta">${esc(blocks.find((b) => b.id === r.building).name)}${r.floor ? ` · floor ${r.floor}` : ""} · seats ${r.capacity}</span></div>`).join("")}</div>`
      : empty("No rooms match", "Clear a filter or check the room code."));
  let timer;
  form.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(search, 150); });
  form.addEventListener("submit", (e) => e.preventDefault());
  search();
}
