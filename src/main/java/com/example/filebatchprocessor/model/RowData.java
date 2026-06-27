package com.example.filebatchprocessor.model;

import java.util.Map;

/** 通用行:列名→值 + 行号。供将来 config-driven 导入链路使用。 */
public record RowData(Map<String, Object> values, long lineNo) {}
