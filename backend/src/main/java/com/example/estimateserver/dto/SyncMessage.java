package com.example.estimateserver.dto;

import java.util.Date;


public class SyncMessage {
    private String type;
    private String entityType;
    private String entityId;
    private Object data;
    private String userId;
    private Date timestamp;
    private Long version;

    public SyncMessage() {}

    public SyncMessage(String type, String entityType, String entityId, Object data,
                       String userId, Date timestamp, Long version) {
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.data = data;
        this.userId = userId;
        this.timestamp = timestamp;
        this.version = version;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}