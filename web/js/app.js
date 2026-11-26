// TEAMSYNC web client (C3): screens S1-S9 in a browser, kept in sync with the
// Android app through the same Firestore data and Security Rules.

import * as api from "./api.js";
import { C, TASK_STATUSES, normalizeEmail, projectRole, toAppDate, formatDate } from "./data-contract.js";

const view = document.getElementById("view");
const titleEl = document.getElementById("title");
const whoEl = document.getElementById("who");
const btnBack = document.getElementById("btnBack");
const btnLogout = document.getElementById("btnLogout");
const banner = document.getElementById("banner");

// Listeners of the current screen; removed when the screen changes (4.3.5).
let unsubs = [];
const clearListeners = () => { unsubs.forEach((u) => u && u()); unsubs = []; };

/** Escapes user text before it is put into HTML. */
const esc = (s) => String(s ?? "").replace(/[&<>"']/g,
  (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

function showBanner(text, isError = false) {
  banner.textContent = text;
  banner.classList.toggle("error", isError);
  banner.classList.toggle("hidden", !text);
}
const showError = (e) => showBanner(api.friendlyError(e), true);

// Offline notice (NFR-04): real-time features need a connection.
window.addEventListener("offline", () => showBanner("You are offline. Changes will sync when you reconnect.", true));
window.addEventListener("online", () => showBanner(""));

const statusClass = (s) => (s === C.STATUS_DONE ? "done" : s === C.STATUS_IN_PROGRESS ? "progress" : "todo");
const roleLabel = (r) => ({ manager: "Project Manager", member: "Project Member", teacher: "Teacher" }[r] || "");

btnLogout.onclick = () => api.logout();
btnBack.onclick = () => showDashboard();

// ======================= S1 Login / Register =======================

function showAuth() {
  clearListeners();
  titleEl.textContent = "TEAMSYNC";
  whoEl.textContent = "";
  btnBack.classList.add("hidden");
  btnLogout.classList.add("hidden");

  view.innerHTML = `
    <div class="auth">
      <div class="tabs">
        <button class="tab active" data-t="login">Log In</button>
        <button class="tab" data-t="register">Register</button>
      </div>
      <form id="fLogin" class="panel">
        <input name="email" type="email" placeholder="Email" required>
        <input name="password" type="password" placeholder="Password" required minlength="6">
        <button class="primary">Log In</button>
      </form>
      <form id="fRegister" class="panel hidden">
        <input name="name" placeholder="Full name" required>
        <input name="email" type="email" placeholder="Email" required>
        <input name="password" type="password" placeholder="Password (6+ characters)" required minlength="6">
        <button class="primary">Create account</button>
      </form>
    </div>`;

  const fLogin = view.querySelector("#fLogin");
  const fRegister = view.querySelector("#fRegister");
  view.querySelectorAll(".tab").forEach((t) => (t.onclick = () => {
    view.querySelectorAll(".tab").forEach((x) => x.classList.toggle("active", x === t));
    fLogin.classList.toggle("hidden", t.dataset.t !== "login");
    fRegister.classList.toggle("hidden", t.dataset.t !== "register");
  }));

  fLogin.onsubmit = async (e) => {
    e.preventDefault();
    const f = new FormData(fLogin);
    try { await api.login(f.get("email"), f.get("password")); } catch (err) { showError(err); }
  };
  fRegister.onsubmit = async (e) => {
    e.preventDefault();
    const f = new FormData(fRegister);
    try { await api.register(f.get("name").trim(), f.get("email"), f.get("password")); } catch (err) { showError(err); }
  };
}

// ======================= S2 Dashboard =======================

let remindersShown = false;

function showDashboard() {
  clearListeners();
  showBanner("");
  titleEl.textContent = "Projects";
  btnBack.classList.add("hidden");
  btnLogout.classList.remove("hidden");

  view.innerHTML = `
    <div class="row spread">
      <h2>My projects</h2>
      <button id="btnNew" class="primary">+ New project</button>
    </div>
    <form id="fNew" class="panel hidden">
      <input name="name" placeholder="Project name" required>
      <input name="code" placeholder="Course code (e.g. IT71A)" required>
      <label>Due date <input name="due" type="date" required></label>
      <div class="row"><button class="primary">Create project</button>
        <button type="button" id="btnCancel" class="secondary">Cancel</button></div>
    </form>
    <div id="reminders"></div>
    <div id="list"><p class="empty">Loading…</p></div>`;

  const fNew = view.querySelector("#fNew");
  view.querySelector("#btnNew").onclick = () => fNew.classList.toggle("hidden");
  view.querySelector("#btnCancel").onclick = () => fNew.classList.add("hidden");
  fNew.onsubmit = async (e) => {
    e.preventDefault();
    const f = new FormData(fNew);
    try {
      await api.createProject(f.get("name").trim(), f.get("code").trim(), toAppDate(f.get("due")));
      fNew.reset();
      fNew.classList.add("hidden");
    } catch (err) { showError(err); }
  };

  unsubs.push(api.watchMyProjects((projects) => {
    const list = view.querySelector("#list");
    if (!list) return;
    if (!projects.length) {
      list.innerHTML = `<p class="empty">No projects yet. Create one, or ask your Project Manager to invite you.</p>`;
      return;
    }
    list.innerHTML = projects.map((p) => `
      <div class="card click" data-id="${esc(p.id)}">
        <div class="row spread">
          <h3>${esc(p.projectName)}</h3>
          <span class="badge role">${esc(roleLabel(projectRole(p, api.me())))}</span>
        </div>
        <div class="muted">${esc(p.courseCode)} · Due ${esc(p.dueDate)}</div>
      </div>`).join("");
    list.querySelectorAll(".card").forEach((el) => (el.onclick = () =>
      showProject(projects.find((p) => p.id === el.dataset.id))));

    if (!remindersShown) {
      remindersShown = true;
      showReminders(projects.filter((p) => projectRole(p, api.me()) !== C.ROLE_TEACHER));
    }
  }, showError));
}

/** M9 / FR-13: unfinished tasks overdue or due within 3 days. */
async function showReminders(projects) {
  const due = await api.dueSoon(projects);
  const box = view.querySelector("#reminders");
  if (!box || !due.length) return;
  const now = Date.now();
  box.innerHTML = `
    <div class="card soft">
      <h3>Deadline reminders</h3>
      ${due.map((t) => {
        const overdue = t.due.getTime() + 86400000 < now;
        return `<div>• <b>${esc(t.title)}</b> — ${esc(t.projectName)}
          <span class="muted">Due ${esc(formatDate(t.due))}${overdue ? " (overdue)" : ""} · ${esc(t.status)}</span></div>`;
      }).join("")}
    </div>`;
}

// ======================= Project screens =======================

function showProject(initial) {
  clearListeners();
  showBanner("");
  btnBack.classList.remove("hidden");
  let project = initial;
  let role = projectRole(project, api.me());
  titleEl.textContent = project.projectName;

  // Keep membership/roles up to date while the project is open
  unsubs.push(api.watchProject(project.id, (p) => { project = p; role = projectRole(p, api.me()); }, showError));

  if (role === C.ROLE_TEACHER) return showTeacherView(() => project);

  // S4-S8: tab bar Tasks · Chat · Files · Progress (Section 4.10)
  view.innerHTML = `
    <div class="tabs">
      <button class="tab active" data-t="tasks">Tasks</button>
      <button class="tab" data-t="chat">Chat</button>
      <button class="tab" data-t="files">Files</button>
      <button class="tab" data-t="progress">Progress</button>
    </div>
    <div id="tabBody"></div>`;

  let tabUnsubs = [];
  const open = (t) => {
    tabUnsubs.forEach((u) => u && u());
    tabUnsubs = [];
    view.querySelectorAll(".tab").forEach((x) => x.classList.toggle("active", x.dataset.t === t));
    const body = view.querySelector("#tabBody");
    const ctx = { body, getProject: () => project, isManager: () => role === C.ROLE_MANAGER, add: (u) => tabUnsubs.push(u) };
    ({ tasks: tasksTab, chat: chatTab, files: filesTab, progress: progressTab })[t](ctx);
  };
  view.querySelectorAll(".tab").forEach((x) => (x.onclick = () => open(x.dataset.t)));
  unsubs.push(() => tabUnsubs.forEach((u) => u && u()));
  open("tasks");
}

// ----- S4 / S5 Tasks (M3) -----
function tasksTab({ body, getProject, isManager, add }) {
  const project = getProject();
  const members = (project.memberIds || []).map(normalizeEmail);
  let filter = "mine";
  let tasks = [];

  body.innerHTML = `
    ${isManager() ? `
    <form id="fTask" class="panel">
      <b>New task</b>
      <input name="title" placeholder="Title" required maxlength="200">
      <textarea name="description" placeholder="Description (optional)"></textarea>
      <div class="row">
        <label style="flex:1">Assign to <select name="assignedTo" id="selAssignee" required>
          <option value="">Choose a member…</option></select></label>
        <label style="flex:1">Priority <select name="priority">
          <option>Low</option><option selected>Medium</option><option>High</option></select></label>
        <label style="flex:1">Due date <input name="due" type="date" required></label>
      </div>
      <div><button class="primary">Save task</button></div>
    </form>` : ""}
    <div class="row spread">
      <div id="summary" class="summary"></div>
      <select id="selView" style="width:auto">
        <option value="mine">My tasks</option><option value="all">All tasks</option>
      </select>
    </div>
    <div id="tasks"><p class="empty">Loading…</p></div>`;

  if (isManager()) {
    const sel = body.querySelector("#selAssignee");
    Promise.all(members.map(async (m) => ({ m, n: await api.nameOf(m) }))).then((rows) => {
      sel.insertAdjacentHTML("beforeend", rows.map(({ m, n }) =>
        `<option value="${esc(m)}">${esc(n)}</option>`).join(""));
    });
    const fTask = body.querySelector("#fTask");
    fTask.onsubmit = async (e) => {
      e.preventDefault();
      const f = new FormData(fTask);
      try {
        await api.createTask(project.id, {
          title: f.get("title").trim(),
          description: f.get("description").trim(),
          assignedTo: f.get("assignedTo"),
          priority: f.get("priority"),
          dueISO: f.get("due"),
        });
        fTask.reset();
      } catch (err) { showError(err); }
    };
  }

  const render = async () => {
    const me = api.me();
    const shown = filter === "mine" ? tasks.filter((t) => normalizeEmail(t.assignedTo) === me) : tasks;
    const count = (s) => shown.filter((t) => (t.status || C.STATUS_TODO) === s).length;
    body.querySelector("#summary").textContent =
      `To Do: ${count(C.STATUS_TODO)} • In Progress: ${count(C.STATUS_IN_PROGRESS)} • Done: ${count(C.STATUS_DONE)}`;

    const box = body.querySelector("#tasks");
    if (!shown.length) { box.innerHTML = `<p class="empty">No tasks here.</p>`; return; }
    const names = await Promise.all(shown.map((t) => api.nameOf(t.assignedTo)));
    box.innerHTML = shown.map((t, i) => `
      <div class="card task ${t.status === C.STATUS_DONE ? "isdone" : ""}">
        <div class="info">
          <div class="name"><b>${esc(t.title)}</b> <span class="muted">· ${esc(t.priority || "")}</span></div>
          ${t.description ? `<div class="muted">${esc(t.description)}</div>` : ""}
          <div class="muted">Assigned to: ${esc(names[i])} · Due ${t.due ? esc(formatDate(t.due)) : "—"}</div>
        </div>
        ${isManager()
          ? `<select data-id="${esc(t.id)}" title="Change status">${TASK_STATUSES.map((s) =>
              `<option ${s === t.status ? "selected" : ""}>${s}</option>`).join("")}</select>`
          : `<span class="badge ${statusClass(t.status)}">${esc(t.status || C.STATUS_TODO)}</span>`}
      </div>`).join("");

    // NFR-07: only the Project Manager marks status
    box.querySelectorAll("select[data-id]").forEach((s) => (s.onchange = async () => {
      try { await api.setTaskStatus(project.id, s.dataset.id, s.value); } catch (err) { showError(err); render(); }
    }));
  };

  body.querySelector("#selView").onchange = (e) => { filter = e.target.value; render(); };
  add(api.watchTasks(project.id, (t) => { tasks = t; render(); }, showError));
}

// ----- S6 Chat (M4) -----
function chatTab({ body, getProject, add }) {
  const project = getProject();
  body.innerHTML = `
    <div id="chat" class="chat"><p class="empty">Loading…</p></div>
    <form id="fChat" class="chat-input">
      <input name="text" placeholder="Message" maxlength="${C.MAX_TEXT}" autocomplete="off" required>
      <button class="primary">Send</button>
    </form>`;

  const box = body.querySelector("#chat");
  add(api.watchMessages(project.id, (msgs) => {
    const me = api.me();
    box.innerHTML = msgs.length ? msgs.map((m) => {
      const mine = normalizeEmail(m.senderId) === me;
      const time = m.sentAt ? m.sentAt.toDate().toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) : "";
      return `<div class="msg ${mine ? "mine" : ""} ${m.pending ? "pending" : ""}">
        <div class="meta">${mine ? "" : esc(m.senderName || m.senderId) + " · "}${esc(time)}${m.pending ? " · sending…" : ""}</div>
        ${esc(m.text)}</div>`;
    }).join("") : `<p class="empty">No messages yet.</p>`;
    box.scrollTop = box.scrollHeight;
  }, showError));

  const f = body.querySelector("#fChat");
  f.onsubmit = async (e) => {
    e.preventDefault();
    const text = f.text.value.trim();
    if (!text) return;
    f.text.value = "";
    try { await api.sendMessage(project.id, text); }
    catch (err) { f.text.value = text; showError(err); } // nothing saved: give the text back
  };
}

// ----- S7 Files (M5) -----
// Files are shared as links because Cloud Storage for Firebase needs the Blaze plan.
function filesTab({ body, getProject, add }) {
  const project = getProject();
  body.innerHTML = `
    <form id="fUpload" class="panel">
      <b>Upload a file</b>
      <span class="muted">Up to 5 MB. For bigger files, use "Share a link" below.</span>
      <input name="file" type="file" required>
      <div><button class="primary">Upload</button></div>
    </form>
    <form id="fFile" class="panel">
      <b>Share a link</b>
      <span class="muted">Upload the file to Google Drive (or similar), share it with your group or
        "Anyone with the link", then paste the link here.</span>
      <input name="name" placeholder="File name (e.g. Chapter 1 draft)" required maxlength="${C.FILE_NAME_MAX}">
      <input name="url" type="url" placeholder="Share link (https://…)" required maxlength="${C.FILE_URL_MAX}"
        pattern="https://.+">
      <div><button class="primary">Share</button></div>
    </form>
    <div id="files"><p class="empty">Loading…</p></div>`;

  const fu = body.querySelector("#fUpload");
  fu.onsubmit = async (e) => {
    e.preventDefault();
    const file = fu.elements["file"].files[0];
    if (!file) return;
    // Section 4.3.4: a file that is too large is rejected before anything is sent
    if (file.size > C.MAX_UPLOAD_BYTES) {
      showBanner("Files can be up to 5 MB. Upload bigger files to Google Drive and share the link.", true);
      return;
    }
    const btn = fu.querySelector("button");
    btn.disabled = true;
    btn.textContent = "Uploading…";
    try { await api.uploadFile(project.id, file); fu.reset(); showBanner(""); }
    catch (err) { showError(err); }
    finally { btn.disabled = false; btn.textContent = "Upload"; }
  };

  const f = body.querySelector("#fFile");
  f.onsubmit = async (e) => {
    e.preventDefault();
    // f.elements[...] because form.name is the form's own name attribute
    const name = f.elements["name"].value.trim();
    const url = f.elements["url"].value.trim();
    if (!name || !url.startsWith("https://")) { showBanner("Enter a file name and an https:// link.", true); return; }
    try { await api.shareFile(project.id, name, url); f.reset(); } catch (err) { showError(err); }
  };

  add(api.watchFiles(project.id, (files) => {
    const box = body.querySelector("#files");
    const size = (n) => (n >= 1048576 ? (n / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(n / 1024)) + " KB");
    box.innerHTML = files.length ? files.map((x) => {
      const when = x.uploadedAt ? x.uploadedAt.toDate().toLocaleDateString() : "";
      const who = esc(x.uploaderName || x.uploadedBy);
      const info = x.pending ? "Saving… (waiting for connection)"
        : x.kind === C.KIND_UPLOAD ? `Uploaded by ${who} · ${size(x.sizeBytes)} · ${esc(when)}`
        : `Link shared by ${who} · ${esc(when)}`;
      // Only https links are stored (enforced by the rules), so they are safe to link to
      const open = x.kind === C.KIND_UPLOAD
        ? `<button class="secondary" data-id="${esc(x.id)}">Download</button>`
        : `<a class="secondary" href="${esc(x.url)}" target="_blank" rel="noopener noreferrer">Open</a>`;
      return `<div class="card">
        <div class="row spread"><b>${esc(x.fileName)}</b>${open}</div>
        <div class="muted">${info}</div>
      </div>`;
    }).join("") : `<p class="empty">No files shared yet.</p>`;

    box.querySelectorAll("button[data-id]").forEach((b) => (b.onclick = async () => {
      const x = files.find((y) => y.id === b.dataset.id);
      try {
        b.disabled = true;
        const blob = await api.downloadFile(project.id, x);
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = x.fileName;
        a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 10000);
      } catch (err) { showError(err); }
      finally { b.disabled = false; }
    }));
  }, showError));
}

// ----- S8 Progress (M6) + teacher feedback + invites -----
function progressTab({ body, getProject, isManager, add }) {
  const project = getProject();
  body.innerHTML = `
    ${isManager() ? `
    <form id="fInvite" class="panel">
      <b>Invite to project</b>
      <div class="row">
        <input name="email" type="email" placeholder="Email address" required style="flex:2">
        <select name="as" style="flex:1"><option value="member">as Member</option>
          <option value="teacher">as Teacher</option></select>
        <button class="primary">Invite</button>
      </div>
    </form>` : ""}
    <div id="feedback"></div>
    <h2>Member progress</h2>
    <div id="members"><p class="empty">Loading…</p></div>`;

  if (isManager()) {
    const f = body.querySelector("#fInvite");
    f.onsubmit = async (e) => {
      e.preventDefault();
      try {
        await api.invite(getProject(), f.email.value, f.as.value === "teacher");
        showBanner(`${f.email.value} was invited.`);
        f.reset();
      } catch (err) { showError(err); }
    };
  }

  add(api.watchTasks(project.id, (tasks) => renderMemberProgress(body.querySelector("#members"), getProject(), tasks), showError));
  add(api.watchComments(project.id, (comments) => {
    body.querySelector("#feedback").innerHTML = comments.length
      ? `<h2>Teacher feedback</h2>${renderComments(comments)}` : "";
  }, showError));
}

async function renderMemberProgress(box, project, tasks) {
  const members = (project.memberIds || []).map(normalizeEmail);
  const rows = await Promise.all(members.map(async (m) => {
    const mine = tasks.filter((t) => normalizeEmail(t.assignedTo) === m);
    const by = (s) => mine.filter((t) => (t.status || C.STATUS_TODO) === s).length;
    const done = by(C.STATUS_DONE);
    const pct = mine.length ? Math.floor((done * 100) / mine.length) : 0;
    const status = !mine.length ? "On Track" : pct >= 80 ? "Ahead" : pct >= 50 ? "On Track" : "Behind";
    return { name: await api.nameOf(m), email: m, total: mine.length, todo: by(C.STATUS_TODO),
      prog: by(C.STATUS_IN_PROGRESS), done, pct, status };
  }));
  box.innerHTML = rows.map((r) => `
    <div class="card">
      <div class="row spread">
        <div><h3>${esc(r.name)}</h3><div class="muted">${esc(r.email)}</div></div>
        <span class="badge ${r.status === "Ahead" ? "done" : r.status === "Behind" ? "behind" : "progress"}">${r.status}</span>
      </div>
      <div class="row spread" style="margin-top:8px">
        <span>${r.done}/${r.total} tasks done</span><b>${r.pct}%</b>
      </div>
      <div class="muted">To Do ${r.todo} • In Progress ${r.prog} • Done ${r.done}</div>
    </div>`).join("") || `<p class="empty">No members yet.</p>`;
}

function renderComments(comments) {
  return comments.map((c) => {
    const when = c.createdAt ? c.createdAt.toDate().toLocaleString([], { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) : "";
    return `<div class="card soft"><b>${esc(c.teacherName || c.teacherId)}</b>
      <span class="muted">· ${esc(when)}</span><div>${esc(c.text)}</div></div>`;
  }).join("");
}

// ======================= S9 Teacher view (M7) =======================

function showTeacherView(getProject) {
  const project = getProject();
  view.innerHTML = `
    <div class="card">
      <h3>Overall project progress</h3>
      <div class="row" style="margin-top:12px"><div class="bar"><div id="bar" style="width:0%"></div></div>
        <b id="pct">0%</b></div>
      <div class="stats">
        <div><b id="total">0</b><span class="muted">Total tasks</span></div>
        <div><b id="done">0</b><span class="muted">Completed</span></div>
        <div><b id="mcount">0</b><span class="muted">Members</span></div>
      </div>
    </div>
    <h2>Member progress</h2>
    <div id="members"><p class="empty">Loading…</p></div>
    <h2>Feedback</h2>
    <form id="fFeedback" class="panel">
      <textarea name="text" placeholder="Write feedback for the group" maxlength="${C.MAX_TEXT}" required></textarea>
      <div><button class="primary">Post</button></div>
    </form>
    <div id="comments"></div>`;

  unsubs.push(api.watchTasks(project.id, (tasks) => {
    const p = getProject();
    const done = tasks.filter((t) => t.status === C.STATUS_DONE).length;
    const pct = tasks.length ? Math.floor((done * 100) / tasks.length) : 0;
    view.querySelector("#total").textContent = tasks.length;
    view.querySelector("#done").textContent = done;
    view.querySelector("#mcount").textContent = (p.memberIds || []).length;
    view.querySelector("#pct").textContent = pct + "%";
    view.querySelector("#bar").style.width = pct + "%";
    renderMemberProgress(view.querySelector("#members"), p, tasks);
  }, showError));

  unsubs.push(api.watchComments(project.id, (comments) => {
    view.querySelector("#comments").innerHTML = comments.length
      ? renderComments(comments) : `<p class="muted">No feedback yet.</p>`;
  }, showError));

  const f = view.querySelector("#fFeedback");
  f.onsubmit = async (e) => {
    e.preventDefault();
    const text = f.text.value.trim();
    if (!text) return;
    try { await api.postComment(project.id, text); f.reset(); } catch (err) { showError(err); }
  };
}

// ======================= Start =======================

api.watchAuth((user) => {
  if (user) {
    whoEl.textContent = user.displayName || user.email;
    remindersShown = false;
    showDashboard();
  } else {
    showAuth();
  }
});
