package com.example.estimateserver.dto;

import com.example.estimateserver.model.User;

import java.util.Date;

public class UserDto {

    private String id;
    private String phoneNumber;
    private String name;
    private boolean verified;
    private Date createdAt;

    public UserDto() {
    }

    public static UserDto from(User user) {
        UserDto dto = new UserDto();
        dto.id = user.getId();
        dto.phoneNumber = user.getPhoneNumber();
        dto.name = user.getName();
        dto.verified = user.isVerified();
        dto.createdAt = user.getCreatedAt();
        return dto;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}