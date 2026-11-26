package com.example.studentprojecttracker.utils;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * M8 Activity Log (Revised paper, Sections 4.3.3, 4.3.4 and NFR-09).
 *
 * A log entry is never written on its own. It is added to the same WriteBatch
 * as the change it describes, so the record and its log commit together or not
 * at all. The changed document stores the log ID in "lastOpId", and the
 * Security Rules reject the write unless that log entry exists after the batch.
 */
public final class ActivityLogger {

    private ActivityLogger() {}

    /** A new operation ID. Reusing the same ID on a retry cannot create a second log entry. */
    public static String newOpId() {
        return UUID.randomUUID().toString();
    }

    /** Email of the signed-in user, lower-case, or null when nobody is signed in. */
    public static String currentActorEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : DataContract.normalizeEmail(user.getEmail());
    }

    /** Adds the log entry projects/{projectId}/activityLogs/{opId} to the batch. */
    public static void addToBatch(FirebaseFirestore db, WriteBatch batch, String projectId,
                                  String opId, String action, String targetType, String targetId) {
        DocumentReference logRef = db.collection(DataContract.PROJECTS)
                .document(projectId)
                .collection(DataContract.ACTIVITY_LOGS)
                .document(opId);

        Map<String, Object> entry = new HashMap<>();
        entry.put(DataContract.LOG_ID, opId);
        entry.put(DataContract.LOG_PROJECT_ID, projectId);
        entry.put(DataContract.LOG_ACTOR_ID, currentActorEmail());
        entry.put(DataContract.LOG_ACTION, action);
        entry.put(DataContract.LOG_TARGET_TYPE, targetType);
        entry.put(DataContract.LOG_TARGET_ID, targetId);
        entry.put(DataContract.LOG_TIMESTAMP, FieldValue.serverTimestamp());

        batch.set(logRef, entry);
    }
}
