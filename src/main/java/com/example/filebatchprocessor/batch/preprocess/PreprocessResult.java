package com.example.filebatchprocessor.batch.preprocess;

import java.nio.file.Path;

/** 预处理结果:明文文件路径 + 需清理的临时文件(透传时为 null)。 */
public record PreprocessResult(Path plaintextPath, Path tempFileOrNull) {}
