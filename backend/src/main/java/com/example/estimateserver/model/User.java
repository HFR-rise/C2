package com.example.estimateserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Version;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(unique = true, nullable = false)
    private String phoneNumber;

    private String name;

    private String verificationCode;

    @Temporal(TemporalType.TIMESTAMP)
    private Date codeExpiresAt;

    @Column(nullable = false)
    private boolean isVerified = false;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false, updatable = false)
    private Date createdAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date lastActiveAt;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "active_session_id")
    private String activeSessionId;

    @Column(name = "current_device_info")
    private String currentDeviceInfo;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_activity_at")
    private Date lastActivityAt;

    @Version
    @Column(nullable = false)
    private long version;

    public User() {
    }

    public User(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.userId = this.id;
    }

    @PrePersist
    void onCreate() {
        Date now = new Date();
        if (createdAt == null) createdAt = now;
        if (lastActiveAt == null) lastActiveAt = now;
        if (userId == null) userId = id;
    }

    @PreUpdate
    void onUpdate() {
        lastActiveAt = new Date();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }

    public Date getCodeExpiresAt() { return codeExpiresAt; }
    public void setCodeExpiresAt(Date codeExpiresAt) { this.codeExpiresAt = codeExpiresAt; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Date lastActiveAt) { this.lastActiveAt = lastActiveAt; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getActiveSessionId() { return activeSessionId; }
    public void setActiveSessionId(String activeSessionId) { this.activeSessionId = activeSessionId; }

    public String getCurrentDeviceInfo() { return currentDeviceInfo; }
    public void setCurrentDeviceInfo(String currentDeviceInfo) { this.currentDeviceInfo = currentDeviceInfo; }

    public Date getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Date lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id='" + id + "', phone='" + mask(phoneNumber) + "'}";
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}