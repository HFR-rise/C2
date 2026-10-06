package com.example.estimateserver.dto;

public class ObjectSnapshot {
    private String name;
    private String street;
    private String house;
    private String building;
    private String description;
    private String parentObjectId;

    public ObjectSnapshot() {}

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
}