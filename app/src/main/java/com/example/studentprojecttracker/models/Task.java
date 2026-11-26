package com.example.studentprojecttracker.models;

import com.example.studentprojecttracker.utils.DataContract;

import java.io.Serializable;

/**
 * A task in projects/{projectId}/tasks/{taskId} (M3; Revised paper Section 4.7).
 * Firestore fields: title, description, assignedTo (email), dueDate, status,
 * priority, projectId, createdBy. assigneeName is only used for display.
 */
public class Task implements Serializable {
    private String taskId;
    private String taskName;      // stored as "title"
    private String description;
    private String priority;
    private String assigneeId;    // stored as "assignedTo" (email of the member)
    private String assigneeName;  // display only, not stored
    private String dueDate;       // shown as M/d/yyyy, stored as a Firestore timestamp
    private String status = DataContract.STATUS_TODO;
    private String projectId;
    private String createdBy;     // Project Manager who created the task

    public Task() {
    }

    public Task(String taskId, String taskName, String description, String priority,
                String assigneeId, String assigneeName, String dueDate, String status,
                String projectId, String createdBy) {
        this.taskId = taskId;
        this.taskName = taskName;
        this.description = description;
        this.priority = priority;
        this.assigneeId = assigneeId;
        this.assigneeName = assigneeName;
        this.dueDate = dueDate;
        this.status = status != null ? status : DataContract.STATUS_TODO;
        this.projectId = projectId;
        this.createdBy = createdBy;
    }

    // Getters and Setters
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }

    public String getAssigneeName() { return assigneeName; }
    public void setAssigneeName(String assigneeName) { this.assigneeName = assigneeName; }

    // Legacy getter for compatibility
    public String getAssignee() { return assigneeName; }
    public void setAssignee(String assignee) { this.assigneeName = assignee; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    /** To Do, In Progress or Done (FR-06). */
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** True when the status is Done; used by the member progress summary. */
    public boolean isCompleted() { return DataContract.STATUS_DONE.equals(status); }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
