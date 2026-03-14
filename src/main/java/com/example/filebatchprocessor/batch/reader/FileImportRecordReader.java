package com.example.filebatchprocessor.batch.reader;

import com.example.filebatchprocessor.batch.reader.spi.CsvRecordLineParser;
import com.example.filebatchprocessor.batch.reader.spi.FixedRecordLineParser;
import com.example.filebatchprocessor.batch.reader.spi.RecordLineParser;
import com.example.filebatchprocessor.batch.reader.spi.RecordLineParserFactory;
import com.example.filebatchprocessor.model.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

/**
 * 导入文件读取器，支持自定义分隔符的 CSV 和简单定长格式，并支持任务分片。
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

    public FileImportRecordReader(Resource resource) {
        this(resource, 0, 1, "CSV", ",", null);
    }

    public FileImportRecordReader(Resource resource, Integer shardIndex, Integer shardTotal,
                                  String format, String delimiter) {
        this(resource, shardIndex, shardTotal, format, delimiter, null);
    }

    public FileImportRecordReader(Resource resource, Integer shardIndex, Integer shardTotal,
                                  String format, String delimiter,
                                  RecordLineParserFactory parserFactory) {
        this.resource = resource;
        this.shardIndex = shardIndex == null ? 0 : shardIndex;
        int total = shardTotal == null ? 1 : shardTotal;
        this.shardTotal = total <= 0 ? 1 : total;
        this.shardingEnabled = this.shardTotal > 1;

        if (parserFactory != null) {
            this.lineParser = parserFactory.create(format, delimiter);
        } else {
            // Backward-compatible fallback (used in tests/legacy wiring)
            String resolvedFormat = (format == null || format.isEmpty()) ? "CSV" : format.toUpperCase();
            if ("FIXED".equals(resolvedFormat)) {
                this.lineParser = new FixedRecordLineParser();
            } else {
                this.lineParser = new CsvRecordLineParser(delimiter);
            }
        }
    }

    @Override
    public FileRecord read() throws Exception {
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
                log.error("Error parsing line {}: {}", lineCount, line, e);
                parseErrorCount++;
            }
        }
    }

    private void initializeReader() throws IOException {
        inputStream = resource.getInputStream();
        reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        lineCount = 0;
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
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
        executionContext.putInt("line.count", lineCount);
        executionContext.putLong("read.count", readCount);
        executionContext.putLong("checksum", checksum.getValue());
        executionContext.putLong("parse.error.count", parseErrorCount);
    }

    @Override
    public void close() throws ItemStreamException {
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
}
