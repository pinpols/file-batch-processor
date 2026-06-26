package com.example.filebatchprocessor.batch.config;

import com.example.filebatchprocessor.batch.processor.ExportRecordProcessor;
import com.example.filebatchprocessor.batch.writer.ExportRecordTraceWriter;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.ExportRecord;
import com.example.filebatchprocessor.params.ExportJobParams;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.infrastructure.item.file.transform.FieldExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DataExportJobConfig {

    private static final String DEFAULT_EXPORT_SQL =
            "select id, business_key, name, description, batch_date from imported_records";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Autowired
    public DataExportJobConfig(
            JobRepository jobRepository, PlatformTransactionManager transactionManager, DataSource dataSource) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<ExportRecord> exportReader(
            @Value("#{jobParameters}") Map<String, Object> jobParameters,
            @Value("${batch.export.fetch-size:500}") int fetchSize) {

        ExportJobParams params = ExportJobParams.from(jobParameters);
        String sql = resolveSafeExportSql(params.getExportSql());
        // setFetchSize 让 PostgreSQL 走服务端游标按批拉取,避免默认把整个结果集读进内存导致大导出 OOM
        return new JdbcCursorItemReaderBuilder<ExportRecord>()
                .name("exportReader")
                .dataSource(dataSource)
                .sql(sql)
                .rowMapper(DataExportJobConfig::mapRow)
                .fetchSize(fetchSize)
                .build();
    }

    private String resolveSafeExportSql(String exportSql) {
        if (exportSql == null || exportSql.trim().isEmpty()) {
            return DEFAULT_EXPORT_SQL;
        }

        String normalized = exportSql.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        boolean startsWithSelect = lower.startsWith("select ");
        boolean singleStatement = !lower.contains(";");
        boolean noDmlKeywords = !(lower.contains(" insert ")
                || lower.contains(" update ")
                || lower.contains(" delete ")
                || lower.contains(" drop ")
                || lower.contains(" alter ")
                || lower.contains(" truncate "));

        if (startsWithSelect && singleStatement && noDmlKeywords) {
            return normalized;
        }

        throw new IllegalArgumentException("Unsupported export.sql: only a single SELECT statement is allowed");
    }

    private static ExportRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        ExportRecord r = new ExportRecord();
        r.setId(rs.getLong("id"));
        r.setBusinessKey(rs.getString("business_key"));
        r.setName(rs.getString("name"));
        r.setDescription(rs.getString("description"));
        r.setBatchDate(rs.getString("batch_date"));
        return r;
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<ExportRecord> exportWriter(@Value("#{jobParameters}") Map<String, Object> jobParameters) {
        ExportJobParams params = ExportJobParams.from(jobParameters);
        String fileName = (params.getOutputFileName() == null
                        || params.getOutputFileName().isEmpty())
                ? "export/output.csv"
                : params.getOutputFileName();

        FieldExtractor<ExportRecord> fieldExtractor = item -> new Object[] {
            item.getId(), item.getBusinessKey(), item.getName(), item.getDescription(), item.getBatchDate()
        };

        DelimitedLineAggregator<ExportRecord> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setDelimiter(",");
        lineAggregator.setFieldExtractor(fieldExtractor);

        return new FlatFileItemWriterBuilder<ExportRecord>()
                .name("exportFileWriter")
                .resource(new FileSystemResource(fileName))
                .lineAggregator(lineAggregator)
                .headerCallback(writer -> writer.write("id,business_key,name,description,batch_date"))
                .build();
    }

    @Bean
    @StepScope
    public ExportRecordTraceWriter exportTraceWriter(
            FlatFileItemWriter<ExportRecord> exportWriter, RecordTraceRepository recordTraceRepository) {
        return new ExportRecordTraceWriter(exportWriter, recordTraceRepository, "dataExportJob");
    }

    @Bean
    public Step exportStep(
            JdbcCursorItemReader<ExportRecord> exportReader,
            ExportRecordProcessor processor,
            ExportRecordTraceWriter exportTraceWriter) {
        return new StepBuilder("exportStep", jobRepository)
                .<ExportRecord, ExportRecord>chunk(200)
                .reader(exportReader)
                .processor(processor)
                .writer(exportTraceWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job dataExportJob(JobCompletionNotificationListener listener, @Qualifier("exportStep") Step exportStep) {
        return new JobBuilder("dataExportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(exportStep)
                .build();
    }
}
