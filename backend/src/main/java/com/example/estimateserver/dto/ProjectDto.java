package com.example.estimateserver.dto;

import com.example.estimateserver.model.Material;
import com.example.estimateserver.model.Project;
import com.example.estimateserver.model.ProjectRole;
import com.example.estimateserver.model.ProjectState;
import com.example.estimateserver.model.WorkItem;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ProjectDto {

    private String id;
    private String name;
    private String description;
    private ProjectState state;
    private boolean hasPendingChanges;
    private String objectId;
    private Date createdAt;
    private Date updatedAt;
    private String status;
    private Double totalBudget;
    private Double totalSpent;
    private String createdBy;
    private String lastModifiedBy;
    private Long version;

    private ProjectRole myRole;

    private List<ProjectMemberDto> members = new ArrayList<>();

    private List<Material> materials = new ArrayList<>();

    private List<WorkItem> workItems = new ArrayList<>();

    public ProjectDto() {
    }

    public static ProjectDto from(Project p, ProjectRole myRole) {
        ProjectDto dto = new ProjectDto();
        dto.id = p.getId();
        dto.name = p.getName();
        dto.description = p.getDescription();
        dto.state = p.getState();
        dto.hasPendingChanges = p.isHasPendingChanges();
        dto.objectId = p.getObjectId();
        dto.createdAt = p.getCreatedAt();
        dto.updatedAt = p.getUpdatedAt();
        dto.status = p.getStatus();
        dto.totalBudget = p.getTotalBudget();
        dto.totalSpent = p.getTotalSpent();
        dto.createdBy = p.getCreatedBy();
        dto.lastModifiedBy = p.getLastModifiedBy();
        dto.version = p.getVersion();
        dto.myRole = myRole;
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public ProjectState getState() { return state; }
    public void setState(ProjectState state) { this.state = state; }

    public boolean isHasPendingChanges() { return hasPendingChanges; }
    public void setHasPendingChanges(boolean hasPendingChanges) {
        this.hasPendingChanges = hasPendingChanges;
    }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getTotalBudget() { return totalBudget; }
    public void setTotalBudget(Double totalBudget) { this.totalBudget = totalBudget; }

    public Double getTotalSpent() { return totalSpent; }
    public void setTotalSpent(Double totalSpent) { this.totalSpent = totalSpent; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getLastModifiedBy() { return lastModifiedBy; }
    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public ProjectRole getMyRole() { return myRole; }
    public void setMyRole(ProjectRole myRole) { this.myRole = myRole; }

    public List<ProjectMemberDto> getMembers() { return members; }
    public void setMembers(List<ProjectMemberDto> members) {
        this.members = members != null ? members : new ArrayList<>();
    }

    public List<Material> getMaterials() { return materials; }
    public void setMaterials(List<Material> materials) {
        this.materials = materials != null ? materials : new ArrayList<>();
    }

    public List<WorkItem> getWorkItems() { return workItems; }
    public void setWorkItems(List<WorkItem> workItems) {
        this.workItems = workItems != null ? workItems : new ArrayList<>();
    }
}
