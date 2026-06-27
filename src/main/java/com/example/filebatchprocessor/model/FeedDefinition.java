package com.example.filebatchprocessor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Feed 定义:声明式映射地基。一个 feed 对应一类输入文件 + 其格式/目标表/业务键。 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "feed_definition")
public class FeedDefinition {

    @Id
    @Column(name = "feed_id", length = 100)
    private String feedId;

    @Column(name = "feed_name", length = 200)
    private String feedName;

    @Column(name = "format", length = 20)
    private String format = "CSV";

    @Column(name = "delimiter", length = 8)
    private String delimiter = ",";

    @Column(name = "has_header")
    private Boolean hasHeader;

    @Column(name = "target_table", length = 100)
    private String targetTable;

    @Column(name = "business_key_fields", length = 500)
    private String businessKeyFields;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
