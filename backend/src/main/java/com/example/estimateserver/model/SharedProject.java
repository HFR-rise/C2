package com.example.estimateserver.model;

import com.example.estimateserver.model.ShareStatus;
import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "shared_projects")
public class SharedProject {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String sharedWithUserId;

    @Column(nullable = false)
    private String sharedByUserId;

    private String permission = "READ";

    @Enumerated(EnumType.STRING)
    private ShareStatus status = ShareStatus.PENDING;

    private Date sharedAt = new Date();
    private Date respondedAt;

    public SharedProject() {}

    public SharedProject(String projectId, String sharedWithUserId, String sharedByUserId, String permission) {
        this.projectId = projectId;
        this.sharedWithUserId = sharedWithUserId;
        this.sharedByUserId = sharedByUserId;
        this.permission = permission;
        this.status = ShareStatus.PENDING;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getSharedWithUserId() { return sharedWithUserId; }
    public void setSharedWithUserId(String sharedWithUserId) { this.sharedWithUserId = sharedWithUserId; }

    public String getSharedByUserId() { return sharedByUserId; }
    public void setSharedByUserId(String sharedByUserId) { this.sharedByUserId = sharedByUserId; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public ShareStatus getStatus() { return status; }
    public void setStatus(ShareStatus status) { this.status = status; }

    public Date getSharedAt() { return sharedAt; }
    public void setSharedAt(Date sharedAt) { this.sharedAt = sharedAt; }

    public Date getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Date respondedAt) { this.respondedAt = respondedAt; }
}