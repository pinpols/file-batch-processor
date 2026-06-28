package com.example.filebatchprocessor.batch.reader.spi;

/** 行解析器提供方 SPI，由平台按文件格式选择具体解析器。 */
public interface RecordLineParserProvider {

    /** 判断当前提供方是否支持指定格式，例如 CSV 或 FIXED。 */
    boolean supports(String format);

    /** 按分隔符创建解析器；定长等格式可以忽略该参数。 */
    RecordLineParser create(String delimiter);
}
