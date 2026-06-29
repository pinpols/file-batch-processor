package com.example.filebatchprocessor.batch.reader;

import com.example.filebatchprocessor.batch.reader.spi.CsvRecordLineParser;
import com.example.filebatchprocessor.batch.reader.spi.DocumentReadOptions;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReader;
import com.example.filebatchprocessor.batch.reader.spi.DocumentRecordReaderFactory;
import com.example.filebatchprocessor.batch.reader.spi.FixedRecordLineParser;
import com.example.filebatchprocessor.batch.reader.spi.RecordLineParser;
import com.example.filebatchprocessor.batch.reader.spi.RecordLineParserFactory;
import com.example.filebatchprocessor.model.FileRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.Adler32;
import java.util.zip.Checksum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.core.io.Resource;

/**
 * 导入文件读取器，支持 CSV、定长、文档类输入和任务分片。
 */
public class FileImportRecordReader implements ItemStreamReader<FileRecord>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(FileImportRecordReader.class);

    private final Resource resource;
    private BufferedReader reader;
    private InputStream inputStream;
    private int lineCount = 0;
    private final int shardIndex;
    private final int shardTotal;
    private final boolean shardingEnabled;
    private long readCount = 0L;
    private long parseErrorCount = 0L;
    private final Checksum checksum = new Adler32();
    // 文件格式解析 SPI
    private final RecordLineParser lineParser;
    // 文档模式 reader(非 null 时走文档分支,行模式字段不参与)
    private final DocumentRecordReader documentReader;
    private Iterator<FileRecord> documentIterator;
    private long recordSeq = 0;
    // feed 模式:非 null 即开启(空 list=用文件首行探测列名,非空=用注入列名并跳过文件首行)
    private final List<String> feedHeaderColumns;
    // feed 模式自用的分隔符(默认模式不读取)
    private final String feedDelimiter;
    // feed 模式解析后缓存的表头列名
    private List<String> resolvedHeader;

    public FileImportRecordReader(Resource resource) {
        this(resource, 0, 1, "CSV", ",", null);
    }

    public FileImportRecordReader(
            Resource resource, Integer shardIndex, Integer shardTotal, String format, String delimiter) {
        this(resource, shardIndex, shardTotal, format, delimiter, null);
    }

    public FileImportRecordReader(
            Resource resource,
            Integer shardIndex,
            Integer shardTotal,
            String format,
            String delimiter,
            RecordLineParserFactory parserFactory) {
        this(resource, shardIndex, shardTotal, format, delimiter, parserFactory, null, null);
    }

    public FileImportRecordReader(
            Resource resource,
            Integer shardIndex,
            Integer shardTotal,
            String format,
            String delimiter,
            RecordLineParserFactory parserFactory,
            DocumentRecordReaderFactory documentReaderFactory,
            DocumentReadOptions documentReadOptions) {
        this(
                resource,
                shardIndex,
                shardTotal,
                format,
                delimiter,
                parserFactory,
                documentReaderFactory,
                documentReadOptions,
                null);
    }

    public FileImportRecordReader(
            Resource resource,
            Integer shardIndex,
            Integer shardTotal,
            String format,
            String delimiter,
            RecordLineParserFactory parserFactory,
            DocumentRecordReaderFactory documentReaderFactory,
            DocumentReadOptions documentReadOptions,
            List<String> feedHeaderColumns) {
        this.resource = resource;
        this.feedHeaderColumns = feedHeaderColumns;
        this.feedDelimiter = (delimiter == null || delimiter.isEmpty()) ? "," : delimiter;
        this.shardIndex = shardIndex == null ? 0 : shardIndex;
        int total = shardTotal == null ? 1 : shardTotal;
        this.shardTotal = total <= 0 ? 1 : total;
        this.shardingEnabled = this.shardTotal > 1;

        if (documentReaderFactory != null && documentReaderFactory.supportsDocument(format)) {
            // 文档模式:整文档解析为 FileRecord 流,行模式 lineParser 不参与
            this.documentReader = documentReaderFactory.create(format, documentReadOptions);
            this.lineParser = null;
        } else {
            this.documentReader = null;
            if (parserFactory != null) {
                this.lineParser = parserFactory.create(format, delimiter);
            } else {
                // 兼容旧测试和未注册 SPI 的本地构造路径。
                String resolvedFormat = (format == null || format.isEmpty()) ? "CSV" : format.toUpperCase();
                if ("FIXED".equals(resolvedFormat)) {
                    this.lineParser = new FixedRecordLineParser();
                } else {
                    this.lineParser = new CsvRecordLineParser(delimiter);
                }
            }
        }
    }

    @Override
    public FileRecord read() throws Exception {
        if (documentReader != null) {
            while (documentIterator.hasNext()) {
                FileRecord rec = documentIterator.next();
                recordSeq++;
                if (shardingEnabled && ((recordSeq - 1) % shardTotal) != shardIndex) {
                    continue;
                }
                rec.setLineNo(recordSeq);
                checksum.update(documentChecksumBytes(rec));
                return rec;
            }
            return null;
        }

        if (feedHeaderColumns != null) {
            return readFeedLine();
        }

        if (reader == null) {
            initializeReader();
        }

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }

            lineCount++;

            // 跳过首行表头
            if (lineCount == 1) {
                continue;
            }

            if (shardingEnabled) {
                // lineCount-1 以忽略表头行
                if (((lineCount - 1) % shardTotal) != shardIndex) {
                    continue;
                }
            }

            try {
                FileRecord record = lineParser.parse(line);
                record.setLineNo((long) lineCount);
                checksum.update(line.getBytes(StandardCharsets.UTF_8));
                readCount++;
                return record;
            } catch (Exception e) {
                // 解析失败跳过当前行，继续读下一行，避免错误地把 null 当作 EOF
                log.error(
                        "Error parsing line {} (length={}, fingerprint={})",
                        lineCount,
                        line.length(),
                        Integer.toHexString(line.hashCode()),
                        e);
                parseErrorCount++;
            }
        }
    }

    /** feed 模式:CSV 行 zip 成 rawValues,name/description/id 留空。 */
    private FileRecord readFeedLine() throws Exception {
        if (reader == null) {
            initializeReader();
        }
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            lineCount++;

            if (lineCount == 1) {
                if (feedHeaderColumns.isEmpty()) {
                    String[] cols = line.split(Pattern.quote(feedDelimiter), -1);
                    List<String> header = new ArrayList<>(cols.length);
                    for (String c : cols) {
                        header.add(c.trim());
                    }
                    resolvedHeader = header;
                } else {
                    resolvedHeader = feedHeaderColumns;
                }
                // 首行为文件表头,跳过(不产生数据)
                continue;
            }

            if (shardingEnabled && ((lineCount - 1) % shardTotal) != shardIndex) {
                continue;
            }

            String[] vals = line.split(Pattern.quote(feedDelimiter), -1);
            LinkedHashMap<String, Object> raw = new LinkedHashMap<>();
            for (int i = 0; i < resolvedHeader.size(); i++) {
                raw.put(resolvedHeader.get(i), i < vals.length ? vals[i] : null);
            }
            FileRecord rec = new FileRecord();
            rec.setRawValues(raw);
            rec.setLineNo((long) lineCount);
            checksum.update(line.getBytes(StandardCharsets.UTF_8));
            readCount++;
            return rec;
        }
    }

    private void initializeReader() throws IOException {
        inputStream = resource.getInputStream();
        reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        lineCount = 0;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        if (documentReader != null) {
            try {
                documentIterator = documentReader.open(resource);
                long skip = executionContext.containsKey("record.count") ? executionContext.getLong("record.count") : 0;
                for (long i = 0; i < skip && documentIterator.hasNext(); i++) {
                    documentIterator.next();
                    recordSeq++;
                }
            } catch (Exception e) {
                throw new ItemStreamException("Failed to open document reader", e);
            }
            return;
        }

        // 重启时恢复读取位置
        if (executionContext.containsKey("line.count")) {
            lineCount = executionContext.getInt("line.count");
            try {
                initializeReader();
                for (int i = 0; i < lineCount; i++) {
                    reader.readLine();
                }
            } catch (IOException e) {
                throw new ItemStreamException("Failed to initialize reader", e);
            }
        } else {
            try {
                initializeReader();
            } catch (IOException e) {
                throw new ItemStreamException("Failed to initialize reader", e);
            }
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        if (documentReader != null) {
            executionContext.putLong("record.count", recordSeq);
            executionContext.putLong("checksum", checksum.getValue());
            return;
        }

        executionContext.putInt("line.count", lineCount);
        executionContext.putLong("read.count", readCount);
        executionContext.putLong("checksum", checksum.getValue());
        executionContext.putLong("parse.error.count", parseErrorCount);
    }

    @Override
    public void close() throws ItemStreamException {
        if (documentReader != null) {
            try {
                documentReader.close();
            } catch (IOException e) {
                log.error("Error closing document reader", e);
            }
            return;
        }
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException e) {
            log.error("Error closing reader", e);
        }
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            log.error("Error closing input stream", e);
        }
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        readCount = 0L;
        parseErrorCount = 0L;
        checksum.reset();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putLong("read.count", readCount);
        stepExecution.getExecutionContext().putLong("checksum", checksum.getValue());
        stepExecution.getExecutionContext().putLong("parse.error.count", parseErrorCount);
        return stepExecution.getExitStatus();
    }

    // 具体解析逻辑已抽到 RecordLineParser SPI 实现中

    /** 文档模式校验和字节:用字段拼接代替行原文(文档模式无行原文)。 */
    private byte[] documentChecksumBytes(FileRecord r) {
        String id = r.getId() == null ? "" : String.valueOf(r.getId());
        String name = r.getName() == null ? "" : r.getName();
        String description = r.getDescription() == null ? "" : r.getDescription();
        return (id + "|" + name + "|" + description).getBytes(StandardCharsets.UTF_8);
    }
}
