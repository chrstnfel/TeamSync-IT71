package com.example.studentprojecttracker.utils;

import android.util.Log;

import androidx.annotation.Nullable;

import com.example.studentprojecttracker.models.Note;
import com.example.studentprojecttracker.models.User;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import com.example.studentprojecttracker.models.Project;
import com.example.studentprojecttracker.models.Task;
import com.example.studentprojecttracker.models.FileModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreHelper {
    private static final String TAG = "FirestoreHelper";

    // Collection names
    public static final String COLLECTION_PROJECTS = "projects";
    public static final String COLLECTION_TASKS = "tasks";
    public static final String COLLECTION_FILES = "files";
    public static final String COLLECTION_USERS = "users";

    private final FirebaseFirestore db;

    public FirestoreHelper() {
        this.db = FirebaseFirestore.getInstance();
    }

    // ==================== PROJECT OPERATIONS ====================

    public interface ProjectCallback {
        void onSuccess(List<Project> projects);
        void onError(String error);
    }

    public interface SingleProjectCallback {
        void onSuccess(Project project);
        void onError(String error);
    }

    /**
     * T2 Create project (UC-02). The project document and its activity log entry
     * are committed in one atomic batch (Section 4.3.3, NFR-09).
     */
    public void createProject(Project project, OnCompleteListener listener) {
        String opId = ActivityLogger.newOpId();

        Map<String, Object> projectData = new HashMap<>();
        projectData.put(DataContract.PROJECT_ID, project.getProjectId());
        projectData.put(DataContract.PROJECT_NAME, project.getProjectName());
        projectData.put(DataContract.PROJECT_COURSE_CODE, project.getCourseCode());
        projectData.put(DataContract.PROJECT_DUE_DATE, project.getDueDate());
        projectData.put(DataContract.PROJECT_CREATOR_ID, DataContract.normalizeEmail(project.getCreatorId()));
        projectData.put(DataContract.PROJECT_LEADER_IDS, normalizeEmails(project.getLeaderIds()));
        projectData.put(DataContract.PROJECT_MEMBER_IDS, normalizeEmails(project.getMemberIds()));
        projectData.put(DataContract.PROJECT_MENTOR_IDS, normalizeEmails(project.getMentorIds()));
        projectData.put(DataContract.PROJECT_PROGRESS, project.getProgressPercentage());
        projectData.put(DataContract.CREATED_AT, System.currentTimeMillis());
        projectData.put(DataContract.LAST_OP_ID, opId);

        WriteBatch batch = db.batch();
        batch.set(db.collection(COLLECTION_PROJECTS).document(project.getProjectId()), projectData);
        ActivityLogger.addToBatch(db, batch, project.getProjectId(), opId,
                DataContract.ACTION_PROJECT_CREATED, DataContract.TARGET_PROJECT, project.getProjectId());

        commit(batch, "Project created successfully", listener);
    }

    /** Edit the project's details and membership lists (logged as project_updated). */
    public void updateProject(Project project, OnCompleteListener listener) {
        updateProject(project, DataContract.ACTION_PROJECT_UPDATED,
                DataContract.TARGET_PROJECT, project.getProjectId(), listener);
    }

    /**
     * Updates the project document together with an activity log entry in one
     * atomic batch. Use action/targetId to say what changed, for example
     * ACTION_MEMBER_INVITED with the invited email as targetId (T3).
     */
    public void updateProject(Project project, String action, String targetType, String targetId,
                              OnCompleteListener listener) {
        String opId = ActivityLogger.newOpId();

        Map<String, Object> updates = new HashMap<>();
        updates.put(DataContract.PROJECT_NAME, project.getProjectName());
        updates.put(DataContract.PROJECT_COURSE_CODE, project.getCourseCode());
        updates.put(DataContract.PROJECT_DUE_DATE, project.getDueDate());
        updates.put(DataContract.PROJECT_LEADER_IDS, normalizeEmails(project.getLeaderIds()));
        updates.put(DataContract.PROJECT_MEMBER_IDS, normalizeEmails(project.getMemberIds()));
        updates.put(DataContract.PROJECT_MENTOR_IDS, normalizeEmails(project.getMentorIds()));
        updates.put(DataContract.PROJECT_PROGRESS, project.getProgressPercentage());
        updates.put(DataContract.UPDATED_AT, System.currentTimeMillis());
        updates.put(DataContract.LAST_OP_ID, opId);

        WriteBatch batch = db.batch();
        batch.update(db.collection(COLLECTION_PROJECTS).document(project.getProjectId()), updates);
        ActivityLogger.addToBatch(db, batch, project.getProjectId(), opId, action, targetType, targetId);

        commit(batch, "Project updated successfully", listener);
    }

    public interface OperationCallback {
        void onSuccess();
        void onError(String error);
    }

    /** Replaces the member list (used when a member is removed), logged in the same batch. */
    public void updateProjectMembers(String projectId, List<String> memberIds, OperationCallback callback) {
        updateProjectList(projectId, DataContract.PROJECT_MEMBER_IDS, memberIds,
                DataContract.ACTION_MEMBER_REMOVED, callback);
    }

    /** Replaces the leader (Project Manager) list, logged in the same batch. */
    public void updateProjectLeaders(String projectId, List<String> leaderIds, OperationCallback callback) {
        updateProjectList(projectId, DataContract.PROJECT_LEADER_IDS, leaderIds,
                DataContract.ACTION_LEADERS_CHANGED, callback);
    }

    private void updateProjectList(String projectId, String field, List<String> emails,
                                   String action, OperationCallback callback) {
        String opId = ActivityLogger.newOpId();

        Map<String, Object> updates = new HashMap<>();
        updates.put(field, normalizeEmails(emails));
        updates.put(DataContract.UPDATED_AT, System.currentTimeMillis());
        updates.put(DataContract.LAST_OP_ID, opId);

        WriteBatch batch = db.batch();
        batch.update(db.collection(COLLECTION_PROJECTS).document(projectId), updates);
        ActivityLogger.addToBatch(db, batch, projectId, opId, action,
                DataContract.TARGET_PROJECT, projectId);

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, action + " committed with log " + opId);
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    // Atomic batch: neither the change nor the log entry was saved.
                    Log.e(TAG, action + " failed, nothing was saved", e);
                    if (callback != null) callback.onError(friendlyError(e));
                });
    }

    /** Commits a record + log batch and reports the result. */
    private void commit(WriteBatch batch, String successMessage, OnCompleteListener listener) {
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, successMessage);
                    if (listener != null) listener.onComplete(true, successMessage);
                })
                .addOnFailureListener(e -> {
                    // Atomic batch: neither the change nor the log entry was saved.
                    Log.e(TAG, "Batch failed, nothing was saved", e);
                    if (listener != null) listener.onComplete(false, friendlyError(e));
                });
    }

    /** Turns a Security Rules rejection into the message from Section 4.3.4. */
    private static String friendlyError(Exception e) {
        if (e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "You are not allowed to do this in this project.";
        }
        return e.getMessage();
    }

    private static List<String> normalizeEmails(List<String> emails) {
        List<String> result = new ArrayList<>();
        if (emails == null) return result;
        for (String email : emails) {
            String normalized = DataContract.normalizeEmail(email);
            if (normalized != null && !normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    public void deleteProject(String projectId, OnCompleteListener listener) {
        // Delete all tasks associated with the project first
        db.collection(COLLECTION_TASKS)
                .whereEqualTo("projectId", projectId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete();
                    }

                    // Then delete the project
                    db.collection(COLLECTION_PROJECTS)
                            .document(projectId)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Project deleted successfully");
                                if (listener != null) {
                                    listener.onComplete(true, "Project deleted successfully");
                                }
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error deleting project", e);
                                if (listener != null) {
                                    listener.onComplete(false, e.getMessage());
                                }
                            });
                });
    }

    public ListenerRegistration getUserProjects(String userId, boolean isAdvisor, ProjectCallback callback) {
        // FR-14: the query and the Security Rules both use the signed-in email.
        userId = DataContract.normalizeEmail(userId);
        Query query;

        if (isAdvisor) {
            // Advisors see projects they're mentoring
            query = db.collection(COLLECTION_PROJECTS)
                    .whereArrayContains("mentorIds", userId);
        } else {
            // Members see projects they're part of
            query = db.collection(COLLECTION_PROJECTS)
                    .whereArrayContains("memberIds", userId);
        }

        return query.addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Error getting projects", error);
                if (callback != null) {
                    callback.onError(error.getMessage());
                }
                return;
            }

            List<Project> projects = new ArrayList<>();
            if (querySnapshot != null) {
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Project project = documentToProject(doc);
                    if (project != null) {
                        projects.add(project);
                    }
                }
            }
            if (callback != null) {
                callback.onSuccess(projects);
            }
        });
    }

    public void getProject(String projectId, SingleProjectCallback callback) {
        db.collection(COLLECTION_PROJECTS)
                .document(projectId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Project project = documentToProject(documentSnapshot);
                        if (callback != null) {
                            callback.onSuccess(project);
                        }
                    } else {
                        if (callback != null) {
                            callback.onError("Project not found");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting project", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // ==================== TASK OPERATIONS ====================

    public interface TaskCallback {
        void onSuccess(List<Task> tasks);
        void onError(String error);
    }

    public void createTask(Task task, OnCompleteListener listener) {
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("taskId", task.getTaskId());
        taskData.put("taskName", task.getTaskName());
        taskData.put("priority", task.getPriority());
        taskData.put("assigneeId", task.getAssigneeId());
        taskData.put("assigneeName", task.getAssigneeName());
        taskData.put("dueDate", task.getDueDate());
        taskData.put("isCompleted", task.isCompleted());
        taskData.put("projectId", task.getProjectId());
        taskData.put("createdBy", task.getCreatedBy());
        taskData.put("createdAt", System.currentTimeMillis());

        db.collection(COLLECTION_TASKS)
                .document(task.getTaskId())
                .set(taskData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Task created successfully");
                    if (listener != null) {
                        listener.onComplete(true, "Task created successfully");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating task", e);
                    if (listener != null) {
                        listener.onComplete(false, e.getMessage());
                    }
                });
    }

    public void updateTask(Task task, OnCompleteListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("taskName", task.getTaskName());
        updates.put("priority", task.getPriority());
        updates.put("assigneeId", task.getAssigneeId());
        updates.put("assigneeName", task.getAssigneeName());
        updates.put("dueDate", task.getDueDate());
        updates.put("isCompleted", task.isCompleted());
        updates.put("updatedAt", System.currentTimeMillis());

        db.collection(COLLECTION_TASKS)
                .document(task.getTaskId())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Task updated successfully");
                    if (listener != null) {
                        listener.onComplete(true, "Task updated successfully");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating task", e);
                    if (listener != null) {
                        listener.onComplete(false, e.getMessage());
                    }
                });
    }

    public void deleteTask(String taskId, OnCompleteListener listener) {
        db.collection(COLLECTION_TASKS)
                .document(taskId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Task deleted successfully");
                    if (listener != null) {
                        listener.onComplete(true, "Task deleted successfully");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting task", e);
                    if (listener != null) {
                        listener.onComplete(false, e.getMessage());
                    }
                });
    }

    public ListenerRegistration getProjectTasks(String projectId, TaskCallback callback) {
        return db.collection(COLLECTION_TASKS)
                .whereEqualTo("projectId", projectId)
                // Removed orderBy - will add back after creating index
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error getting tasks", error);
                        if (callback != null) {
                            callback.onError(error.getMessage());
                        }
                        return;
                    }

                    List<Task> tasks = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            Task task = documentToTask(doc);
                            if (task != null) {
                                tasks.add(task);
                            }
                        }
                    }
                    if (callback != null) {
                        callback.onSuccess(tasks);
                    }
                });
    }

    // ==================== FILE OPERATIONS ====================

    public interface FileCallback {
        void onSuccess(List<FileModel> files);
        void onError(String error);
    }

    public void saveFileMetadata(String projectId, FileModel file, OnCompleteListener listener) {
        Map<String, Object> fileData = new HashMap<>();
        fileData.put("name", file.getName());
        fileData.put("url", file.getUrl());
        fileData.put("uploadedAt", file.getUploadedAt());
        fileData.put("projectId", projectId);

        db.collection(COLLECTION_FILES)
                .add(fileData)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "File metadata saved successfully");
                    if (listener != null) {
                        listener.onComplete(true, "File uploaded successfully");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving file metadata", e);
                    if (listener != null) {
                        listener.onComplete(false, e.getMessage());
                    }
                });
    }

    public ListenerRegistration getProjectFiles(String projectId, FileCallback callback) {
        return db.collection(COLLECTION_FILES)
                .whereEqualTo("projectId", projectId)
                // Removed orderBy - will add back after creating index
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error getting files", error);
                        if (callback != null) {
                            callback.onError(error.getMessage());
                        }
                        return;
                    }

                    List<FileModel> files = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            FileModel file = documentToFile(doc);
                            if (file != null) {
                                files.add(file);
                            }
                        }
                    }
                    if (callback != null) {
                        callback.onSuccess(files);
                    }
                });
    }

    // ==================== USER OPERATIONS ====================

    public interface UserCallback {
        void onSuccess(User user);
        void onError(String error);
    }

    public void saveUserProfile(User user, OnCompleteListener listener) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", user.getUserId());
        userData.put("name", user.getName());
        userData.put("email", DataContract.normalizeEmail(user.getEmail()));
        userData.put("role", user.getRole());

        // Add photoUrl if it exists
        if (user.getPhotoUrl() != null && !user.getPhotoUrl().isEmpty()) {
            userData.put("photoUrl", user.getPhotoUrl());
        }

        userData.put("updatedAt", System.currentTimeMillis());

        db.collection(COLLECTION_USERS)
                .document(user.getUserId())
                .set(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile saved successfully");
                    if (listener != null) {
                        listener.onComplete(true, "Profile saved successfully");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user profile", e);
                    if (listener != null) {
                        listener.onComplete(false, e.getMessage());
                    }
                });
    }


    /**
     * T1 Register or log in (M1). Makes sure users/{uid} exists for the signed-in
     * account so teammates can find this user's name by email. Each user only
     * writes their own profile; Security Rules reject writes to other profiles.
     */
    public void ensureOwnProfile(String displayName) {
        com.google.firebase.auth.FirebaseUser authUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (authUser == null) return;

        String uid = authUser.getUid();
        String email = DataContract.normalizeEmail(authUser.getEmail());
        String name = (displayName != null && !displayName.isEmpty()) ? displayName
                : (authUser.getDisplayName() != null ? authUser.getDisplayName() : email);

        db.collection(COLLECTION_USERS).document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) return;
                    Map<String, Object> userData = new HashMap<>();
                    userData.put(DataContract.USER_ID, uid);
                    userData.put(DataContract.USER_NAME, name);
                    userData.put(DataContract.USER_EMAIL, email);
                    userData.put(DataContract.USER_ROLE, "member");
                    userData.put(DataContract.UPDATED_AT, System.currentTimeMillis());
                    db.collection(COLLECTION_USERS).document(uid).set(userData)
                            .addOnFailureListener(e -> Log.e(TAG, "Could not create own profile", e));
                })
                .addOnFailureListener(e -> Log.e(TAG, "Could not read own profile", e));
    }

    public void getUserProfile(String userId, UserCallback callback) {
        db.collection(COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        try {
                            String id = documentSnapshot.getString("userId");
                            String name = documentSnapshot.getString("name");
                            String email = documentSnapshot.getString("email");
                            String role = documentSnapshot.getString("role");
                            String photoUrl = documentSnapshot.getString("photoUrl");

                            User user = new User(id, name, email, role, photoUrl);
                            if (callback != null) {
                                callback.onSuccess(user);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user document", e);
                            if (callback != null) {
                                callback.onError(e.getMessage());
                            }
                        }
                    } else {
                        if (callback != null) {
                            callback.onError("User not found");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user profile", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    /**
     * Get user profile by email (queries the email field instead of document ID)
     */
    public void getUserProfileByEmail(String email, UserCallback callback) {
        email = DataContract.normalizeEmail(email);
        db.collection(COLLECTION_USERS)
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        try {
                            String id = doc.getString("userId");
                            String name = doc.getString("name");
                            String userEmail = doc.getString("email");
                            String role = doc.getString("role");
                            String photoUrl = doc.getString("photoUrl");

                            User user = new User(id, name, userEmail, role, photoUrl);
                            if (callback != null) {
                                callback.onSuccess(user);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing user document", e);
                            if (callback != null) {
                                callback.onError(e.getMessage());
                            }
                        }
                    } else {
                        if (callback != null) {
                            callback.onError("User not found");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user profile by email", e);
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                });
    }

    // ==================== NOTE OPERATIONS ====================
    public interface NoteCallback {
        void onSuccess(List<Note> notes);
        void onError(String error);
    }

    public void addProjectNote(String projectId, Note note, @Nullable OnCompleteListener listener) {
        db.collection(COLLECTION_PROJECTS)
                .document(projectId)
                .collection("notes")
                .document(note.getNoteId())
                .set(note)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onComplete(true, "Note saved successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving note", e);
                    if (listener != null) listener.onComplete(false, e.getMessage());
                });
    }

    /**
     * Delete a note
     */
    public void deleteProjectNote(String projectId, String noteId, @Nullable OnCompleteListener listener) {
        db.collection(COLLECTION_PROJECTS)
                .document(projectId)
                .collection("notes")
                .document(noteId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onComplete(true, "Note deleted successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting note", e);
                    if (listener != null) listener.onComplete(false, e.getMessage());
                });
    }

    /**
     * Listen for real-time notes
     */
    public ListenerRegistration getProjectNotes(String projectId, NoteCallback callback) {
        Query query = db.collection(COLLECTION_PROJECTS)
                .document(projectId)
                .collection("notes")
                .orderBy("timestamp");

        return query.addSnapshotListener((querySnapshot, error) -> {
            if (error != null) {
                if (callback != null) callback.onError(error.getMessage());
                return;
            }

            List<Note> notes = new ArrayList<>();
            if (querySnapshot != null) {
                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                    Note note = doc.toObject(Note.class);
                    if (note != null) notes.add(note);
                }
            }

            if (callback != null) callback.onSuccess(notes);
        });
    }


    // ==================== HELPER METHOD FOR TYPE-SAFE LIST CONVERSION ====================

    @SuppressWarnings("unchecked")
    private List<String> getStringList(DocumentSnapshot doc, String field) {
        Object obj = doc.get(field);
        if (obj instanceof List<?>) {
            List<?> list = (List<?>) obj;
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    // ==================== HELPER METHODS ====================

    private Project documentToProject(DocumentSnapshot doc) {
        try {
            String projectId = doc.getString("projectId");
            String projectName = doc.getString("projectName");
            String courseCode = doc.getString("courseCode");
            String dueDate = doc.getString("dueDate");
            String creatorId = doc.getString("creatorId");

            Project project = new Project(projectId, projectName, courseCode, dueDate, creatorId);

            // Use helper method for type-safe list conversion
            List<String> leaderIds = getStringList(doc, "leaderIds");
            if (!leaderIds.isEmpty()) {
                project.setLeaderIds(leaderIds);
            }

            List<String> memberIds = getStringList(doc, "memberIds");
            if (!memberIds.isEmpty()) {
                project.setMemberIds(memberIds);
            }

            List<String> mentorIds = getStringList(doc, "mentorIds");
            if (!mentorIds.isEmpty()) {
                project.setMentorIds(mentorIds);
            }

            Long progress = doc.getLong("progressPercentage");
            if (progress != null) {
                project.setProgressPercentage(progress.intValue());
            }

            return project;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing project document", e);
            return null;
        }
    }

    private Task documentToTask(DocumentSnapshot doc) {
        try {
            String taskId = doc.getString("taskId");
            String taskName = doc.getString("taskName");
            String priority = doc.getString("priority");
            String assigneeId = doc.getString("assigneeId");
            String assigneeName = doc.getString("assigneeName");
            String dueDate = doc.getString("dueDate");
            Boolean isCompleted = doc.getBoolean("isCompleted");
            String projectId = doc.getString("projectId");
            String createdBy = doc.getString("createdBy");

            return new Task(
                    taskId,
                    taskName,
                    priority,
                    assigneeId,
                    assigneeName,
                    dueDate,
                    isCompleted != null && isCompleted,
                    projectId,
                    createdBy
            );
        } catch (Exception e) {
            Log.e(TAG, "Error parsing task document", e);
            return null;
        }
    }

    private FileModel documentToFile(DocumentSnapshot doc) {
        try {
            String name = doc.getString("name");
            String url = doc.getString("url");
            Long uploadedAt = doc.getLong("uploadedAt");

            return new FileModel(name, url, uploadedAt != null ? uploadedAt : 0);
        } catch (Exception e) {
            Log.e(TAG, "Error parsing file document", e);
            return null;
        }
    }

    // ==================== CALLBACK INTERFACE ====================

    public interface OnCompleteListener {
        void onComplete(boolean success, String message);
    }
}