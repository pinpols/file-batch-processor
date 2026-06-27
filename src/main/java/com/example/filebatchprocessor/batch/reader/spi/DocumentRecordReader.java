package com.example.filebatchprocessor.batch.reader.spi;

import com.example.filebatchprocessor.model.FileRecord;
import java.io.Closeable;
import java.util.Iterator;
import org.springframework.core.io.Resource;

/** 文档级记录读取:把整个资源解析成可迭代的 FileRecord 流(JSON 数组/Excel 工作簿)。 */
public interface DocumentRecordReader extends Closeable {

    /** 打开资源并返回惰性迭代器;实现内部持有底层流/解析器,close() 释放。 */
    Iterator<FileRecord> open(Resource resource) throws Exception;
}
