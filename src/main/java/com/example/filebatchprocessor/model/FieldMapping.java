package com.example.filebatchprocessor.model;

import com.example.filebatchprocessor.mapping.TransformOp;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 单条字段映射规则:source_column → target_field + 算子 + 必填/顺序。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "field_mapping")
public class FieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feed_id", length = 100)
    private String feedId;

    @Column(name = "source_column", length = 200)
    private String sourceColumn;

    @Column(name = "target_field", length = 200)
    private String targetField;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform_op", length = 20)
    private TransformOp transformOp = TransformOp.NONE;

    @Column(name = "transform_arg", length = 200)
    private String transformArg;

    @Column(name = "required")
    private boolean required;

    @Column(name = "order_no")
    private Integer orderNo;

    @Column(name = "enabled")
    private Boolean enabled;
}
