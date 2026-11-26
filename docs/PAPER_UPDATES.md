# Suggested updates to the Revised paper

These edits bring **TEAMSYNC_Revised_Systems_Integration.docx** in line with what was
actually built and tested. Each item gives the section, the current text (shortened with
"…"), and the suggested replacement. Show them to the group before changing the paper.

There are two changes:

1. **Identity by email** (Section 4.7). Members are identified by their login email.
2. **Files without Cloud Storage** (M5). Cloud Storage needs the Blaze plan (a payment card
   and a USD 50 authorization hold), which the group could not use. Files up to **5 MB** are
   uploaded into **Cloud Firestore** (split into chunks), and bigger files are shared as
   **links**. Everything stays on the free Spark plan.

---

## Change 1: Identity by email

### Section 4.7 Database Design: paragraph after Figure 5

**Current:** "…The userId equals the Firebase Authentication user ID, so Firebase
Authentication and Cloud Firestore identify a user in the same way."

**Replace with:**
> Members are identified by their login email address, stored in lower case. Project
> membership and roles are kept as three lists on the project document (`leaderIds` for the
> Project Manager, `memberIds` for Project Members and `mentorIds` for Teachers), so a user
> can be invited before creating an account, and Firebase Security Rules compare these lists
> with the email in the user's authentication token. The `users` collection is keyed by the
> Firebase Authentication user ID.

### Section 4.7 data dictionary

- `members` (under a project): replace with the three fields on `projects`: `leaderIds`,
  `memberIds`, `mentorIds` (array of string, emails), plus `lastOpId` (string, ID of the
  activity log entry written in the same batch).
- `tasks.assignedTo`, `messages.senderId`, `comments.teacherId`, `files.uploadedBy`,
  `activityLogs.actorId`: description "email of the user" instead of "userId of the user".

---

## Change 2: Files without Cloud Storage (M5)

### Chapter I, Scope and Limitations (2nd paragraph)

**Current:** "…Files can only be uploaded up to 25MB, and are also subject to Firebase
usage limits. Cloud Storage for Firebase may require a billing enabled plan (Google,
n.d.-b). This could limit the long term hosting…"

**Replace with:**
> Cloud Storage for Firebase requires the billing-enabled Blaze plan (Google, n.d.-b), which
> needs a payment card and a temporary authorization charge, so it is not used in this
> release. Files of up to 5 MB are uploaded into Cloud Firestore, where the file content is
> split into chunks; larger files are shared as links to an external service such as Google
> Drive. Uploaded files share Cloud Firestore's no-cost storage of 1 GiB with the rest of the
> project data, and access to a linked file also depends on the sharing settings of that
> service.

### Chapter I, Statement of the Problem Q2 and Specific Objective 2

Remove "Cloud Storage" from the list of services:
> …using Firebase Authentication, Cloud Firestore, and Security Rules…

In **Definition of Terms → Firebase**, change "and Cloud Storage for Firebase for uploaded
files" to "(including uploaded files of up to 5 MB)".

### Chapter III, Technology Stack Summary

Replace the **File storage** row:

| Layer | Technology | Exact role in TEAMSYNC |
|---|---|---|
| File storage | Cloud Firestore (file chunks); links to Google Drive or similar | Content of uploaded files (up to 5 MB) split into chunks, and records of shared links |

Replace the **Cloud Storage for Firebase** paragraph in Section 4 with:
> **File storage in Cloud Firestore.** Cloud Storage for Firebase requires the
> billing-enabled Blaze plan (Google, n.d.-b), so uploaded files are stored in Cloud
> Firestore instead. A file of up to 5 MB is split into chunks of at most 900 KB, because one
> Firestore document is limited to about 1 MiB. The file record, all chunks and the activity
> log entry are written in one atomic batch, which Firestore limits to 10 MiB; this is why
> files are limited to 5 MB. Larger files are shared as links. `storage.rules` are prepared
> so that Cloud Storage can be enabled later without changing the other modules.

In **Justification of the Stack**, change "and Cloud Storage for shared files" to "and Cloud
Firestore for shared files, so the whole system runs on the no-cost Spark plan".

### Section 4.2.2 Functional Requirements

**FR-09 current:** "The system shall let users upload files of up to 25 MB to a project and
list the uploaded files."

**Replace with:**
> FR-09: The system shall let users upload files of up to 5 MB to a project, share larger
> files as links, and list the shared files.

### Section 4.2.3 Nonfunctional Requirements

- **NFR-06 Capacity:** "Uploaded files shall not exceed 5 MB, and all stored data shares
  Cloud Firestore's usage limits (1 GiB on the no-cost plan)."
- **NFR-09:** change "file metadata failures shall be recoverable" to "a file's content,
  record and log entry shall be written in one atomic batch".

### Sections 4.2.4, 4.2.5, 4.4.5 (M5 rows)

- M5 File Sharing: "File upload (up to 5 MB), link sharing and file list."
- Inputs: "File (up to 5 MB) or file name and https link". Outputs: "File list with name,
  sharer, size and date; downloadable file or link". Stored in: "Cloud Firestore (file record
  and chunks)".
- Services and collections used: "files; files/chunks".

### Section 4.3 Architecture

- **Figure 1 and component inventory:** remove **C7 Cloud Storage for Firebase**, or mark it
  "planned (needs Blaze plan)". **C5 Cloud Firestore** role: add "uploaded file content
  (chunks)".
- **Integration inventory I4:** change to "Android app or web app to Cloud Firestore (file
  chunks) | HTTPS through the Firebase SDK | File content in chunks (up to 5 MB) and file
  records | Firestore usage limits (1 GiB no-cost)". In **I5**, remove "and Cloud Storage".
- **4.3.3 Storage step:** "Files are first uploaded to Cloud Storage; the file record and log
  entry are then committed together" becomes "A file's content chunks, its file record and
  its log entry are committed together in one batch."
- **Typical flows, "Upload a file" row:** Processed by "Client size check, Security Rules,
  Cloud Firestore"; Data "File (5 MB or less)"; Stored "File record and chunks in Cloud
  Firestore; activity log entry".
- **4.3.4 Error handling:** "File larger than 25 MB" becomes "File larger than 5 MB: the app
  rejects it before upload and suggests sharing a link". **Remove** the row "File uploaded but
  Firestore metadata or log fails", and add: "Upload interrupted or rejected: the batch is
  atomic, so no file record or chunk is saved; the user may try again."
- **4.3.5 Integration dependencies, "Firebase usage limits and pricing":** "Cloud Storage
  requires the Blaze plan, so files are stored in Cloud Firestore (5 MB limit); usage
  monitored in the Firebase Console."
- **4.3.6 Inter-module table, M5 row:** "file bytes in chunks, fileId, fileName, mimeType,
  sizeBytes, chunkCount, uploadedBy, uploadedAt | Firestore batch (record + chunks + log);
  authorized retrieval of chunks".

### Section 4.4.4 Key Transactions

**T7:** data "File of 5 MB or less, or a file link"; result "File record (and content) saved
and listed"; logged "Yes".

### Section 4.5 UC-10: Upload and View Files

**Main flow:**
1. The user opens the Files view and chooses Upload.
2. The user selects a file.
3. The app checks that the file is 5 MB or less.
4. The app splits the file into chunks and sends the file record, the chunks and the
   activity log entry to Cloud Firestore in one batch over HTTPS.
5. Firebase Security Rules check that the user is a Project Manager or Project Member.
6. The system saves the file record, the chunks and the log entry together.
7. The file list updates for the connected clients.

**Alternate flows:**
- A1. The file is larger than 5 MB: the app rejects it before upload and suggests Share link.
- A2. The user is not allowed: nothing is stored and the app shows a message.
- A3. The upload fails because of the connection or a usage limit: nothing is stored, the
  app shows the error and the user may try again.
- A4. Share link: the user enters a file name and an https link; the record and log entry
  are saved in one batch.

**Supporting services:** Cloud Firestore.

### Section 4.7 data dictionary, `files`

| Field | Type | Key | Description |
|---|---|---|---|
| fileId | string | PK | Document ID of the file record |
| projectId | string | FK | Project the file belongs to |
| uploadedBy | string | FK | Email of the member who shared it |
| kind | string | | "upload" or "link" |
| fileName | string | | Name of the file |
| mimeType | string | | Type of an uploaded file |
| sizeBytes | number | | Size of an uploaded file, 5 MB or less |
| chunkCount | number | | Number of chunks of an uploaded file (1 to 6) |
| url | string | | https link (link files only) |
| uploadedAt | timestamp | | Date and time shared |

Add a new collection **`chunks` (under a file):** `index` (number, PK), `data` (bytes, up to
900 KB). Relationship: "One file has many chunks". Remove `storagePath`. In the access
table, change "files and Storage" to "files and chunks".

### Section 4.9.2 Figure 9: Flowchart: File Upload

Replace the description with:
> Figure 9 follows UC-10. The app first checks the 5 MB limit, so a file that is too large is
> rejected before anything is sent. The file is split into chunks, and the file record, the
> chunks and the activity log entry are sent to Cloud Firestore in one batch, where Security
> Rules check that the user is a Project Manager or Project Member of the project. A refused
> request stores nothing. If the upload fails because of the connection or a usage limit,
> nothing is stored, the app shows the error and the user may try again. Because the batch
> is atomic, a partly saved file cannot occur. The file list then updates on authorized
> connected clients.

**Update Figure 9:** "25 MB" → "5 MB", "Cloud Storage" → "Cloud Firestore (chunks)", and
remove the separate "save metadata" step (it is part of the same batch).

### Section 4.10 Wireframes, S7

"S7 shows the uploaded and linked files and the Upload button with the 5 MB limit, plus a
Share link button." Update Figure 12 (S7) to match.

### Section 4.3.7 Integration tests

- **IT-03:** "…Attempt a file over 5 MB." (instead of 25 MB). Add "Share a link; open it
  from the other client."
- **IT-05:** "In test configuration, reject the activity log write within the upload batch.
  Restore permission and retry." Expected: "No file record or chunk is published without its
  log entry; a retry creates one record and one log entry."

### Section 4.11 Consistency table

Data model row: "The collections match the data listed for Cloud Firestore in Section 4.3.1;
file content is stored in Cloud Firestore chunks, and Cloud Storage is not used."

---

## Other small updates

- **Chapter III, Web client** row and Chapter I: the web client is live at
  https://teamsync-it71.web.app (Firebase Hosting).
- **Section 4.4.3:** members open the chat room from the "Chat" tab (Tasks · Chat · Files ·
  Team).
- **Profile pictures** (an extra feature from the 3rd-year app) still use Cloud Storage, so
  changing a profile picture does not work in this release.
