package com.example.estimateserver.model;

import jakarta.persistence.*;
import java.util.Date;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(name = "share_status")
    private String shareStatus = "PENDING";

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    private String objectId;
    private String customerContactId;
    private String foremanContactId;
    private String managerContactId;
    private boolean includeForeman;
    private boolean includeManager;

    private Date createdAt = new Date();
    private Date updatedAt = new Date();

    private String status = "ACTIVE";
    private Double totalBudget = 0.0;
    private Double totalSpent = 0.0;

    @Column(nullable = false)
    private String createdBy;

    private String lastModifiedBy;

    @Column(nullable = false)
    private String userId;

    @Version
    private Long version;

    public Project() {}

    public String getShareStatus() { return shareStatus; }
    public void setShareStatus(String shareStatus) { this.shareStatus = shareStatus; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public String getCustomerContactId() { return customerContactId; }
    public void setCustomerContactId(String customerContactId) { this.customerContactId = customerContactId; }

    public String getForemanContactId() { return foremanContactId; }
    public void setForemanContactId(String foremanContactId) { this.foremanContactId = foremanContactId; }

    public String getManagerContactId() { return managerContactId; }
    public void setManagerContactId(String managerContactId) { this.managerContactId = managerContactId; }

    public boolean isIncludeForeman() { return includeForeman; }
    public void setIncludeForeman(boolean includeForeman) { this.includeForeman = includeForeman; }

    public boolean isIncludeManager() { return includeManager; }
    public void setIncludeManager(boolean includeManager) { this.includeManager = includeManager; }

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
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}