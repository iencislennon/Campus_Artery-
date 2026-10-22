// In-memory stand-in for the three services, mirroring the planned REST contracts.
const delay = (ms = 350) => new Promise((resolve) => setTimeout(resolve, ms));

// Real SDU layout: blocks D-I, rooms 101-105 / 201-205 / 301-305, plus the "barrel" halls.
const buildings = [
  { id: "D", name: "Block D", color: "#7B3FA0", ink: "#fff" },
  { id: "E", name: "Block E", color: "#8B5A2B", ink: "#fff" },
  { id: "F", name: "Block F", color: "#1E3A8A", ink: "#fff" },
  { id: "G", name: "Block G", color: "#B5D99C", ink: "#1A1A1A" },
  { id: "H", name: "Block H", color: "#8FD0F2", ink: "#1A1A1A" },
  { id: "I", name: "Block I", color: "#14532D", ink: "#fff" },
];
const BARREL_IDS = ["H01", "H02", "H03", "D117", "D218", "D116", "D217", "D113", "D214", "E117", "E221"];
const FLOORS = [1, 2, 3];
const FREE_UNTIL = ["09:50", "10:40", "11:30", "12:20", "13:30", "14:20", "15:10", "16:00"];
const pick = (list, seed) => list[seed % list.length];

const classrooms = buildings.flatMap((b, bi) => FLOORS.flatMap((floor) =>
  [1, 2, 3, 4, 5].map((n) => ({
    id: `${b.id}${floor}0${n}`, building: b.id, floor, kind: "Classroom",
    capacity: 24 + ((bi + floor + n) % 4) * 6, freeUntil: pick(FREE_UNTIL, bi * 7 + floor * 3 + n),
  }))));
const barrels = BARREL_IDS.map((id, i) => ({
  id, building: id[0], floor: id.length === 3 ? 0 : Number(id[1]), kind: "Barrel",
  capacity: 80 + (i % 3) * 20, freeUntil: pick(FREE_UNTIL, i * 5 + 2),
}));
const rooms = [...classrooms, ...barrels];
const teachers = [
  { id: "t1", name: "Dr. Aigerim Nurlanova", department: "Computer Science", office: "E301" },
  { id: "t2", name: "Prof. Marat Sadykov", department: "Mathematics", office: "D302" },
  { id: "t3", name: "Dr. Dina Kassymova", department: "Software Engineering", office: "F305" },
];
const officeHours = [
  { id: "oh1", teacherId: "t1", day: "Mon", start: "14:00", end: "15:00", capacity: 4, booked: 2 },
  { id: "oh2", teacherId: "t1", day: "Wed", start: "10:00", end: "11:30", capacity: 3, booked: 3 },
  { id: "oh3", teacherId: "t2", day: "Tue", start: "16:00", end: "17:00", capacity: 5, booked: 1 },
  { id: "oh4", teacherId: "t3", day: "Thu", start: "12:00", end: "13:00", capacity: 4, booked: 0 },
];
const categories = ["Projector", "Air conditioning", "Furniture", "Network", "Lighting", "Other"];
const issues = [
  { id: "i1", roomId: "D204", category: "Projector", description: "Image flickers after ten minutes.", status: "IN_PROGRESS", createdAt: "2026-10-18", reporterId: "u-student" },
  { id: "i2", roomId: "F103", category: "Network", description: "Four workstations lose the network.", status: "OPEN", createdAt: "2026-10-20", reporterId: "u-student" },
  { id: "i3", roomId: "E117", category: "Lighting", description: "Rear row lights are out.", status: "RESOLVED", createdAt: "2026-10-09", reporterId: "u-teacher" },
];
const initiatives = [
  { id: "n1", title: "SDU Hackathon 2026", category: "Event", organizer: "Aruzhan S.", contact: "aruzhan@sdu.edu.kz", followers: 142, updatedAt: "2026-10-19", following: false, summary: "48 hours of building. Teams of up to four." },
  { id: "n2", title: "Campus Air Quality Study", category: "Research", organizer: "Dr. Dina Kassymova", contact: "d.kassymova@sdu.edu.kz", followers: 38, updatedAt: "2026-10-15", following: true, summary: "Student sensors across Blocks A to C." },
  { id: "n3", title: "Autumn Film Night", category: "Party", organizer: "Timur B.", contact: "timur@sdu.edu.kz", followers: 77, updatedAt: "2026-10-20", following: false, summary: "Open-air screening, bring a blanket." },
  { id: "n4", title: "Open Source Guild", category: "Project", organizer: "Madina K.", contact: "madina@sdu.edu.kz", followers: 91, updatedAt: "2026-10-12", following: false, summary: "Weekly contributions to real repositories." },
];
const clubs = [
  { id: "c1", name: "Robotics Club", members: 54, joined: false, deadline: { title: "Spring cohort applications", date: "2026-11-05" } },
  { id: "c2", name: "Debate Society", members: 33, joined: true, deadline: { title: "Registration for inter-university cup", date: "2026-10-30" } },
  { id: "c3", name: "Data Science Circle", members: 61, joined: false, deadline: { title: "Kaggle sprint sign-up", date: "2026-11-12" } },
];

const demoUsers = {
  "student@sdu.edu.kz": { id: "u-student", name: "Ansar L.", role: "STUDENT", password: "student123" },
  "teacher@sdu.edu.kz": { id: "u-teacher", name: "Dr. Aigerim Nurlanova", role: "TEACHER", password: "teacher123" },
};
const mockTrustedDevices = new Set();
const mockVerificationCodes = new Map();


const profiles = new Map();
const defaultProfile = (user) => ({
  id: user.id, name: user.name, role: user.role, email: `${user.id.replace("u-", "")}@sdu.edu.kz`,
  phone: "+7 700 000 00 00", telegram: "", photo: "",
  notifications: { lessons: true, deadlines: true, grades: true, attendance: true },
});
const grades = [
  { course: "Data Structures", grade: 92, letter: "A" },
  { course: "Web Technologies", grade: 88, letter: "A-" },
  { course: "Discrete Mathematics", grade: 79, letter: "B+" },
  { course: "Software Architecture", grade: 85, letter: "B+" },
];
const feed = [
  { id: "f1", kind: "grades", text: "New grade: Web Technologies midterm, 88.", at: "Today, 09:12" },
  { id: "f2", kind: "attendance", text: "Attendance changed: Discrete Mathematics, 14 Oct marked present.", at: "Yesterday" },
  { id: "f3", kind: "lessons", text: "Software Architecture moved to F203, 11:30.", at: "Yesterday" },
  { id: "f4", kind: "deadlines", text: "Deadline in 3 days: Kaggle sprint sign-up.", at: "Mon" },
];

const fail = (status, message) => Object.assign(new Error(message), { status });
const byId = (list, id) => list.find((item) => item.id === id) ?? (() => { throw fail(404, "Not found"); })();

const routes = {
  "POST /auth/request-code": ({ body }) => {
    const email = body.email?.toLowerCase();
    const user = demoUsers[email] ?? (email?.endsWith("@sdu.edu.kz")
      ? (demoUsers[email] = { id: `u-${Date.now()}`, name: email.split("@")[0], role: "STUDENT" })
      : null);
    if (!user) throw fail(422, "Use your @sdu.edu.kz email.");
    const key = `${user.id}:${body.deviceId}`;
    if (mockTrustedDevices.has(key)) return { trusted: true };
    mockVerificationCodes.set(key, "123456");
    return { trusted: false };
  },
  "POST /auth/login": ({ body }) => {
    const email = body.email?.toLowerCase();
    const user = demoUsers[email];
    const key = user && `${user.id}:${body.deviceId}`;
    if (!user || (!mockTrustedDevices.has(key) && mockVerificationCodes.get(key) !== body.verificationCode)) {
      throw fail(401, "Email or verification code is incorrect.");
    }
    mockTrustedDevices.add(key);
    const { password, ...safe } = user;
    return { user: safe, token: "mock" };
  },
  "POST /auth/register": ({ body }) => ({ user: { id: `u-${Date.now()}`, name: body.name, role: body.role }, token: "mock" }),
  "GET /buildings": () => buildings,
  "GET /rooms": ({ params }) => rooms.filter((r) =>
    (!params.building || r.building === params.building) &&
    (!params.floor || String(r.floor) === params.floor) &&
    (!params.minCapacity || r.capacity >= Number(params.minCapacity)) &&
    (!params.kind || r.kind === params.kind) &&
    (!params.q || r.id.toLowerCase().includes(params.q.toLowerCase()))),
  "GET /teachers": ({ params }) => teachers.filter((t) => !params.q || t.name.toLowerCase().includes(params.q.toLowerCase())),
  "GET /office-hours": () => officeHours.map((h) => ({ ...h, teacher: byId(teachers, h.teacherId).name })),
  "POST /office-hours": ({ body, user }) => {
    const teacherId = user.role === "TEACHER" ? "t1" : null;
    if (!teacherId) throw fail(403, "Only teachers can create office hours.");
    const slot = { id: `oh${Date.now()}`, teacherId, capacity: Number(body.capacity), booked: 0, day: body.day, start: body.start, end: body.end };
    const clash = officeHours.some((h) => h.teacherId === teacherId && h.day === slot.day && h.start < slot.end && slot.start < h.end);
    if (clash) throw fail(409, "This slot overlaps one of your existing office hours.");
    officeHours.push(slot);
    return slot;
  },
  "POST /office-hours/book": ({ body }) => {
    const slot = byId(officeHours, body.id);
    if (slot.booked >= slot.capacity) throw fail(409, "This office hour is full.");
    slot.booked += 1;
    return slot;
  },
  "GET /issues": ({ user }) => issues.filter((i) => i.reporterId === user.id),
  "GET /issue-categories": () => categories,
  "POST /issues": ({ body, user }) => {
    const duplicate = issues.find((i) => i.roomId === body.roomId && i.category === body.category && i.status !== "RESOLVED");
    if (duplicate) throw fail(409, `A similar report (#${duplicate.id}) is already open for ${body.roomId}.`);
    const issue = { id: `i${Date.now()}`, ...body, status: "OPEN", createdAt: new Date().toISOString().slice(0, 10), reporterId: user.id };
    issues.unshift(issue);
    return issue;
  },
  "GET /initiatives": ({ params }) => initiatives.filter((n) =>
    (!params.category || n.category === params.category) &&
    (!params.q || n.title.toLowerCase().includes(params.q.toLowerCase()))),
  "POST /initiatives": ({ body, user }) => {
    const item = { id: `n${Date.now()}`, ...body, organizer: user.name, followers: 0, updatedAt: new Date().toISOString().slice(0, 10), following: false };
    initiatives.unshift(item);
    return item;
  },
  "POST /initiatives/follow": ({ body }) => {
    const item = byId(initiatives, body.id);
    item.following = !item.following;
    item.followers += item.following ? 1 : -1;
    return item;
  },
  "GET /recommendations": ({ params }) => {
    const sorters = {
      popularity: (a, b) => b.followers - a.followers,
      recent: (a, b) => b.updatedAt.localeCompare(a.updatedAt),
      category: (a, b) => Number(b.following) - Number(a.following),
    };
    return [...initiatives].sort(sorters[params.strategy] ?? sorters.popularity).slice(0, 3);
  },
  "GET /profile": ({ user }) => {
    if (!profiles.has(user.id)) profiles.set(user.id, defaultProfile(user));
    const profile = profiles.get(user.id);
    const enabled = Object.keys(profile.notifications).filter((k) => profile.notifications[k]);
    return {
      profile, grades,
      clubs: clubs.filter((c) => c.joined).map((c) => c.name),
      deadlines: clubs.map((c) => c.deadline).sort((a, b) => a.date.localeCompare(b.date)),
      feed: feed.filter((f) => enabled.includes(f.kind)),
    };
  },
  "PUT /profile": ({ body, user }) => {
    if (!body.name?.trim()) throw fail(422, "Name cannot be empty.");
    if (body.photo && body.photo.length > 1_500_000) throw fail(413, "Photo is too large. Use an image under 1 MB.");
    const next = { ...(profiles.get(user.id) ?? defaultProfile(user)), ...body };
    profiles.set(user.id, next);
    return next;
  },
  "GET /clubs": () => clubs,
  "POST /clubs": ({ body }) => {
    if (!body.name?.trim()) throw fail(422, "Club name is required.");
    const club = { id: `c${Date.now()}`, name: body.name.trim(), members: 0, joined: false,
      deadline: body.deadlineTitle && body.deadlineDate ? { title: body.deadlineTitle, date: body.deadlineDate } : { title: "No deadline", date: "9999-12-31" } };
    club.owned = true;
    club.event = { name: "", start: "", end: "", description: "", photo: "" };
    clubs.push(club);
    return club;
  },
  "GET /clubs/manage": ({ params }) => ({ ...byId(clubs, params.id), surveys: [] }),
  "POST /clubs/toggle": ({ body }) => {
    const club = byId(clubs, body.id);
    club.joined = !club.joined;
    club.members += club.joined ? 1 : -1;
    return club;
  },
};

export async function mockRequest(method, path, { params = {}, body, user }) {
  await delay();
  const handler = routes[`${method} ${path}`];
  if (!handler) throw fail(404, `No mock route for ${method} ${path}`);
  return structuredClone(handler({ params, body, user }));
}
