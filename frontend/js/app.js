import { clearUser, getUser, setUser } from "./session.js?v=2";
import { enhanceSelects } from "./select.js";
import { esc, icon, logo } from "./ui.js";
import { renderAuth } from "./views/login.js?v=6";
import { renderDashboard } from "./views/dashboard.js?v=2";
import { renderRooms } from "./views/rooms.js";
import { renderTeachers } from "./views/teachers.js?v=2";
import { renderIssues } from "./views/issues.js?v=2";
import { renderInitiatives, renderNewInitiative } from "./views/initiatives.js?v=2";
import { renderClubs } from "./views/clubs.js?v=8";
import { renderClubPage } from "./views/club-page.js?v=3";
import { renderSurveyEditor, renderSurveyFill, renderSurveyResults } from "./views/surveys.js?v=2";
import { renderProfile } from "./views/profile.js?v=5";

const ROUTES = [
  { path: "/", label: "Dashboard", icon: "home", group: "", render: renderDashboard },
  { path: "/rooms", label: "Free rooms", icon: "door", group: "CampusPlus", render: renderRooms, studentOnly: true },
  { path: "/teachers", label: "Set office hours", icon: "user", group: "CampusPlus", render: renderTeachers },
  { path: "/issues", label: "Report an issue", icon: "wrench", group: "FixMyCampus", render: renderIssues, studentOnly: true },
  { path: "/initiatives", label: "Initiatives", icon: "spark", group: "ProjectForge", render: renderInitiatives },
  { path: "/initiatives/new", label: "New initiative", icon: "spark", group: "ProjectForge", render: renderNewInitiative, hideNav: true },
  { path: "/clubs", label: "Clubs", icon: "users", group: "ProjectForge", render: renderClubs, studentOnly: true },
  { path: "/clubs/manage", label: "Club", icon: "users", group: "ProjectForge", render: renderClubPage, hideNav: true, studentOnly: true, parent: "/clubs" },
  { path: "/surveys/edit", label: "Survey", icon: "users", group: "ProjectForge", render: renderSurveyEditor, hideNav: true, studentOnly: true, parent: "/clubs" },
  { path: "/surveys/fill", label: "Survey", icon: "users", group: "ProjectForge", render: renderSurveyFill, hideNav: true, studentOnly: true, parent: "/clubs" },
  { path: "/surveys/results", label: "Survey results", icon: "users", group: "ProjectForge", render: renderSurveyResults, hideNav: true, studentOnly: true, parent: "/clubs" },
  { path: "/profile", label: "Profile", icon: "id", group: "Account", render: renderProfile },
];

const app = document.getElementById("app");
const prefersReducedMotion = () => matchMedia("(prefers-reduced-motion: reduce)").matches;
const hashPath = () => location.hash.replace(/^#/, "").split("?")[0] || "/";
const hashQuery = () => new URLSearchParams(location.hash.split("?")[1] ?? "");
const currentRoute = (user) => {
  const path = hashPath();
  const available = ROUTES.filter((r) => !r.studentOnly || user.role !== "TEACHER");
  return available.find((r) => r.path === path) ?? ROUTES[0];
};

function navHtml(user) {
  let lastGroup = null;
  return ROUTES.filter((r) => !r.hideNav && (!r.studentOnly || user.role !== "TEACHER")).map((r) => {
    const heading = r.group && r.group !== lastGroup ? `<div class="nav-group">${r.group}</div>` : "";
    lastGroup = r.group || lastGroup;
    return `${heading}<a href="#${r.path}" data-path="${r.path}">${icon(r.icon)}${r.label}</a>`;
  }).join("");
}

function buildShell(user) {
  app.innerHTML = `
    <div class="topbar">${logo()}<button class="btn sm" data-menu aria-label="Open menu" style="width:40px;padding:0;display:grid;place-items:center;color:#fff;border-color:rgba(255,255,255,.4)">${icon("menu")}</button></div>
    <div class="shell">
      <aside class="side">${logo()}
        <nav class="nav" aria-label="Main">${navHtml(user)}</nav>
        <div class="user"><b data-user-name>${esc(user.name)}</b><span class="role">${esc(user.role)}</span><button class="btn sm" data-logout>Sign out</button></div></aside>
      <main class="main" id="main"></main>
    </div>`;
  const side = app.querySelector(".side");
  app.querySelector("[data-menu]").addEventListener("click", () => side.classList.toggle("open"));
  app.querySelector("[data-logout]").addEventListener("click", () => { clearUser(); location.hash = "#/"; start(); });
  app.querySelector(".nav").addEventListener("click", () => side.classList.remove("open"));
}

// Swaps only the page content: the shell stays mounted and the swap is a view transition.
function showRoute() {
  const user = getUser();
  const route = currentRoute(user);
  const swap = () => {
    app.querySelectorAll(".nav a").forEach((a) => (a.dataset.path === (route.parent ?? route.path)
      ? a.setAttribute("aria-current", "page") : a.removeAttribute("aria-current")));
    const main = app.querySelector("#main");
    main.innerHTML = "";
    route.render(main, {
      user,
      query: hashQuery(),
      onProfileChange: (profile) => {
        setUser({ ...user, name: profile.name });
        app.querySelector("[data-user-name]").textContent = profile.name;
      },
    });
    document.title = `${route.label} · SDU Bağdar`;
    window.scrollTo(0, 0);
  };
  if (document.startViewTransition && !prefersReducedMotion()) document.startViewTransition(swap);
  else swap();
}

function start() {
  const user = getUser();
  if (!user) return renderAuth(app, start);
  buildShell(user);
  showRoute();
}

window.addEventListener("hashchange", () => { if (getUser() && app.querySelector("#main")) showRoute(); });
new MutationObserver(() => enhanceSelects(app)).observe(app, { childList: true, subtree: true });
start();
