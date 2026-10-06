package com.example.estimateserver.dto;

import java.util.List;

public class ContactSnapshot {
    private String name;
    private String description;
    private List<ContactMethodSnapshot> methods;

    public ContactSnapshot() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<ContactMethodSnapshot> getMethods() { return methods; }
    public void setMethods(List<ContactMethodSnapshot> methods) { this.methods = methods; }

    public static class ContactMethodSnapshot {
        private String id;
        private String methodType;
        private String value;

        public ContactMethodSnapshot() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getMethodType() { return methodType; }
        public void setMethodType(String methodType) { this.methodType = methodType; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}