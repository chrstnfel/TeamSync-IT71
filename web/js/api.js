// TEAMSYNC web client: Firebase integration layer (C3 -> C4, C5, C6).
// Every change is written in one atomic WriteBatch together with its activity
// log entry (Revised paper 4.3.3 / NFR-09), exactly like the Android client.

import { app } from "./firebase-config.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword,
  createUserWithEmailAndPassword, updateProfile, signOut,
} from "https://www.gstatic.com/firebasejs/13.4.0/firebase-auth.js";
import {
  getFirestore, collection, doc, getDoc, getDocs, setDoc, writeBatch,
  onSnapshot, query, where, orderBy, serverTimestamp, Timestamp,
} from "https://www.gstatic.com/firebasejs/13.4.0/firebase-firestore.js";
import { C, normalizeEmail } from "./data-contract.js";

export const auth = getAuth(app);
export const db = getFirestore(app);

export const me = () => normalizeEmail(auth.currentUser?.email);
export const myName = () => auth.currentUser?.displayName || me();

const projectRef = (pid) => doc(db, C.PROJECTS, pid);
const sub = (pid, name) => collection(db, C.PROJECTS, pid, name);

/** Adds projects/{pid}/activityLogs/{opId} to the batch (M8). */
function addLog(batch, pid, opId, action, targetType, targetId) {
  batch.set(doc(db, C.PROJECTS, pid, C.ACTIVITY_LOGS, opId), {
    logId: opId,
    projectId: pid,
    actorId: me(),
    action,
    targetType,
    targetId,
    timestamp: serverTimestamp(),
  });
}

const newOpId = () => crypto.randomUUID();

/** Firestore errors -> the messages from Section 4.3.4. */
export function friendlyError(e) {
  if (e?.code === "permission-denied") return "You are not allowed to do this in this project.";
  if (e?.code === "unavailable") return "No connection. Check your internet and try again.";
  if (e?.code?.startsWith?.("auth/")) {
    if (["auth/invalid-credential", "auth/wrong-password", "auth/user-not-found"].includes(e.code))
      return "Invalid email or password.";
    if (e.code === "auth/email-already-in-use") return "That email already has an account.";
    if (e.code === "auth/weak-password") return "Password must be at least 6 characters.";
  }
  return e?.message || String(e);
}

// ---------------- M1 Authentication ----------------

export const watchAuth = (cb) => onAuthStateChanged(auth, cb);

export async function login(email, password) {
  await signInWithEmailAndPassword(auth, email.trim(), password);
  await ensureOwnProfile();
}

export async function register(name, email, password) {
  const cred = await createUserWithEmailAndPassword(auth, email.trim(), password);
  await updateProfile(cred.user, { displayName: name });
  await ensureOwnProfile(name);
}

export const logout = () => signOut(auth);

/** T1: users/{uid} exists so teammates can find this account (same as Android). */
async function ensureOwnProfile(name) {
  const u = auth.currentUser;
  if (!u) return;
  const ref = doc(db, C.USERS, u.uid);
  const snap = await getDoc(ref);
  if (snap.exists()) return;
  await setDoc(ref, {
    userId: u.uid,
    name: name || u.displayName || me(),
    email: me(),
    role: "member",
    updatedAt: Date.now(),
  });
}

const nameCache = new Map();
/** Display name for an email, from users/ (falls back to the email). */
export async function nameOf(email) {
  const e = normalizeEmail(email);
  if (!e) return "Unassigned";
  if (nameCache.has(e)) return nameCache.get(e);
  try {
    const snap = await getDocs(query(collection(db, C.USERS), where("email", "==", e)));
    const name = snap.empty ? e : snap.docs[0].data().name || e;
    nameCache.set(e, name);
    return name;
  } catch {
    return e;
  }
}

// ---------------- M2 Projects ----------------

/** FR-14: projects where I am a student or an invited teacher, live. */
export function watchMyProjects(cb, onError) {
  const results = { student: [], teacher: [] };
  const emit = () => {
    const byId = new Map();
    [...results.student, ...results.teacher].forEach((p) => byId.set(p.id, p));
    cb([...byId.values()]);
  };
  const toList = (snap) => snap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const u1 = onSnapshot(
    query(collection(db, C.PROJECTS), where("memberIds", "array-contains", me())),
    (s) => { results.student = toList(s); emit(); }, onError);
  const u2 = onSnapshot(
    query(collection(db, C.PROJECTS), where("mentorIds", "array-contains", me())),
    (s) => { results.teacher = toList(s); emit(); }, onError);
  return () => { u1(); u2(); };
}

export function watchProject(pid, cb, onError) {
  return onSnapshot(projectRef(pid), (s) => s.exists() && cb({ id: s.id, ...s.data() }), onError);
}

/** T2 Create project: the creator becomes the only Project Manager. */
export async function createProject(name, courseCode, dueDate) {
  const pid = crypto.randomUUID();
  const opId = newOpId();
  const batch = writeBatch(db);
  batch.set(projectRef(pid), {
    projectId: pid,
    projectName: name,
    courseCode,
    dueDate, // "M/d/yyyy", same text format as Android
    creatorId: me(),
    leaderIds: [me()],
    memberIds: [me()],
    mentorIds: [],
    progressPercentage: 0,
    createdAt: Date.now(),
    lastOpId: opId,
  });
  addLog(batch, pid, opId, C.ACTION_PROJECT_CREATED, C.TARGET_PROJECT, pid);
  await batch.commit();
}

/** T3 Invite a member (memberIds) or teacher (mentorIds) by email. */
export async function invite(project, email, asTeacher) {
  const e = normalizeEmail(email);
  const field = asTeacher ? "mentorIds" : "memberIds";
  const list = (project[field] || []).map(normalizeEmail);
  if (list.includes(e)) throw new Error(`${e} is already in this project.`);
  const opId = newOpId();
  const batch = writeBatch(db);
  batch.update(projectRef(project.id), {
    [field]: [...list, e],
    updatedAt: Date.now(),
    lastOpId: opId,
  });
  addLog(batch, project.id, opId,
    asTeacher ? C.ACTION_TEACHER_INVITED : C.ACTION_MEMBER_INVITED, C.TARGET_MEMBER, e);
  await batch.commit();
}

// ---------------- M3 Tasks ----------------

const taskFromDoc = (d) => {
  const t = d.data();
  return { id: d.id, ...t, due: t.dueDate ? t.dueDate.toDate() : null };
};

/** Real-time tasks of one project (FR-07, FR-15). */
export function watchTasks(pid, cb, onError) {
  return onSnapshot(sub(pid, C.TASKS), (s) => cb(s.docs.map(taskFromDoc)), onError);
}

/** T4 Create and assign task (Project Manager only). */
export async function createTask(pid, { title, description, assignedTo, priority, dueISO }) {
  const ref = doc(sub(pid, C.TASKS));
  const opId = newOpId();
  const [y, m, d] = dueISO.split("-").map(Number);
  const batch = writeBatch(db);
  batch.set(ref, {
    taskId: ref.id,
    projectId: pid,
    title,
    description: description || "",
    priority: priority || "Medium",
    assignedTo: normalizeEmail(assignedTo),
    dueDate: Timestamp.fromDate(new Date(y, m - 1, d)), // local midnight, like Android
    status: C.STATUS_TODO,
    createdBy: me(),
    createdAt: Date.now(),
    lastOpId: opId,
  });
  addLog(batch, pid, opId, C.ACTION_TASK_CREATED, C.TARGET_TASK, ref.id);
  await batch.commit();
}

/** T5 Change task status (Project Manager only, enforced by the rules). */
export async function setTaskStatus(pid, taskId, status) {
  const opId = newOpId();
  const batch = writeBatch(db);
  batch.update(doc(db, C.PROJECTS, pid, C.TASKS, taskId), {
    status,
    updatedAt: Date.now(),
    lastOpId: opId,
  });
  addLog(batch, pid, opId, C.ACTION_TASK_STATUS_CHANGED, C.TARGET_TASK, taskId);
  await batch.commit();
}

/** T9 Deadline reminders: my unfinished tasks overdue or due within 3 days. */
export async function dueSoon(projects) {
  const limit = Date.now() + C.REMINDER_DAYS * 86400000;
  const out = [];
  for (const p of projects) {
    try {
      const s = await getDocs(query(sub(p.id, C.TASKS), where("assignedTo", "==", me())));
      s.docs.map(taskFromDoc)
        .filter((t) => t.due && t.status !== C.STATUS_DONE && t.due.getTime() <= limit)
        .forEach((t) => out.push({ ...t, projectName: p.projectName }));
    } catch {
      // A project the user can no longer read is skipped
    }
  }
  return out;
}

// ---------------- M4 Chat ----------------

/** Real-time chat, oldest first. `pending` marks writes not yet confirmed by the server. */
export function watchMessages(pid, cb, onError) {
  return onSnapshot(
    query(sub(pid, C.MESSAGES), orderBy("sentAt", "asc")),
    { includeMetadataChanges: true },
    (s) => cb(s.docs.map((d) => ({
      id: d.id,
      ...d.data({ serverTimestamps: "estimate" }),
      pending: d.metadata.hasPendingWrites,
    }))),
    onError);
}

/** T6 Send chat message. */
export async function sendMessage(pid, text) {
  const ref = doc(sub(pid, C.MESSAGES));
  const opId = newOpId();
  const batch = writeBatch(db);
  batch.set(ref, {
    messageId: ref.id,
    projectId: pid,
    senderId: me(),
    senderName: myName(),
    text,
    sentAt: serverTimestamp(),
    lastOpId: opId,
  });
  addLog(batch, pid, opId, C.ACTION_MESSAGE_SENT, C.TARGET_MESSAGE, ref.id);
  await batch.commit();
}

// ---------------- M7 Teacher feedback ----------------

export function watchComments(pid, cb, onError) {
  return onSnapshot(
    query(sub(pid, C.COMMENTS), orderBy("createdAt", "desc")),
    (s) => cb(s.docs.map((d) => ({ id: d.id, ...d.data({ serverTimestamps: "estimate" }) }))),
    onError);
}

/** T8 Leave feedback (invited Teacher only). */
export async function postComment(pid, text) {
  const ref = doc(sub(pid, C.COMMENTS));
  const opId = newOpId();
  const batch = writeBatch(db);
  batch.set(ref, {
    commentId: ref.id,
    projectId: pid,
    teacherId: me(),
    teacherName: myName(),
    text,
    createdAt: serverTimestamp(),
    lastOpId: opId,
  });
  addLog(batch, pid, opId, C.ACTION_COMMENT_POSTED, C.TARGET_COMMENT, ref.id);
  await batch.commit();
}
