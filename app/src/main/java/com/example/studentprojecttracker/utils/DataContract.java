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
    public static final String MESSAGES = "messages";           // projects/{projectId}/messages/{messageId}
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

    public static final String ACTION_TASK_CREATED = "task_created";
    public static final String ACTION_TASK_UPDATED = "task_updated";
    public static final String ACTION_TASK_STATUS_CHANGED = "task_status_changed";
    public static final String ACTION_TASK_DELETED = "task_deleted";
    public static final String ACTION_MESSAGE_SENT = "message_sent";

    // ---------- Log target types ----------
    public static final String TARGET_PROJECT = "project";
    public static final String TARGET_MEMBER = "member";
    public static final String TARGET_TASK = "task";
    public static final String TARGET_MESSAGE = "message";

    // ---------- projects/{projectId}/messages/{messageId} (M4) ----------
    public static final String MSG_ID = "messageId";
    public static final String MSG_PROJECT_ID = "projectId";
    public static final String MSG_SENDER_ID = "senderId";     // email of the sender
    public static final String MSG_SENDER_NAME = "senderName"; // display name at send time
    public static final String MSG_TEXT = "text";
    public static final String MSG_SENT_AT = "sentAt";         // server timestamp
    public static final int MSG_MAX_LENGTH = 2000;

    // ---------- projects/{projectId}/comments/{commentId} (M7) ----------
    public static final String COMMENTS = "comments";
    public static final String COMMENT_ID = "commentId";
    public static final String COMMENT_PROJECT_ID = "projectId";
    public static final String COMMENT_TEACHER_ID = "teacherId";     // email of the teacher
    public static final String COMMENT_TEACHER_NAME = "teacherName";
    public static final String COMMENT_TEXT = "text";
    public static final String COMMENT_CREATED_AT = "createdAt";     // server timestamp
    public static final String ACTION_COMMENT_POSTED = "comment_posted";
    public static final String TARGET_COMMENT = "comment";

    // ---------- projects/{projectId}/files/{fileId} (M5) ----------
    // Cloud Storage for Firebase needs the billing-enabled Blaze plan, so files
    // are either UPLOADED into Firestore (content split into chunks, up to 5 MB)
    // or SHARED AS A LINK (e.g. Google Drive) for anything bigger.
    public static final String FILE_KIND = "kind";              // "upload" or "link"
    public static final String KIND_UPLOAD = "upload";
    public static final String KIND_LINK = "link";
    public static final String FILE_MIME = "mimeType";          // upload only
    public static final String FILE_SIZE = "sizeBytes";         // upload only
    public static final String FILE_CHUNK_COUNT = "chunkCount"; // upload only
    public static final String ACTION_FILE_UPLOADED = "file_uploaded";

    // projects/{projectId}/files/{fileId}/chunks/{index}: part of an uploaded file
    public static final String CHUNKS = "chunks";
    public static final String CHUNK_INDEX = "index";
    public static final String CHUNK_DATA = "data";             // Firestore Blob (bytes)
    /** Maximum upload size: the file, its chunks and its log must fit in one 10 MiB batch. */
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    /** Bytes per chunk; a Firestore document is limited to about 1 MiB. */
    public static final int CHUNK_BYTES = 900 * 1024;

    public static final String FILE_ID = "fileId";
    public static final String FILE_PROJECT_ID = "projectId";
    public static final String FILE_NAME = "fileName";
    public static final String FILE_URL = "url";                // https link to the file
    public static final String FILE_UPLOADED_BY = "uploadedBy"; // email of the member who shared it
    public static final String FILE_UPLOADER_NAME = "uploaderName";
    public static final String FILE_UPLOADED_AT = "uploadedAt"; // server timestamp
    public static final String ACTION_FILE_SHARED = "file_shared";
    public static final String TARGET_FILE = "file";
    public static final int FILE_NAME_MAX = 200;
    public static final int FILE_URL_MAX = 2000;

    // ---------- M9 Deadline reminders ----------
    /** Unfinished tasks due within this many days (or overdue) are reminded. */
    public static final int REMINDER_DAYS = 3;

    // ---------- projects/{projectId}/tasks/{taskId} (M3) ----------
    public static final String TASK_ID = "taskId";
    public static final String TASK_PROJECT_ID = "projectId";
    public static final String TASK_TITLE = "title";
    public static final String TASK_DESCRIPTION = "description";
    public static final String TASK_ASSIGNED_TO = "assignedTo";   // email of the member
    public static final String TASK_DUE_DATE = "dueDate";         // Firestore timestamp
    public static final String TASK_STATUS = "status";
    public static final String TASK_PRIORITY = "priority";        // Low, Medium, High (extra)
    public static final String TASK_CREATED_BY = "createdBy";     // email of the Project Manager

    // ---------- Task status values (FR-06) ----------
    public static final String STATUS_TODO = "To Do";
    public static final String STATUS_IN_PROGRESS = "In Progress";
    public static final String STATUS_DONE = "Done";
    public static final String[] TASK_STATUSES = {STATUS_TODO, STATUS_IN_PROGRESS, STATUS_DONE};

    /** Display format of due dates in the app; Firestore stores a timestamp. */
    public static final String DUE_DATE_PATTERN = "M/d/yyyy";

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
