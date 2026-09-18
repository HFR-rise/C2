package com.example.estimateserver.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "materials")
public class Material {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String userId;

    private Double quantity = 0.0;
    private String unit = "??";
    private Double unitPrice = 0.0;
    private String category = "";
    private String notes = "";

    @Version
    private Long version;

    public Material() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Double getTotalPrice() {
        return quantity * unitPrice;
    }
}