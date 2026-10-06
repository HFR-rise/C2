package com.example.estimateserver.dto;

import com.example.estimateserver.model.ChangeKind;
import com.example.estimateserver.model.ChangeStatus;
import com.example.estimateserver.model.ProjectChangeRequest;

import java.util.Date;

public class ChangeRequestDto {

    private String id;
    private String projectId;
    private String projectName;

    private String authorId;
    private String authorName;
    private String authorPhone;

    private ChangeKind kind;
    private ChangeStatus status;

    private String payloadJson;
    private String comment;
    private String reviewComment;

    private String reviewerId;
    private String reviewerName;
    private String reviewerPhone;

    private Date createdAt;
    private Date reviewedAt;
    private Long version;

    public ChangeRequestDto() {
    }

    public static ChangeRequestDto from(ProjectChangeRequest r,
                                        String projectName,
                                        String authorName,
                                        String authorPhone,
                                        String reviewerName,
                                        String reviewerPhone) {
        ChangeRequestDto dto = new ChangeRequestDto();
        dto.id = r.getId();
        dto.projectId = r.getProjectId();
        dto.projectName = projectName;
        dto.authorId = r.getAuthorId();
        dto.authorName = authorName;
        dto.authorPhone = authorPhone;
        dto.kind = r.getKind();
        dto.status = r.getStatus();
        dto.payloadJson = r.getPayloadJson();
        dto.comment = r.getComment();
        dto.reviewComment = r.getReviewComment();
        dto.reviewerId = r.getReviewerId();
        dto.reviewerName = reviewerName;
        dto.reviewerPhone = reviewerPhone;
        dto.createdAt = r.getCreatedAt();
        dto.reviewedAt = r.getReviewedAt();
        dto.version = r.getVersion();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorPhone() { return authorPhone; }
    public void setAuthorPhone(String authorPhone) { this.authorPhone = authorPhone; }

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

    public String getReviewerName() { return reviewerName; }
    public void setReviewerName(String reviewerName) { this.reviewerName = reviewerName; }

    public String getReviewerPhone() { return reviewerPhone; }
    public void setReviewerPhone(String reviewerPhone) { this.reviewerPhone = reviewerPhone; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Date reviewedAt) { this.reviewedAt = reviewedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}