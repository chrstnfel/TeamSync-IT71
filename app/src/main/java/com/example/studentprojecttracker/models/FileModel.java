package com.example.studentprojecttracker.models;

import com.example.studentprojecttracker.utils.DataContract;

/**
 * A shared file in projects/{projectId}/files/{fileId} (M5).
 * kind = "upload": the content is stored in Firestore chunks (up to 5 MB).
 * kind = "link":   the file lives at `url` (for example Google Drive).
 */
public class FileModel {

    private String fileId;
    private String name;
    private String url;
    private long uploadedAt;
    private String uploadedBy;    // email of the member who shared it
    private String uploaderName;
    private boolean pending;      // true until the server confirms the write
    private String kind = DataContract.KIND_LINK;
    private String mimeType;
    private long sizeBytes;

    // Required empty constructor for Firestore
    public FileModel() {}

    public FileModel(String name, String url, long uploadedAt) {
        this.name = name;
        this.url = url;
        this.uploadedAt = uploadedAt;
    }

    public FileModel(String fileId, String kind, String name, String url, String mimeType, long sizeBytes,
                     long uploadedAt, String uploadedBy, String uploaderName, boolean pending) {
        this(name, url, uploadedAt);
        this.fileId = fileId;
        this.kind = kind != null ? kind : DataContract.KIND_LINK;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.uploaderName = uploaderName;
        this.pending = pending;
    }

    public String getFileId() { return fileId; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public long getUploadedAt() { return uploadedAt; }
    public String getUploadedBy() { return uploadedBy; }
    public String getUploaderName() { return uploaderName; }
    public boolean isPending() { return pending; }
    public String getKind() { return kind; }
    public boolean isUpload() { return DataContract.KIND_UPLOAD.equals(kind); }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }

    public void setName(String name) { this.name = name; }
    public void setUrl(String url) { this.url = url; }
    public void setUploadedAt(long uploadedAt) { this.uploadedAt = uploadedAt; }
}
