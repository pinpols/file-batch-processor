package com.example.filebatchprocessor.batch.handler.support;

import com.example.filebatchprocessor.util.IdempotencyKeyBuilder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ImportJobRequestResolver {

    private static final DateTimeFormatter BATCH_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    public ImportJobRequest resolve(String rawParam, String defaultInputFile, int shardIndex, int shardTotal) {
        Map<String, String> params = parseParams(rawParam);
        String inputParam = params.getOrDefault("input", defaultInputFile);
        if (inputParam == null || inputParam.isBlank()) {
            throw new IllegalArgumentException("input.file.name/input is required");
        }
        String batchDate = normalizeBatchDate(params.get("batchDate"));
        String runMode = params.getOrDefault("runMode", "normal");
        String rerunId = params.getOrDefault("rerunId", "");
        String dedupKey = params.getOrDefault(
                "dedupKey",
                IdempotencyKeyBuilder.forImportRequest(inputParam, batchDate, rerunId, shardIndex, shardTotal));

        ImportJobRequest request = ImportJobRequest.builder()
                .inputFile(inputParam)
                .batchDate(batchDate)
                .runMode(runMode)
                .rerunId(rerunId)
                .dedupKey(dedupKey)
                .priority(Integer.parseInt(params.getOrDefault("priority", "0")))
                .maxRetries(Integer.parseInt(params.getOrDefault("maxRetries", "0")))
                .backoffMs(Long.parseLong(params.getOrDefault("backoffMs", "1000")))
                .maxDurationMs(Long.parseLong(params.getOrDefault("maxDurationMs", "0")))
                .timeoutMs(Long.parseLong(params.getOrDefault("timeoutMs", "0")))
                .fileFormat(params.getOrDefault("file.format", "CSV"))
                .fileDelimiter(params.getOrDefault("file.delimiter", ","))
                .build();

        validateInputFile(request.getInputFile());
        return request;
    }

    public Map<String, String> parseParams(String param) {
        Map<String, String> params = new HashMap<>();
        if (param == null || param.trim().isEmpty()) {
            return params;
        }

        String[] pairs = param.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private String normalizeBatchDate(String batchDate) {
        if (batchDate == null || batchDate.isBlank()) {
            return LocalDate.now().format(BATCH_DATE_FORMATTER);
        }
        return batchDate;
    }

    private void validateInputFile(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("Input file not found: " + filePath);
            }
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Input path is not a regular file: " + filePath);
            }
            if (Files.size(path) == 0L) {
                throw new IllegalArgumentException("Input file is empty: " + filePath);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Input file validation failed: " + e.getMessage(), e);
        }
    }
}
