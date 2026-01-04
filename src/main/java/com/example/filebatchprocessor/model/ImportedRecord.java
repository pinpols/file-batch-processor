package com.example.filebatchprocessor.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//import javax.persistence.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "imported_records", indexes = {
        @Index(name = "uk_import_biz_batch", columnList = "business_key,batch_date", unique = true)
})
public class ImportedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_key", length = 200, nullable = false)
    private String businessKey;

    @Column(length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "batch_date", length = 20, nullable = false)
    private String batchDate;
}


