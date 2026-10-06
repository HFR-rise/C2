package com.example.estimateserver.dto;

import com.example.estimateserver.model.ProjectRole;

import java.util.List;

public class ProjectMemberRequest {

    private String phoneNumber;
    private ProjectRole role;
    private List<String> phoneNumbers;

    public ProjectMemberRequest() {
    }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }

    public List<String> getPhoneNumbers() { return phoneNumbers; }
    public void setPhoneNumbers(List<String> phoneNumbers) { this.phoneNumbers = phoneNumbers; }
}