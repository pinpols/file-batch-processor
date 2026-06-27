package com.example.filebatchprocessor.batch.reader.spi;

/** 文档解析选项(Excel sheet 选择等)。单租户内部够用。 */
public record DocumentReadOptions(Integer sheetIndex, String sheetName) {}
