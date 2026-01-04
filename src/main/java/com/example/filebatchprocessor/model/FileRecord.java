package com.example.filebatchprocessor.model;

import lombok.Data;

@Data
public class FileRecord {
    private Long id;
    private String name;
    private String description;
    
    // You can add more fields as per your CSV structure
    // Add constructors, getters, and setters as needed
}
