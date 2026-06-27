package com.example.filebatchprocessor.manifest;

import java.util.List;

public record ParsedManifest(String manifestId, String sourceSystem, String bizDate, List<ExpectedFile> files) {
    public record ExpectedFile(
            String fileName, Long expectedRecordCount, String checksum, String checksumAlgorithm, boolean required) {}
}
