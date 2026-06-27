package com.example.filebatchprocessor.batch.reader.spi;

/** 文档级 reader 的 provider SPI。新增格式 = 新增一个 @Component provider。 */
public interface DocumentRecordReaderProvider {

    boolean supports(String format);

    DocumentRecordReader create(DocumentReadOptions options);
}
