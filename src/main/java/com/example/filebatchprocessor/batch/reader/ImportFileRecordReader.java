package com.example.filebatchprocessor.batch.reader;

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
import java.util.zip.Adler32;
import java.util.zip.Checksum;

/**
 * 导入文件读取器，支持自定义分隔符的 CSV 和简单定长格式，并支持 XXL 分片。
 */
public class ImportFileRecordReader implements ItemStreamReader<FileRecord>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ImportFileRecordReader.class);

    private final Resource resource;
    private BufferedReader reader;
    private int lineCount = 0;
    private final int shardIndex;
    private final int shardTotal;
    private final boolean shardingEnabled;
    private long readCount = 0L;
    private final Checksum checksum = new Adler32();
    // 文件格式：CSV / FIXED
    private final String format;
    // 文本分隔符，仅在 CSV 模式下生效
    private final String delimiter;

    public ImportFileRecordReader(Resource resource) {
        this(resource, 0, 1, "CSV", ",");
    }

    public ImportFileRecordReader(Resource resource, Integer shardIndex, Integer shardTotal,
                                  String format, String delimiter) {
        this.resource = resource;
        this.shardIndex = shardIndex == null ? 0 : shardIndex;
        int total = shardTotal == null ? 1 : shardTotal;
        this.shardTotal = total <= 0 ? 1 : total;
        this.shardingEnabled = this.shardTotal > 1;
        this.format = (format == null || format.isEmpty()) ? "CSV" : format.toUpperCase();
        this.delimiter = (delimiter == null || delimiter.isEmpty()) ? "," : delimiter;
    }

    @Override
    public FileRecord read() throws Exception {
        if (reader == null) {
            initializeReader();
        }

        String line;
        while (true) {
            line = reader.readLine();
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
            break;
        }

        // 解析一行记录
        FileRecord record;
        try {
            if ("FIXED".equals(format)) {
                record = parseFixed(line);
            } else {
                record = parseDelimited(line);
            }
        } catch (Exception e) {
            log.error("Error parsing line {}: {}", lineCount, line, e);
            return null; // 解析失败的行跳过
        }

        checksum.update(line.getBytes());
        readCount++;

        return record;
    }

    private void initializeReader() throws IOException {
        InputStream inputStream = resource.getInputStream();
        reader = new BufferedReader(new InputStreamReader(inputStream));
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
        }
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt("line.count", lineCount);
        executionContext.putLong("read.count", readCount);
        executionContext.putLong("checksum", checksum.getValue());
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
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        readCount = 0L;
        checksum.reset();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stepExecution.getExecutionContext().putLong("read.count", readCount);
        stepExecution.getExecutionContext().putLong("checksum", checksum.getValue());
        return stepExecution.getExitStatus();
    }

    private FileRecord parseDelimited(String line) {
        String[] fields = line.split(delimiter, -1);
        FileRecord record = new FileRecord();
        if (fields.length > 0 && !fields[0].trim().isEmpty()) {
            record.setId(Long.parseLong(fields[0].trim()));
        }
        if (fields.length > 1) {
            record.setName(fields[1].trim());
        }
        if (fields.length > 2) {
            record.setDescription(fields[2].trim());
        }
        return record;
    }

    // 简单定长示例，可按实际格式调整
    private FileRecord parseFixed(String line) {
        FileRecord record = new FileRecord();
        String idPart = safeSub(line, 0, 10).trim();
        if (!idPart.isEmpty()) {
            record.setId(Long.parseLong(idPart));
        }
        record.setName(safeSub(line, 10, 40).trim());
        record.setDescription(safeSub(line, 40, line.length()).trim());
        return record;
    }

    private String safeSub(String line, int start, int end) {
        if (line == null || start >= line.length()) {
            return "";
        }
        int realEnd = Math.min(end, line.length());
        return line.substring(start, realEnd);
    }
}


