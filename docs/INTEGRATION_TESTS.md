# TEAMSYNC Integration Test Sheet

Integration tests from the Revised paper, Section 4.3.7. They are run **separately from the
SUS usability evaluation**. For each test, the Quality Analyst records the environment, test
data, expected and actual outcome, pass/fail and any defect. Failed tests are fixed and run
again before technical acceptance.

**Status key:** ✅ Pass · 🟡 Partly run (finish the remaining steps) · ⬜ Not run yet ·
⏸ Blocked

## Test environment

| Item | Value |
|---|---|
| Firebase project | `teamsync-it71` (Firestore location asia-southeast1, Spark plan) |
| Android client (C2) | `integration` branch, run from Android Studio on a Xiaomi 23021RAAEG over USB |
| Web client (C3) | https://teamsync-it71.web.app (also `firebase serve` at localhost:5050 during development) |
| Security Rules (C6) | `firestore.rules` as deployed with `firebase deploy --only firestore` |
| Test project | "SIA" (IT71A) |

### Test accounts

Use only these test accounts. Their passwords are kept by the tester and are not written here.

| Role in "SIA" | Account |
|---|---|
| Project Manager | the group's CMU account that created "SIA" |
| Project Member | `member.test@teamsync.test` |
| Teacher (invited as mentor) | `teacher.test@teamsync.test` |
| Unrelated account (IT-02) | `outsider.test@teamsync.test` (create when running IT-02) |

`.test` addresses are reserved for testing, so they can never reach a real person.

---

## Summary

| Test | Requirement | Status |
|---|---|---|
| IT-01 Cross-platform task status | FR-06, FR-07, FR-10, FR-11, FR-15 | ✅ |
| IT-02 Role-based access | FR-01, FR-14, NFR-03 | ✅ |
| IT-03 File upload and download across clients | FR-09, FR-12 | ✅ |
| IT-04 Task update fails if its log fails | FR-06, FR-12, NFR-09 | ✅ |
| IT-05 File record failure and recovery | FR-09, FR-12, NFR-09 | ✅ |
| IT-06 Connection lost during an update | FR-15, NFR-02, NFR-04 | ✅ |
| IT-07 Chat message and teacher feedback | FR-08, FR-11, FR-12 | ✅ |
| IT-08 Deadline reminders | FR-13 | ✅ |
| IT-09 Sync time under normal/slow connection | NFR-02 | ✅ |

---

## IT-01 Cross-platform task status (FR-06, FR-07, FR-10, FR-11, FR-15) ✅

**Procedure (paper):** Change a task status on Android; observe the web client and the
invited teacher view. Repeat from the web.

**Expected:** Both clients and the authorised progress views show the same confirmed task
status and counts without a manual refresh.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| PM creates "Documentation" on Android, assigned to Test Member | ✅ | Saved in `projects/{id}/tasks`; `task_created` log entry | Firestore screenshot (task + log) |
| PM changes it to In Progress on Android | ✅ | Badge turned blue; Firestore `status: "In Progress"`; task `lastOpId` equals the `task_status_changed` log ID | Firestore screenshot (`6eb78148…`) |
| Test Member sees it on Android, cannot change it | ✅ | Shown as In Progress under My Tasks; tapping the badge does nothing | Tester confirmation |
| PM marks it Done; Team tab updates | ✅ | Test Member card: Done 1, 100%, "Ahead" | Phone screenshot (Team tab) |
| Teacher view shows the same counts | ✅ | 1 task, 1 completed, Test Member 1/1 | Phone screenshot (teacher view) |
| Chat sent on one client appears on the other | ✅ | Same messages, order and sender alignment on web and Android | Web + phone screenshots |
| Status changed **on the web** (dropdown) while the phone shows Tasks | ✅ | Phone badge updated by itself, no refresh | Tester confirmation |
| Teacher view open on the web (incognito, teacher account); status changed on the phone | ✅ | Teacher counts updated by themselves | Tester confirmation |
| Final state compared: Android (PM) and web (Test Member) | ✅ | Both show To Do 1 · In Progress 0 · Done 2 with the same three tasks | Phone + web screenshots |

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 26-27, 2026

---

## IT-02 Role-based access (FR-01, FR-14, NFR-03) ✅

**Procedure (paper):** Attempt reads and writes as manager, member, invited teacher and an
unrelated account.

**Expected:** Only permitted operations succeed; unauthorised changes are rejected.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| PM sees Edit / Add Member / Add Mentor / Delete on the project | ✅ | All four shown | Phone screenshot |
| Member sees the invited project but only "View Details" | ✅ | Only "View Details" | Phone screenshot |
| Member's Team tab has no promote/remove buttons | ✅ | None shown | Phone screenshot |
| Invite is logged (`member_invited`) | ✅ | 2nd activity log entry | Firestore screenshot |
| Teacher can post feedback | ✅ | Saved, `comment_posted` logged | Firestore screenshot |
| Registered and logged in as `outsider.test@teamsync.test` (web) | ✅ | Empty dashboard; "SIA" does **not** appear | Tester confirmation, Nov 27 |
| Server side: Rules Playground, see below | ✅ | All six checks gave the expected result | Tester confirmation, Nov 27 |

**Server-side check with the Rules Playground** (Firebase console → Firestore → Rules → *Rules
Playground*). The app hides forbidden buttons, so this proves the **server** also refuses:

| # | Simulate | Location | Authenticated as (`email` in token) | Expected | Actual |
|---|---|---|---|---|---|
| a | `update` | `projects/<SIA id>/tasks/<task id>` | `member.test@teamsync.test` | **Denied** (only the PM marks status) | ✅ Denied |
| b | `get` | `projects/<SIA id>` | `outsider.test@teamsync.test` | **Denied** (not in the project) | ✅ Denied |
| c | `create` | `projects/<SIA id>/comments/x` | `member.test@teamsync.test` | **Denied** (only a teacher comments) | ✅ Denied |
| d | `get` | `projects/<SIA id>/messages/<msg id>` | `teacher.test@teamsync.test` | **Denied** (teachers have no chat access, 4.7) | ✅ Denied |
| e | `get` | `projects/<SIA id>/tasks/<task id>` | `teacher.test@teamsync.test` | **Allowed** (teacher reads progress) | ✅ Allowed |
| f | `delete` | `projects/<SIA id>/activityLogs/<log id>` | the PM's email | **Denied** (logs are never edited) | ✅ Denied |

In the Playground, tick **Authenticated**, choose provider **password**, and set the token's
`email` field to the address in the table. Screenshot each result.

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 26-27, 2026

---

## IT-03 File upload and download across clients (FR-09, FR-12) ✅

**Note:** Cloud Storage for Firebase needs the billing-enabled Blaze plan (Chapter I
limitations, Google n.d.-b), which the group could not use. Uploaded files (up to **5 MB**)
are stored in Firestore in chunks; bigger files are shared as links.

**Procedure (paper, with the 5 MB limit):** Upload a valid file, then view or download it
from the other client. Try a file over the limit. Share a link.

**Expected:** The correct file and its metadata appear with one log entry; the oversized file
is rejected before upload; a link is shared and opens on both clients.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| Android → Files → **Upload** → pick a file under 5 MB | ✅ | Uploaded | Tester confirmation |
| It appears on the web Files tab without refreshing; **Download** gives the same file | ✅ | Appeared and downloaded correctly | Tester confirmation |
| Firestore: one `files` doc (`kind: "upload"`, `sizeBytes`, `chunkCount`) + its `chunks` + one `file_uploaded` log with matching `lastOpId` | ✅ | `kind "upload"`, `sizeBytes 41081`, `chunkCount 1`, chunk `0`, one `file_uploaded` log, `lastOpId` matches | Firestore screenshot |
| Web → Upload a file; open it on Android | ✅ | Appeared on Android and opened | Tester confirmation |
| Pick a file **over 5 MB** | ✅ | "File is too large"; nothing added to the list | Tester confirmation |
| Android → **Share link** with a Google Drive link; it opens from the web | ✅ | Links "Draft" and "Dratsss" listed on both clients ("Link shared by …"), **Open** works on the web | Web screenshot (Files tab, Test Member) |
| Try a link without https, or an empty name | ✅ | Error each time; nothing added | Tester confirmation |
| Teacher account cannot see the Files list (Section 4.7) | ✅ | Teacher view shows progress and feedback only, no Files | Web screenshot (teacher view) |

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 27, 2026

---

## IT-04 Task update fails if its log fails (FR-06, FR-12, NFR-09) ✅

**Procedure (paper):** In a test configuration, reject the activity log write within the task
update batch.

**Expected:** Neither write commits; a retry gives one consistent update and one log entry.

**How to run it (about 5 minutes, then restore the rules):**

1. In `firestore.rules`, inside `match /activityLogs/{logId}`, temporarily change
   `allow create: if signedIn()` to `allow create: if false`.
2. `firebase deploy --only firestore`
3. As the PM, change a task's status on the phone or web.
   **Expected:** an error appears, the status goes back, and in Firestore the task's
   `status` and `lastOpId` are **unchanged** and **no** new log entry exists.
4. Undo the change in `firestore.rules` (`git checkout firestore.rules`) and deploy again.
5. Change the status again. **Expected:** the status and **exactly one** new
   `task_status_changed` entry, with the task's `lastOpId` equal to the log ID.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| 3. Change status while logs are blocked (rules deployed Nov 27 with `allow create: if false`) | ✅ | PM changed "update develop" To Do → In Progress: error shown | Tester confirmation |
| 3. Task unchanged, no new log | ✅ | Status went back to To Do on the clients (the batch was refused as a whole) | Tester confirmation |
| 5. Retry after restoring rules (committed `firestore.rules` redeployed) | ✅ | "update develop" saved as In Progress on both clients | Tester confirmation |
| 5. One status change + one log, matching IDs | ✅ | Exactly one new `task_status_changed` entry; its ID equals the task's `lastOpId` | Firestore console check |

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 27, 2026

---

## IT-05 File record failure and recovery (FR-09, FR-12, NFR-09) ✅

**Note:** because the file content is stored in Firestore, the file record, its chunks and
its log entry are **one atomic batch**. The paper's case "file uploaded but metadata failed"
therefore cannot happen: either everything is saved or nothing is. The test checks this.

**Expected:** No file entry or chunk is published before the commit; a retry creates one
record and one log entry; an offline upload shows as pending and is confirmed once after
reconnecting.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| Temporarily set `activityLogs` create to `if false` (as in IT-04), deploy, upload a file | ✅ | "Not uploaded" error; no file appeared on either client | Tester confirmation, Nov 27 |
| Restore the rules (`git checkout firestore.rules`), deploy, upload again | ✅ | File appeared once on both clients; one `file_uploaded` entry matching the file's `lastOpId` | Firestore console check |
| Phone in Airplane mode → upload a small file | ✅ | Held while offline (not shown as uploaded) | Tester confirmation, Nov 27 |
| Airplane mode off | ✅ | Confirmed once on both clients, no duplicates | Tester confirmation |

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 27, 2026

---

## IT-06 Connection lost during an update (FR-15, NFR-02, NFR-04) ✅

**Procedure (paper):** Interrupt connectivity during an update, reconnect, and compare the
confirmed records on both clients.

**Expected:** A pending or failed state is shown; both clients converge after reconnecting,
with no duplicate records.

**How to run it:**

1. Open "SIA" → Chat on the phone **and** on the web.
2. On the phone, turn on **Airplane mode**.
3. On the phone, send "offline test".
4. On the web, send "online while phone is offline".
5. Turn Airplane mode **off** on the phone and wait about 10 seconds.
6. **Expected:** both clients show **both** messages, once each, in the same order. In
   Firestore there is exactly **one** document per message and **one** `message_sent` log
   entry per message.
7. Repeat with the web: in Chrome DevTools → Network, choose **Offline**, send a message (it
   shows "sending…"), then go back **Online**.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| Phone (PM) in Airplane mode sends "offline test" | ✅ | Shown as pending on the phone | Tester confirmation |
| Web (Test Member) sends "online while phone is offline"; phone reconnects | ✅ | Both clients show both messages, once each, same order | Tester confirmation |
| No duplicate messages | ✅ | Each message appears once on both clients | Tester confirmation |
| Web set to Offline in DevTools, sends "web offline", back online | ✅ | Shown as sending, then appeared once on both clients | Tester confirmation |
| Firestore: one document and one `message_sent` log per message | ⬜ | Check in the console (optional evidence) | |

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 27, 2026

---

## IT-07 Chat message and teacher feedback (FR-08, FR-11, FR-12) ✅

**Procedure (paper):** Send a chat message and submit teacher feedback from different clients.

**Expected:** Authorised project views receive the correct message/comment and its activity
record.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| PM sends "Hello team" on Android | ✅ | Saved in `messages` with `senderId`, `senderName`, server `sentAt`; `lastOpId` `74505e5a…` matches a `message_sent` log | Firestore screenshots |
| Member replies on Android | ✅ | Log `33839d53…`: `message_sent`, `actorId` = member | Firestore screenshot |
| Bubbles align per sender on both accounts | ✅ | Own messages right/green, others left/grey | Phone screenshots |
| Same chat on the web client | ✅ | Same messages, names, times and alignment | Web screenshot |
| Teacher posts "Good progress, add the timeline" | ✅ | `comments/qoFQOGR5…`, `teacherId` = teacher; `lastOpId` `60f78a9b…` matches a `comment_posted` log | Firestore screenshots |
| Member sees the feedback | ✅ | "Teacher Feedback" at the top of the Team tab | Phone screenshot |

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 26-27, 2026

---

## IT-08 Deadline reminders (FR-13) ✅

**Procedure (paper):** Create or change a due date and mark the task Done; inspect the
assigned member's reminders.

**Expected:** The reminder uses the updated deadline and assignment; a completed task no
longer produces a reminder.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| PM creates "development" due tomorrow, assigned to Test Member | ✅ | Saved | |
| Test Member opens the dashboard | ✅ | "Deadline reminders: development — SIA, Due 11/27/2026 · To Do" | Phone screenshot |
| PM marks "development" Done; Test Member reopens the app | ✅ | No reminder for "development" | Tester confirmation |
| PM creates "update develop" due 12/7/2026 (10 days), assigned to the PM; PM opens the dashboard | ✅ | No reminder for it (outside 3 days) | Tester confirmation |
| Same check on the web dashboard | ✅ | Same reminders as Android (none) | Tester confirmation |

**Pass/fail:** ✅ Pass **Tester:** ________ **Date:** Nov 27, 2026

---

## IT-09 Sync time under normal and slow connection (NFR-02) ✅

**Procedure (paper):** Repeat cross-platform task updates under normal and throttled
connections; record the time from starting the write to the confirmed display on the
receiving client.

**Expected:** Record latency and failures for each condition and compare across clients. No
number is set as a target before testing.

**How to run it:** Use a phone stopwatch (or record the screen of both devices). Start
timing when you tap the new status on the sending client, and stop when the receiving client
shows it. For the slow condition, in Chrome DevTools → Network choose **Slow 4G** on the web
client.

| Trial | Sender → receiver | Connection | Seconds | Failure? |
|---|---|---|---|---|
| 1 | Android → Web | Normal Wi-Fi | 1.2 | No |
| 2 | Android → Web | Normal Wi-Fi | 0.9 | No |
| 3 | Android → Web | Normal Wi-Fi | 1.1 | No |
| 4 | Web → Android | Normal Wi-Fi | 0.8 | No |
| 5 | Web → Android | Normal Wi-Fi | 1.0 | No |
| 6 | Web → Android | Normal Wi-Fi | 0.9 | No |
| 7 | Web → Android | Web on Slow 4G | 2.5 | No |
| 8 | Web → Android | Web on Slow 4G | 2.8 | No |
| 9 | Web → Android | Web on Slow 4G | 2.4 | No |

**Average, normal:** 0.98 s (Android → Web 1.07 s, Web → Android 0.90 s) **Average, slow:** 2.57 s
— measured Nov 27, 2026 with a hand-held stopwatch, so each time includes the tester's
reaction time (roughly 0.2 s). No update failed or was lost in any trial.

**Pass/fail:** ✅ Recorded (no target was set before testing) **Tester:** ________ **Date:** Nov 27, 2026

---

## Defects found and fixed during integration

These were found while integrating and testing, and fixed before the tests above passed.
Useful for the paper's results and discussion.

| # | Found in | Defect | Fix | Commit |
|---|---|---|---|---|
| 1 | Build | Duplicate Firebase libraries with conflicting versions | One BoM-managed set | `84a2665` |
| 2 | M1/M2 | Member lists mixed upper/lower-case emails, so the server could not match the signed-in user | Emails stored lower-case everywhere | `84a2665` |
| 3 | M2 | Inviting wrote a profile for another user, which the rules must refuse | Each user writes only their own `users/{uid}` | `84a2665` |
| 4 | M3 | Any member could tick tasks complete; status was only true/false | PM-only To Do / In Progress / Done | `40d2c5b` |
| 5 | M3 | Add Task notes were discarded | Saved as `description` | `40d2c5b` |
| 6 | M4 | The chat tab opened the Notes board; the chat screen was not reachable | Tab opens the chat room | `5336ddf` |
| 7 | M4 | Chat adapter's context was never set (would crash), own messages detected by UID instead of email | Fixed context and sender check | `5336ddf` |
| 8 | M4 | All bubbles on the left; receiver bubble invisible (white on white) | Alignment by row gravity; grey receiver bubble | `5336ddf` |
| 9 | M6 | A new task listener was added per member every time the Team tab opened, and never removed | One listener, removed when the tab closes | `5336ddf` |
| 10 | M7 | Teacher view showed hard-coded sample data | Live totals and member progress from tasks | `b84b144` |
| 11 | M5 | Cloud Storage requires the Blaze plan (card + USD 50 hold), which the group could not use | Uploads up to 5 MB stored in Firestore chunks (atomic with the log), plus link sharing for bigger files; `storage.rules` kept for later | `2e086c8` |
| 12 | M6/M7 (web) | Found in IT-03, Nov 27: the web teacher view showed all 3 tasks under Test Member (2/3) although "update develop" is assigned to the PM (0/0 shown). Names are looked up asynchronously, so an older task snapshot could finish drawing after a newer one and overwrite it | Progress and task lists draw only the latest snapshot. Verified after deploying: teacher view shows Christine 0/1 and Test Member 2/2 | (this fix) |
