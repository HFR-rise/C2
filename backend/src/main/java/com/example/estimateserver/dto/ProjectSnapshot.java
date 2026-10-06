package com.example.estimateserver.dto;

import java.util.List;

public class ProjectSnapshot {

    private String name;
    private String description;
    private String objectId;
    private List<MaterialSnapshot> materials;
    private List<WorkItemSnapshot> workItems;
    private Double totalBudget;
    private String comment;

    public ProjectSnapshot() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getObjectId() { return objectId; }
    public void setObjectId(String objectId) { this.objectId = objectId; }

    public List<MaterialSnapshot> getMaterials() { return materials; }

    public List<WorkItemSnapshot> getWorkItems() { return workItems; }

    public void setMaterials(List<MaterialSnapshot> materials) {
        this.materials = materials;
    }

    public void setWorkItems(List<WorkItemSnapshot> workItems) {
        this.workItems = workItems;
    }

    public Double getTotalBudget() { return totalBudget; }
    public void setTotalBudget(Double totalBudget) { this.totalBudget = totalBudget; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public static class MaterialSnapshot {
        private String id;
        private String name;
        private Double quantity;
        private String unit;
        private Double unitPrice;
        private String category;
        private String notes;

        public MaterialSnapshot() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

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
    }

    public static class WorkItemSnapshot {
        private String id;
        private String name;
        private Integer stage;
        private Double laborHours;
        private Double hourlyRate;
        private Double materialCost;
        private Boolean isCompleted;
        private String notes;

        public WorkItemSnapshot() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getStage() { return stage; }
        public void setStage(Integer stage) { this.stage = stage; }

        public Double getLaborHours() { return laborHours; }
        public void setLaborHours(Double laborHours) { this.laborHours = laborHours; }

        public Double getHourlyRate() { return hourlyRate; }
        public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }

        public Double getMaterialCost() { return materialCost; }
        public void setMaterialCost(Double materialCost) { this.materialCost = materialCost; }

        public Boolean getIsCompleted() { return isCompleted; }
        public void setIsCompleted(Boolean isCompleted) { this.isCompleted = isCompleted; }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}