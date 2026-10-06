package com.example.estimateserver.dto;

import com.example.estimateserver.model.ProjectRole;

public class ChangeRoleRequest {

    private ProjectRole role;

    public ChangeRoleRequest() {
    }

    public ProjectRole getRole() { return role; }
    public void setRole(ProjectRole role) { this.role = role; }
}