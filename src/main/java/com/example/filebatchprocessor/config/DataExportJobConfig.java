package com.example.filebatchprocessor.config;

import com.example.filebatchprocessor.listener.JobCompletionNotificationListener;
import com.example.filebatchprocessor.model.ExportRecord;

import org.springframework.batch.core.configuration.annotation.StepScope;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.infrastructure.item.file.transform.FieldExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;

@Configuration
public class DataExportJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;

    @Autowired
    public DataExportJobConfig(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              DataSource dataSource) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.dataSource = dataSource;
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<ExportRecord> exportReader(
            @Value("#{jobParameters['export.sql']}") String exportSql) {


        String sql = exportSql != null && !exportSql.trim().isEmpty()
                ? exportSql
                : "select id, business_key, name, description, batch_date from imported_records";

        return new JdbcCursorItemReader<>(dataSource, sql, DataExportJobConfig::mapRow);
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
    public FlatFileItemWriter<ExportRecord> exportWriter(
            @Value("#{jobParameters['output.file.name']}") String outputFileName) {
        String fileName = (outputFileName == null || outputFileName.isEmpty())
                ? "export/output.csv"
                : outputFileName;

        FieldExtractor<ExportRecord> fieldExtractor = item -> new Object[]{
                item.getId(),
                item.getBusinessKey(),
                item.getName(),
                item.getDescription(),
                item.getBatchDate()
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
    public Step exportStep(JdbcCursorItemReader<ExportRecord> exportReader,
                           FlatFileItemWriter<ExportRecord> exportWriter) {
        return new StepBuilder("exportStep", jobRepository)
                .<ExportRecord, ExportRecord>chunk(200)
                .reader(exportReader)
                .writer(exportWriter)
                .transactionManager(transactionManager)
                .build();
    }

    @Bean
    public Job dataExportJob(JobCompletionNotificationListener listener, Step exportStep) {
        return new JobBuilder("dataExportJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(listener)
                .start(exportStep)
                .build();
    }
}


