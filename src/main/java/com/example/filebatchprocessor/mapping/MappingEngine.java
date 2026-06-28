package com.example.filebatchprocessor.mapping;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 声明式字段映射纯函数:应用算子 + 必填校验。无副作用,可独立测试。 */
@Component
public class MappingEngine {

    /** 映射规则(实体 FieldMapping → 本 record 的转换在接线时做,引擎本身不依赖实体)。 */
    public record MappingRule(String sourceColumn, String targetField, TransformOp op, String arg, boolean required) {}

    public Map<String, Object> apply(List<MappingRule> rules, Map<String, Object> sourceRow) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (MappingRule rule : rules) {
            Object raw = sourceRow.get(rule.sourceColumn());
            Object value = applyOp(rule.op(), rule.arg(), raw);
            if (rule.required() && (value == null || String.valueOf(value).isBlank())) {
                throw new IllegalArgumentException("required target field missing: " + rule.targetField());
            }
            out.put(rule.targetField(), value);
        }
        return out;
    }

    private Object applyOp(TransformOp op, String arg, Object raw) {
        String s = raw == null ? null : String.valueOf(raw);
        return switch (op) {
            case NONE -> s;
            case TRIM -> s == null ? null : s.trim();
            case UPPER -> s == null ? null : s.toUpperCase(Locale.ROOT);
            case LOWER -> s == null ? null : s.toLowerCase(Locale.ROOT);
            case DEFAULT -> (s == null || s.isBlank()) ? arg : s;
            case DATE_FORMAT -> formatDate(s, arg);
        };
    }

    private String formatDate(String s, String sourcePattern) {
        if (s == null || s.isBlank()) {
            return s;
        }
        DateTimeFormatter src = DateTimeFormatter.ofPattern(
                (sourcePattern == null || sourcePattern.isBlank()) ? "yyyy-MM-dd" : sourcePattern);
        return LocalDate.parse(s.trim(), src).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
