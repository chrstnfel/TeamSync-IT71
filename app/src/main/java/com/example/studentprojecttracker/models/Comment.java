package com.example.studentprojecttracker.models;

/**
 * Teacher feedback in projects/{projectId}/comments/{commentId}
 * (M7; Revised paper Section 4.7). teacherId is the teacher's email.
 */
public class Comment {
    private final String commentId;
    private final String teacherId;
    private final String teacherName;
    private final String text;
    private final long createdAt; // milliseconds; estimated while the server time is pending

    public Comment(String commentId, String teacherId, String teacherName, String text, long createdAt) {
        this.commentId = commentId;
        this.teacherId = teacherId;
        this.teacherName = teacherName;
        this.text = text;
        this.createdAt = createdAt;
    }

    public String getCommentId() { return commentId; }
    public String getTeacherId() { return teacherId; }
    public String getTeacherName() { return teacherName; }
    public String getText() { return text; }
    public long getCreatedAt() { return createdAt; }
}
