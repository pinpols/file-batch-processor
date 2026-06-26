package com.example.filebatchprocessor.model;

import lombok.Data;

@Data
public class ExportRecord {
    private Long id;
    private String businessKey;
    private String name;
    private String description;
    private String batchDate;
}
