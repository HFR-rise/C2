package com.example.estimateserver.dto;

public class CommentRequest {

    private String text;
    private String entityType;
    private String entityId;

    public CommentRequest() {
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
}