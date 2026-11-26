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
| IT-01 Cross-platform task status | FR-06, FR-07, FR-10, FR-11, FR-15 | 🟡 |
| IT-02 Role-based access | FR-01, FR-14, NFR-03 | 🟡 |
| IT-03 File upload and download | FR-09, FR-12 | ⏸ Blaze plan needed |
| IT-04 Task update fails if its log fails | FR-06, FR-12, NFR-09 | ⬜ |
| IT-05 File metadata failure recovery | FR-09, FR-12, NFR-09 | ⏸ Blaze plan needed |
| IT-06 Connection lost during an update | FR-15, NFR-02, NFR-04 | ⬜ |
| IT-07 Chat message and teacher feedback | FR-08, FR-11, FR-12 | ✅ |
| IT-08 Deadline reminders | FR-13 | 🟡 |
| IT-09 Sync time under normal/slow connection | NFR-02 | ⬜ |

---

## IT-01 Cross-platform task status (FR-06, FR-07, FR-10, FR-11, FR-15) 🟡

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
| **To do:** change a status **on the web** (dropdown) and watch the phone update without refreshing | ⬜ | | |
| **To do:** with the teacher view open on the web, change a status on the phone | ⬜ | | |

**Pass/fail:** ________ **Tester:** ________ **Date:** ________

---

## IT-02 Role-based access (FR-01, FR-14, NFR-03) 🟡

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
| **To do:** log in as `outsider.test@teamsync.test` | ⬜ | Expected: "SIA" does **not** appear | |
| **To do (server side):** Rules Playground, see below | ⬜ | | |

**Server-side check with the Rules Playground** (Firebase console → Firestore → Rules → *Rules
Playground*). The app hides forbidden buttons, so this proves the **server** also refuses:

| # | Simulate | Location | Authenticated as (`email` in token) | Expected |
|---|---|---|---|---|
| a | `update` | `projects/<SIA id>/tasks/<task id>` | `member.test@teamsync.test` | **Denied** (only the PM marks status) |
| b | `get` | `projects/<SIA id>` | `outsider.test@teamsync.test` | **Denied** (not in the project) |
| c | `create` | `projects/<SIA id>/comments/x` | `member.test@teamsync.test` | **Denied** (only a teacher comments) |
| d | `get` | `projects/<SIA id>/messages/<msg id>` | `teacher.test@teamsync.test` | **Denied** (teachers have no chat access, 4.7) |
| e | `get` | `projects/<SIA id>/tasks/<task id>` | `teacher.test@teamsync.test` | **Allowed** (teacher reads progress) |
| f | `update` / `delete` | `projects/<SIA id>/activityLogs/<log id>` | the PM's email | **Denied** (logs are never edited) |

In the Playground, tick **Authenticated**, choose provider **password**, and set the token's
`email` field to the address in the table. Screenshot each result.

**Pass/fail:** ________ **Tester:** ________ **Date:** ________

---

## IT-03 File upload and download (FR-09, FR-12) ⏸

**Blocked:** Cloud Storage for Firebase needs the Blaze plan (Chapter I limitations, Google
n.d.-b). M5 will be connected in increment (c) part 2.

**Procedure:** Upload a valid file, then view or download it from the other client. Try a
file over 25 MB.

**Expected:** The correct file and its metadata appear with one log entry; the oversized file
is rejected before upload.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| Upload a small PDF on Android | ⬜ | | |
| Open it from the web | ⬜ | | |
| One `file_uploaded` log entry | ⬜ | | |
| Try a file over 25 MB | ⬜ | | |

---

## IT-04 Task update fails if its log fails (FR-06, FR-12, NFR-09) ⬜

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
| 3. Change status while logs are blocked | ⬜ | | |
| 3. Task unchanged in Firestore, no new log | ⬜ | | |
| 5. Retry after restoring rules | ⬜ | | |
| 5. One status change + one log, matching IDs | ⬜ | | |

**Pass/fail:** ________ **Tester:** ________ **Date:** ________

---

## IT-05 File metadata failure recovery (FR-09, FR-12, NFR-09) ⏸

**Blocked:** needs Cloud Storage (Blaze plan), like IT-03.

**Procedure:** Allow the Storage upload but reject the Firestore metadata/log batch. Restore
permission and retry; also test abandoning the upload.

**Expected:** No file entry is published before the commit; a retry creates one record and
one log entry; an abandoned upload is cleaned up or flagged.

---

## IT-06 Connection lost during an update (FR-15, NFR-02, NFR-04) ⬜

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
| Phone offline send | ⬜ | | |
| Both clients converge after reconnect | ⬜ | | |
| No duplicate messages or logs | ⬜ | | |
| Web shows "sending…" while offline | ⬜ | | |

**Pass/fail:** ________ **Tester:** ________ **Date:** ________

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

## IT-08 Deadline reminders (FR-13) 🟡

**Procedure (paper):** Create or change a due date and mark the task Done; inspect the
assigned member's reminders.

**Expected:** The reminder uses the updated deadline and assignment; a completed task no
longer produces a reminder.

| Step | Done | Actual result | Evidence |
|---|---|---|---|
| PM creates "development" due tomorrow, assigned to Test Member | ✅ | Saved | |
| Test Member opens the dashboard | ✅ | "Deadline reminders: development — SIA, Due 11/27/2026 · To Do" | Phone screenshot |
| **To do:** PM marks "development" Done; Test Member reopens the app | ⬜ | Expected: **no** reminder for it | |
| **To do:** PM creates a task due in 10 days | ⬜ | Expected: **no** reminder (outside 3 days) | |
| **To do:** same check on the web dashboard | ⬜ | Expected: the same reminders as Android | |

**Pass/fail:** ________ **Tester:** ________ **Date:** ________

---

## IT-09 Sync time under normal and slow connection (NFR-02) ⬜

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
| 1 | Android → Web | Normal Wi-Fi | | |
| 2 | Android → Web | Normal Wi-Fi | | |
| 3 | Android → Web | Normal Wi-Fi | | |
| 4 | Web → Android | Normal Wi-Fi | | |
| 5 | Web → Android | Normal Wi-Fi | | |
| 6 | Web → Android | Normal Wi-Fi | | |
| 7 | Web → Android | Web on Slow 4G | | |
| 8 | Web → Android | Web on Slow 4G | | |
| 9 | Web → Android | Web on Slow 4G | | |

**Average, normal:** ______ s **Average, slow:** ______ s

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
