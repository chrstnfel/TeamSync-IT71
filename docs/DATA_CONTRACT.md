# TEAMSYNC Data Contract

The shared agreement between the Android client (C2), the web client (C3) and Firebase
(Revised paper, Sections 4.3.6 and 4.7). Both clients must use exactly these paths,
field names and values. In code: `app/.../utils/DataContract.java` (Android) and
`web/js/data-contract.js` (web, added in Step 5).

Firebase project: `teamsync-it71` (Firestore location asia-southeast1).

## Identity and roles

- A user is identified by their **login email, stored lower-case**. Security Rules
  compare it with `request.auth.token.email`.
- Roles apply **per project** and come from three email lists on the project document:

| List on `projects/{projectId}` | Role (Section 4.2.1) |
|---|---|
| `leaderIds` | Project Manager |
| `memberIds` | Project Member (every student, including the Project Manager) |
| `mentorIds` | Teacher |

## Collections (increments a to d)

| Path | Fields | Written by |
|---|---|---|
| `users/{uid}` | `userId`, `name`, `email`, `role` (account type), `photoUrl`, `updatedAt` | The user themself (at sign-up or first login) |
| `projects/{projectId}` | `projectId`, `projectName`, `courseCode`, `dueDate`, `creatorId`, `leaderIds`, `memberIds`, `mentorIds`, `progressPercentage`, `createdAt`, `updatedAt`, `lastOpId` | Project Manager |
| `projects/{projectId}/activityLogs/{logId}` | `logId`, `projectId`, `actorId` (email), `action`, `targetType`, `targetId`, `timestamp` (server time) | Every module, in the same batch as the change |
| `projects/{projectId}/messages/{messageId}` | `messageId`, `projectId`, `senderId` (email), `senderName`, `text` (1-2000 chars), `sentAt` (server time), `lastOpId` | Project Manager, Project Member (sender only; never edited) |
| `projects/{projectId}/notes/{noteId}` | as in `Note` | Project Manager, Project Member |
| `projects/{projectId}/tasks/{taskId}` | `taskId`, `projectId`, `title`, `description`, `assignedTo` (email), `dueDate` (timestamp), `status` (`To Do` / `In Progress` / `Done`), `priority` (Low/Medium/High, extra), `createdBy`, `createdAt`, `updatedAt`, `lastOpId` | Project Manager only (read by everyone in the project) |
| `projects/{projectId}/comments/{commentId}` | `commentId`, `projectId`, `teacherId` (email), `teacherName`, `text` (1-2000 chars), `createdAt` (server time), `lastOpId` | Teacher only (read by everyone in the project; never edited) |
| `files/{fileId}` *(legacy, moves under the project in increment c part 2)* | `name`, `url`, `uploadedAt`, `projectId` | Project Manager, Project Member |

## Atomic record + log (NFR-09)

Every change is one `WriteBatch`:

1. the record (for example the project document) with `lastOpId = <new UUID>`;
2. `projects/{projectId}/activityLogs/<same UUID>`.

The Security Rules reject the record unless that log entry exists after the batch, so a
change can never be saved without its log entry, and vice versa.

## Log actions

`project_created`, `project_updated`, `member_invited`, `teacher_invited`,
`member_removed`, `leaders_changed`, `task_created`, `task_updated`,
`task_status_changed`, `task_deleted`, `message_sent`, `comment_posted`.
Deadline reminders (T9) are not logged, as in Section 4.4.4.

## Member progress (M6) and teacher overview (M7)

Not stored. Each client computes it from `projects/{projectId}/tasks`: for every member,
the number of their tasks (`assignedTo`) with each `status`, and the percentage Done.
The teacher overview adds the project totals (all tasks, Done tasks, members).

## Deadline reminders (M9)

Not stored. When a student opens the dashboard, the app queries each of their projects'
tasks with `assignedTo == <their email>` and lists those whose `status` is not `Done` and
whose `dueDate` is overdue or within 3 days. No email, SMS or push (Chapter I limitations).

## Storage paths

| Path | Rule |
|---|---|
| `projects/{projectId}/files/{fileId}/{fileName}` | Students of the project, 25 MB or less (from increment c) |
| `profile_images/{uid}.jpg` | Only that user, image 5 MB or less |
| `project_files/{fileName}` *(legacy)* | Signed-in users, 25 MB or less |

## Paper update (Section 4.7)

Suggested replacement for the identity sentence in 4.7:

> Members are identified by their login email address, stored in lower case. Project
> membership and roles are kept as three lists on the project document (`leaderIds` for
> the Project Manager, `memberIds` for Project Members and `mentorIds` for Teachers), so a
> user can be invited before creating an account, and Firebase Security Rules compare
> these lists with the email in the user's authentication token. The `users` collection
> is keyed by the Firebase Authentication user ID.
