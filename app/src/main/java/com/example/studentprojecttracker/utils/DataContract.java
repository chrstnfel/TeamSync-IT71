package com.example.studentprojecttracker.utils;

import java.util.Locale;

/**
 * TEAMSYNC data contract (Revised paper, Sections 4.3.6 and 4.7).
 *
 * Every collection path, field name and fixed value that the Android client
 * (C2) and the web client (C3) share is defined here, so both clients read and
 * write the same documents. The web client mirrors these names in
 * web/js/data-contract.js, and docs/DATA_CONTRACT.md describes them.
 *
 * Identity: members are identified by their login email (always lower-case).
 * Firebase Security Rules compare it with request.auth.token.email.
 */
public final class DataContract {

    private DataContract() {}

    // ---------- Collections ----------
    public static final String USERS = "users";                 // users/{uid}
    public static final String PROJECTS = "projects";           // projects/{projectId}
    public static final String ACTIVITY_LOGS = "activityLogs";  // projects/{projectId}/activityLogs/{logId}
    public static final String CHATS = "chats";                 // projects/{projectId}/chats/{messageId}
    public static final String NOTES = "notes";                 // projects/{projectId}/notes/{noteId}
    // Moved under projects/{projectId}/ in increments (b) and (c):
    public static final String TASKS = "tasks";
    public static final String FILES = "files";

    // ---------- projects/{projectId} ----------
    public static final String PROJECT_ID = "projectId";
    public static final String PROJECT_NAME = "projectName";
    public static final String PROJECT_COURSE_CODE = "courseCode";
    public static final String PROJECT_DUE_DATE = "dueDate";
    public static final String PROJECT_CREATOR_ID = "creatorId";   // email of the creator
    public static final String PROJECT_LEADER_IDS = "leaderIds";   // emails, role = Project Manager
    public static final String PROJECT_MEMBER_IDS = "memberIds";   // emails, every student in the project
    public static final String PROJECT_MENTOR_IDS = "mentorIds";   // emails, role = Teacher
    public static final String PROJECT_PROGRESS = "progressPercentage";
    public static final String CREATED_AT = "createdAt";
    public static final String UPDATED_AT = "updatedAt";
    /** ID of the activity log entry written in the same batch (checked by Security Rules). */
    public static final String LAST_OP_ID = "lastOpId";

    // ---------- users/{uid} ----------
    public static final String USER_ID = "userId";
    public static final String USER_NAME = "name";
    public static final String USER_EMAIL = "email";
    public static final String USER_ROLE = "role";          // account type: "member" or "advisor"
    public static final String USER_PHOTO_URL = "photoUrl";

    // ---------- activityLogs/{logId} ----------
    public static final String LOG_ID = "logId";
    public static final String LOG_PROJECT_ID = "projectId";
    public static final String LOG_ACTOR_ID = "actorId";    // email of the user who acted
    public static final String LOG_ACTION = "action";
    public static final String LOG_TARGET_TYPE = "targetType";
    public static final String LOG_TARGET_ID = "targetId";
    public static final String LOG_TIMESTAMP = "timestamp"; // server time

    // ---------- Log actions (T1-T8 in Section 4.4.4) ----------
    public static final String ACTION_PROJECT_CREATED = "project_created";
    public static final String ACTION_PROJECT_UPDATED = "project_updated";
    public static final String ACTION_MEMBER_INVITED = "member_invited";
    public static final String ACTION_TEACHER_INVITED = "teacher_invited";
    public static final String ACTION_MEMBER_REMOVED = "member_removed";
    public static final String ACTION_LEADERS_CHANGED = "leaders_changed";

    // ---------- Log target types ----------
    public static final String TARGET_PROJECT = "project";
    public static final String TARGET_MEMBER = "member";

    // ---------- Per-project roles (Section 4.2.1) ----------
    public static final String ROLE_PROJECT_MANAGER = "manager"; // email in leaderIds
    public static final String ROLE_PROJECT_MEMBER = "member";   // email in memberIds only
    public static final String ROLE_TEACHER = "teacher";         // email in mentorIds
    public static final String ROLE_NONE = "none";

    /** Emails are stored lower-case so they match request.auth.token.email in the rules. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
