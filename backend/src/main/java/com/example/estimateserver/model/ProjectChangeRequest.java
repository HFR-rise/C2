package com.example.estimateserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "project_change_requests",
        indexes = {
                @Index(name = "idx_change_project", columnList = "project_id"),
                @Index(name = "idx_change_author", columnList = "author_id"),
                @Index(name = "idx_change_status", columnList = "status"),
                @Index(name = "idx_change_project_status", columnList = "project_id,status")
        }
)
public class ProjectChangeRequest {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Column(name = "author_id", nullable = false, length = 36)
    private String authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private ChangeKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ChangeStatus status = ChangeStatus.PENDING;

    @Lob
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Lob
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Lob
    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewer_id", length = 36)
    private String reviewerId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at", nullable = false)
    private Date createdAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "reviewed_at")
    private Date reviewedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public ProjectChangeRequest() {
    }

    public ProjectChangeRequest(String projectId, String authorId, ChangeKind kind) {
        this.projectId = projectId;
        this.authorId = authorId;
        this.kind = kind;
        this.status = ChangeStatus.PENDING;
        this.createdAt = new Date();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public ChangeKind getKind() { return kind; }
    public void setKind(ChangeKind kind) { this.kind = kind; }

    public ChangeStatus getStatus() { return status; }
    public void setStatus(ChangeStatus status) { this.status = status; }

    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

    public String getReviewerId() { return reviewerId; }
    public void setReviewerId(String reviewerId) { this.reviewerId = reviewerId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectChangeRequest other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ProjectChangeRequest{id='" + id + "', projectId='" + projectId +
                "', authorId='" + authorId + "', kind=" + kind +
                ", status=" + status + '}';
    }
}