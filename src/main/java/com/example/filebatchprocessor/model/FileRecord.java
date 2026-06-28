package com.example.filebatchprocessor.model;

import lombok.Data;

@Data
public class FileRecord {
    private Long id;
    private String name;
    private String description;

    private Long lineNo;

    private java.util.Map<String, Object> rawValues;

    private java.util.Map<String, Object> attributes;

    // Explicit getters and setters as workaround for Lombok annotation processing issue

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getLineNo() {
        return lineNo;
    }

    public void setLineNo(Long lineNo) {
        this.lineNo = lineNo;
    }

    public java.util.Map<String, Object> getRawValues() {
        return rawValues;
    }

    public void setRawValues(java.util.Map<String, Object> rawValues) {
        this.rawValues = rawValues;
    }

    public java.util.Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(java.util.Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    // You can add more fields as per your CSV structure
    // Add constructors, getters, and setters as needed
}
