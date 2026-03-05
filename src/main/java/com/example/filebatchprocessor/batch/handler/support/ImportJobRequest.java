package com.example.filebatchprocessor.batch.handler.support;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImportJobRequest {
    private final String inputFile;
    private final String batchDate;
    private final String runMode;
    private final String rerunId;
    private final String dedupKey;
    private final int priority;
    private final int maxRetries;
    private final long backoffMs;
    private final long maxDurationMs;
    private final long timeoutMs;
    private final String fileFormat;
    private final String fileDelimiter;

    // Explicit builder as workaround for Lombok annotation processing issue
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String inputFile;
        private String batchDate;
        private String runMode;
        private String rerunId;
        private String dedupKey;
        private int priority;
        private int maxRetries;
        private long backoffMs;
        private long maxDurationMs;
        private long timeoutMs;
        private String fileFormat;
        private String fileDelimiter;

        public Builder inputFile(String inputFile) {
            this.inputFile = inputFile;
            return this;
        }

        public Builder batchDate(String batchDate) {
            this.batchDate = batchDate;
            return this;
        }

        public Builder runMode(String runMode) {
            this.runMode = runMode;
            return this;
        }

        public Builder rerunId(String rerunId) {
            this.rerunId = rerunId;
            return this;
        }

        public Builder dedupKey(String dedupKey) {
            this.dedupKey = dedupKey;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder backoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
            return this;
        }

        public Builder maxDurationMs(long maxDurationMs) {
            this.maxDurationMs = maxDurationMs;
            return this;
        }

        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public Builder fileFormat(String fileFormat) {
            this.fileFormat = fileFormat;
            return this;
        }

        public Builder fileDelimiter(String fileDelimiter) {
            this.fileDelimiter = fileDelimiter;
            return this;
        }

        public ImportJobRequest build() {
            return new ImportJobRequest(inputFile, batchDate, runMode, rerunId, dedupKey, priority, maxRetries, backoffMs, maxDurationMs, timeoutMs, fileFormat, fileDelimiter);
        }
    }
}
