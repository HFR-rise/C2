package com.example.estimateserver.dto;

import com.example.estimateserver.model.ProjectMember;
import com.example.estimateserver.model.ProjectRole;

import java.util.Date;

public class ProjectMemberDto {

    private String id;
    private String projectId;
    private String userId;
    private String phoneNumber;
    private String name;
    private ProjectRole role;
    private String addedBy;
    private Date addedAt;

    public ProjectMemberDto() {
    }

    public static ProjectMemberDto from(ProjectMember member, String phoneNumber, String name) {
        ProjectMemberDto dto = new ProjectMemberDto();
        dto.id = member.getId();
        dto.projectId = member.getProjectId();
        dto.userId = member.getUserId();
        dto.phoneNumber = phoneNumber;
        dto.name = name;
        dto.role = member.getRole();
        dto.addedBy = member.getAddedBy();
        dto.addedAt = member.getAddedAt();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }

    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }

    public Date getAddedAt() { return addedAt; }
    public void setAddedAt(Date addedAt) { this.addedAt = addedAt; }
}
