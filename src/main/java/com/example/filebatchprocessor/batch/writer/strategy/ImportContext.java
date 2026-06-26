package com.example.filebatchprocessor.batch.writer.strategy;

/**
 * 一次导入 step 内不变的上下文,随 chunk 透传给各 {@link ChunkImportStrategy}。
 */
public record ImportContext(String batchDate, Long jobExecutionId, String inputFileName) {

    /** 业务键 = name:batchDate,与历史口径一致。 */
    public String buildBusinessKey(String name) {
        String safeName = name == null ? "unknown" : name;
        return safeName + ":" + batchDate;
    }
}
