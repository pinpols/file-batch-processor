package com.example.filebatchprocessor.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JsonManifestParser implements ManifestParser {

    private final ObjectMapper objectMapper;

    public JsonManifestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ParsedManifest parse(String content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            String manifestId = text(root, "manifestId");
            if (manifestId == null || manifestId.isBlank()) {
                throw new IllegalArgumentException("manifest missing manifestId");
            }
            JsonNode filesNode = root.get("files");
            if (filesNode == null || !filesNode.isArray() || filesNode.isEmpty()) {
                throw new IllegalArgumentException("manifest has no files");
            }
            List<ParsedManifest.ExpectedFile> files = new ArrayList<>();
            for (JsonNode f : filesNode) {
                String fileName = text(f, "fileName");
                if (fileName == null || fileName.isBlank()) {
                    throw new IllegalArgumentException("manifest file missing fileName");
                }
                Long count = f.hasNonNull("expectedRecordCount")
                        ? f.get("expectedRecordCount").asLong()
                        : null;
                String checksum = text(f, "checksum");
                String algo = f.hasNonNull("checksumAlgorithm")
                        ? f.get("checksumAlgorithm").asText()
                        : "MD5";
                if (!"MD5".equalsIgnoreCase(algo)) {
                    throw new IllegalArgumentException("only MD5 checksum supported, got: " + algo);
                }
                boolean required =
                        !f.hasNonNull("required") || f.get("required").asBoolean();
                files.add(new ParsedManifest.ExpectedFile(fileName, count, checksum, "MD5", required));
            }
            return new ParsedManifest(manifestId, text(root, "sourceSystem"), text(root, "bizDate"), files);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid manifest JSON", e);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
