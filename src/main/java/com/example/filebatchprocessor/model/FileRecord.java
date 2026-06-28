package com.example.filebatchprocessor.model;

import java.util.Map;

public class FileRecord {
    private Long id;
    private String name;
    private String description;

    private Long lineNo;

    private Map<String, Object> rawValues;

    private Map<String, Object> attributes;

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

    public Map<String, Object> getRawValues() {
        return rawValues;
    }

    public void setRawValues(Map<String, Object> rawValues) {
        this.rawValues = rawValues;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }
}
