package com.example.filebatchprocessor.batch.writer.strategy;

import java.util.List;
import java.util.Map;

/**
 * 一次导入 step 内不变的上下文,随 chunk 透传给各 {@link ChunkImportStrategy}。
 */
public record ImportContext(
        String batchDate, Long jobExecutionId, String inputFileName, List<String> businessKeyFields) {

    /** 业务键 = name:batchDate,与历史口径一致。 */
    public String buildBusinessKey(String name) {
        String safeName = name == null ? "unknown" : name;
        return safeName + ":" + batchDate;
    }

    /**
     * 基于映射后的行构造业务键。
     * <p>未配置 {@code businessKeyFields} 时退回默认口径(name:batchDate),与
     * {@link #buildBusinessKey(String)} 字节级一致;配置时按字段顺序用 {@code |} 连接,
     * 再附加 {@code :batchDate}。
     */
    public String buildBusinessKeyFromFields(Map<String, Object> mappedRow) {
        if (businessKeyFields == null || businessKeyFields.isEmpty()) {
            Object n = mappedRow == null ? null : mappedRow.get("name");
            return buildBusinessKey(n == null ? null : String.valueOf(n));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < businessKeyFields.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            Object v = mappedRow == null ? null : mappedRow.get(businessKeyFields.get(i));
            sb.append(v == null ? "" : String.valueOf(v));
        }
        sb.append(':').append(batchDate);
        return sb.toString();
    }
}
