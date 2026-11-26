// TEAMSYNC data contract for the web client (C3).
// Mirrors app/src/main/java/.../utils/DataContract.java and docs/DATA_CONTRACT.md.
// Both clients MUST use these exact paths, field names and values.

export const C = {
  // Collections
  USERS: "users",
  PROJECTS: "projects",
  ACTIVITY_LOGS: "activityLogs",
  TASKS: "tasks",
  MESSAGES: "messages",
  COMMENTS: "comments",

  // Task status values (FR-06)
  STATUS_TODO: "To Do",
  STATUS_IN_PROGRESS: "In Progress",
  STATUS_DONE: "Done",

  // Per-project roles (Section 4.2.1)
  ROLE_MANAGER: "manager",
  ROLE_MEMBER: "member",
  ROLE_TEACHER: "teacher",
  ROLE_NONE: "none",

  // Log actions
  ACTION_PROJECT_CREATED: "project_created",
  ACTION_MEMBER_INVITED: "member_invited",
  ACTION_TEACHER_INVITED: "teacher_invited",
  ACTION_TASK_CREATED: "task_created",
  ACTION_TASK_STATUS_CHANGED: "task_status_changed",
  ACTION_MESSAGE_SENT: "message_sent",
  ACTION_COMMENT_POSTED: "comment_posted",

  // Log target types
  TARGET_PROJECT: "project",
  TARGET_MEMBER: "member",
  TARGET_TASK: "task",
  TARGET_MESSAGE: "message",
  TARGET_COMMENT: "comment",

  MAX_TEXT: 2000,
  REMINDER_DAYS: 3,
};

export const TASK_STATUSES = [C.STATUS_TODO, C.STATUS_IN_PROGRESS, C.STATUS_DONE];

/** Emails are stored lower-case so they match request.auth.token.email in the rules. */
export function normalizeEmail(email) {
  return email ? email.trim().toLowerCase() : null;
}

/** Role of `email` in `project` (a user's role applies per project). */
export function projectRole(project, email) {
  const e = normalizeEmail(email);
  if (!project || !e) return C.ROLE_NONE;
  const has = (list) => (list || []).some((x) => normalizeEmail(x) === e);
  if (has(project.leaderIds)) return C.ROLE_MANAGER;
  if (has(project.mentorIds)) return C.ROLE_TEACHER;
  if (has(project.memberIds)) return C.ROLE_MEMBER;
  return C.ROLE_NONE;
}

/** The Android app shows project due dates as M/d/yyyy; keep the same text format. */
export function toAppDate(isoDate) {
  // "2026-11-27" -> "11/27/2026"
  const [y, m, d] = isoDate.split("-").map(Number);
  return `${m}/${d}/${y}`;
}

export function formatDate(date) {
  return `${date.getMonth() + 1}/${date.getDate()}/${date.getFullYear()}`;
}
