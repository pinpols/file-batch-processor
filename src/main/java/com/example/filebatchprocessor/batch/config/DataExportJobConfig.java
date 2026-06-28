package com.example.filebatchprocessor.batch.config;

import com.example.filebatchprocessor.batch.BatchJobNames;
import com.example.filebatchprocessor.batch.processor.ExportRecordProcessor;
import com.example.filebatchprocessor.batch.writer.ExportRecordTraceWriter;
import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.ExportRecord;
import com.example.filebatchprocessor.params.ExportJobParams;
import com.example.filebatchprocessor.repository.RecordTraceRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private static final String DEFAULT_ALLOWED_TABLES = "imported_records,imported_records_partition";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Value("${batch.io.output-base-dir:}")
    private String outputBaseDir;

    @Value("${batch.export.allowed-tables:" + DEFAULT_ALLOWED_TABLES + "}")
    private String allowedTablesCsv = DEFAULT_ALLOWED_TABLES;

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

    // DML/DDL 关键字(词边界匹配,避免 " insert " 这种带空格的形态被 insert( 等绕过)
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|merge|call|do|copy|"
                    + "vacuum|analyze|comment|reindex|cluster|lock|listen|notify|set|reset|begin|commit|rollback)\\b",
            Pattern.CASE_INSENSITIVE);

    // 危险函数 / 系统对象:文件读写(pg_read_file/pg_ls_dir/lo_import...)、外部连接(dblink)、
    // 阻塞(pg_sleep)、配置读写(current_setting/set_config)、系统目录(pg_catalog/information_schema)。
    private static final Pattern FORBIDDEN_FUNCTIONS = Pattern.compile(
            "\\b(pg_read_file|pg_read_binary_file|pg_ls_dir|pg_stat_file|pg_logdir_ls|lo_import|lo_export|"
                    + "lo_get|lo_put|dblink|dblink_exec|pg_sleep|pg_terminate_backend|pg_cancel_backend|"
                    + "current_setting|set_config|pg_read_server_files|pg_execute_server_program|"
                    + "pg_catalog|information_schema|pg_authid|pg_shadow|pg_user|pg_roles)\\b",
            Pattern.CASE_INSENSITIVE);

    private String resolveSafeExportSql(String exportSql) {
        if (exportSql == null || exportSql.trim().isEmpty()) {
            return DEFAULT_EXPORT_SQL;
        }

        String normalized = exportSql.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);

        boolean startsWithSelect = lower.startsWith("select ") || lower.startsWith("with ");
        boolean singleStatement = normalized.indexOf(';') < 0;
        boolean noForbiddenKeyword = !FORBIDDEN_KEYWORDS.matcher(normalized).find();
        boolean noForbiddenFunction = !FORBIDDEN_FUNCTIONS.matcher(normalized).find();
        // 禁止注释(常用于绕过 token 检测:-- 与 /* */)
        boolean noComment = !lower.contains("--") && !normalized.contains("/*");

        if (startsWithSelect
                && singleStatement
                && noForbiddenKeyword
                && noForbiddenFunction
                && noComment
                && tablesAllowed(normalized)) {
            return normalized;
        }

        throw new IllegalArgumentException(
                "Unsupported export.sql: only a single read-only SELECT (no DML/DDL, no system functions, "
                        + "no comments/semicolons, and only allow-listed tables) is allowed");
    }

    private boolean tablesAllowed(String sql) {
        Set<String> allowed = Arrays.stream(allowedTablesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allowed.isEmpty()) {
            return false;
        }

        Set<String> cteAliases = extractCteAliases(sql);
        Matcher matcher = Pattern.compile(
                        "\\b(?:from|join)\\s+([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)?)\\b",
                        Pattern.CASE_INSENSITIVE)
                .matcher(sql);
        boolean sawTable = false;
        while (matcher.find()) {
            sawTable = true;
            String table = matcher.group(1).toLowerCase(Locale.ROOT);
            int dot = table.lastIndexOf('.');
            String simple = dot >= 0 ? table.substring(dot + 1) : table;
            if (cteAliases.contains(simple)) {
                continue;
            }
            if (!allowed.contains(simple) && !allowed.contains(table)) {
                return false;
            }
        }
        return sawTable;
    }

    private Set<String> extractCteAliases(String sql) {
        Set<String> aliases = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile(
                        "(?:\\bwith|,)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+as\\s*\\(", Pattern.CASE_INSENSITIVE)
                .matcher(sql);
        while (matcher.find()) {
            aliases.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return aliases;
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
        // 路径穿越防护:限定在 batch.io.output-base-dir 之内(未配置则至少拒绝 .. 逃逸)
        fileName = com.example.filebatchprocessor.util.PathSafety.confine(outputBaseDir, fileName);

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
        return new ExportRecordTraceWriter(exportWriter, recordTraceRepository, BatchJobNames.DATA_EXPORT_JOB);
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
        return new JobBuilder(BatchJobNames.DATA_EXPORT_JOB, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(exportStep)
                .build();
    }
}
