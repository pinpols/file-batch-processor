package com.example.filebatchprocessor.manifest;

public interface ManifestParser {
    ParsedManifest parse(String content);
}
