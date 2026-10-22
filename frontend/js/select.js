// Replaces native <select> popups (unstyled, mispositioned on macOS) with an
// accessible listbox. The native select stays in the DOM as the source of truth,
// so FormData, `.value` and change/input listeners keep working.
let openMenu = null;

function closeMenu() {
  if (!openMenu) return;
  openMenu.root.classList.remove("open");
  openMenu.trigger.setAttribute("aria-expanded", "false");
  openMenu = null;
}

function enhance(select) {
  if (select.dataset.enhanced) return;
  select.dataset.enhanced = "true";

  const root = document.createElement("div");
  root.className = "select";
  const trigger = document.createElement("button");
  trigger.type = "button";
  trigger.className = "select-trigger";
  trigger.setAttribute("aria-haspopup", "listbox");
  trigger.setAttribute("aria-expanded", "false");
  const menu = document.createElement("ul");
  menu.className = "select-menu";
  menu.setAttribute("role", "listbox");

  select.before(root);
  root.append(select, trigger, menu);
  select.classList.add("select-native");
  select.tabIndex = -1;

  const options = () => [...select.options];
  const label = () => select.selectedOptions[0]?.textContent ?? "";
  let active = 0;

  const paint = () => {
    trigger.textContent = label();
    menu.innerHTML = "";
    options().forEach((option, index) => {
      const item = document.createElement("li");
      item.setAttribute("role", "option");
      item.textContent = option.textContent;
      item.dataset.index = index;
      if (option.selected) item.setAttribute("aria-selected", "true");
      if (index === active) item.classList.add("active");
      menu.append(item);
    });
  };
  const highlight = (index) => {
    active = Math.max(0, Math.min(options().length - 1, index));
    menu.querySelectorAll("li").forEach((li, i) => li.classList.toggle("active", i === active));
    menu.children[active]?.scrollIntoView({ block: "nearest" });
  };
  const choose = (index) => {
    select.selectedIndex = index;
    select.dispatchEvent(new Event("input", { bubbles: true }));
    select.dispatchEvent(new Event("change", { bubbles: true }));
    paint();
    closeMenu();
    trigger.focus();
  };
  const open = () => {
    closeMenu();
    active = select.selectedIndex;
    paint();
    root.classList.add("open");
    trigger.setAttribute("aria-expanded", "true");
    openMenu = { root, trigger };
    menu.children[active]?.scrollIntoView({ block: "nearest" });
  };

  trigger.addEventListener("click", () => (root.classList.contains("open") ? closeMenu() : open()));
  menu.addEventListener("click", (event) => {
    const item = event.target.closest("li");
    if (item) choose(Number(item.dataset.index));
  });
  trigger.addEventListener("keydown", (event) => {
    const isOpen = root.classList.contains("open");
    if (["ArrowDown", "ArrowUp", "Enter", " "].includes(event.key)) {
      event.preventDefault();
      if (!isOpen) return open();
      if (event.key === "ArrowDown") highlight(active + 1);
      else if (event.key === "ArrowUp") highlight(active - 1);
      else choose(active);
    } else if (event.key === "Escape" || event.key === "Tab") closeMenu();
  });
  select.addEventListener("reset-select", paint);
  select.form?.addEventListener("reset", () => setTimeout(paint));
  paint();
}

export function enhanceSelects(scope = document) {
  scope.querySelectorAll("select:not([data-enhanced])").forEach(enhance);
}

document.addEventListener("click", (event) => {
  if (openMenu && !openMenu.root.contains(event.target)) closeMenu();
});
