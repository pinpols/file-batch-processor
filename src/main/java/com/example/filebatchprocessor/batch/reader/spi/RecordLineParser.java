package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;

/**
 * 单行记录解析 SPI，按不同文本格式实现。
 */
public interface RecordLineParser {

    /**
     * 将原始文本行解析为 FileRecord。
     */
    FileRecord parse(String line) throws Exception;
}

