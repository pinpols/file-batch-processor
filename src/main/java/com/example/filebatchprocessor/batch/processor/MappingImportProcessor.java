package com.example.filebatchprocessor.batch.processor;

import com.example.filebatchprocessor.exception.RecordValidationException;
import com.example.filebatchprocessor.mapping.MappingEngine;
import com.example.filebatchprocessor.model.FileRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.util.StringUtils;

/**
 * Feed 模式导入处理器：用声明式映射把原始列转换到 name/description/attributes;
 * 默认模式(record 无 rawValues)委托原 {@link FileImportRecordProcessor} 保持现行为。
 *
 * <p>非 @Component:由 JobConfig 在 feed 路由时按规则 new 出来。
 */
public class MappingImportProcessor implements ItemProcessor<FileRecord, FileRecord> {

    private final FileImportRecordProcessor delegate;
    private final MappingEngine mappingEngine;
    private final List<MappingEngine.MappingRule> rules;

    public MappingImportProcessor(
            FileImportRecordProcessor delegate,
            MappingEngine mappingEngine,
            List<MappingEngine.MappingRule> rules) {
        this.delegate = delegate;
        this.mappingEngine = mappingEngine;
        this.rules = rules;
    }

    @Override
    public FileRecord process(final FileRecord record) {
        if (record == null) {
            throw new RecordValidationException("Record is null");
        }
        if (record.getRawValues() == null) {
            // 默认模式:完全保持原行为
            return delegate.process(record);
        }

        // feed 模式
        Map<String, Object> mapped = mappingEngine.apply(rules, record.getRawValues());

        FileRecord out = new FileRecord();
        out.setId(record.getId());
        out.setLineNo(record.getLineNo());
        out.setName(Objects.toString(mapped.get("name"), null));
        out.setDescription(Objects.toString(mapped.get("description"), null));

        Map<String, Object> attrs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : mapped.entrySet()) {
            String key = e.getKey();
            if ("name".equals(key) || "description".equals(key)) {
                continue;
            }
            attrs.put(key, e.getValue());
        }
        out.setAttributes(attrs.isEmpty() ? null : attrs);

        if (!StringUtils.hasText(out.getName())) {
            throw new RecordValidationException("Record name is required");
        }

        return out;
    }
}
