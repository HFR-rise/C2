package com.example.estimateserver.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "objects")
public class ObjectModel {
    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String name;

    private String street;
    private String house;
    private String building;
    private String description;
    private String parentObjectId;
    private Long createdAt = System.currentTimeMillis();

    @Column(nullable = false)
    private String userId;

    public ObjectModel() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getHouse() { return house; }
    public void setHouse(String house) { this.house = house; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getParentObjectId() { return parentObjectId; }
    public void setParentObjectId(String parentObjectId) { this.parentObjectId = parentObjectId; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (street != null && !street.isBlank()) sb.append("ул. ").append(street);
        if (house != null && !house.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("д. ").append(house);
        }
        if (building != null && !building.isBlank()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("к. ").append(building);
        }
        return sb.toString();
    }
}
