package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.filebatchprocessor.mapping.TransformOp;
import com.example.filebatchprocessor.model.FeedDefinition;
import com.example.filebatchprocessor.model.FieldMapping;
import com.example.filebatchprocessor.model.ImportedRecordPartitioned;
import com.example.filebatchprocessor.repository.FeedDefinitionRepository;
import com.example.filebatchprocessor.repository.FieldMappingRepository;
import com.example.filebatchprocessor.repository.ImportedRecordPartitionedRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 守门回归 IT:证明声明式映射接线(feedId 路由)未漂移默认导入语义。
 *
 * <p>测试 A:字节级对照 —— 默认路径 vs 对照 feed(default-csv,复刻默认语义)在
 * (name, description, business_key 去 batchDate) 上逐行一致。
 *
 * <p>测试 B:真映射 + attributes JSONB 落库与回读。
 */
@SpringBootTest
@ActiveProfiles("test")
class DeclarativeMappingWiringIT extends PostgresContainerSupport {

    private static final String ATTRS_FEED_ID = "it-attrs-feed";

    @Autowired
    @Qualifier("asyncJobLauncher")
    private JobLauncher jobLauncher;

    @Autowired
    @Qualifier("fileImportJob")
    private Job fileImportJob;

    @Autowired
    private ImportedRecordPartitionedRepository partitionedRepository;

    @Autowired
    private FeedDefinitionRepository feedDefinitionRepository;

    @Autowired
    private FieldMappingRepository fieldMappingRepository;

    @AfterEach
    void cleanupTestFeed() {
        fieldMappingRepository.deleteAll(fieldMappingRepository.findByFeedIdAndEnabledTrueOrderByOrderNoAsc(
                ATTRS_FEED_ID));
        feedDefinitionRepository.findById(ATTRS_FEED_ID).ifPresent(feedDefinitionRepository::delete);
    }

    private JobExecution runImport(Path csv, String batchDate, String feedId) throws Exception {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .addString("input.file.name", csv.toString())
                .addString("batchDate", batchDate)
                .addString("file.format", "CSV")
                .addString("file.delimiter", ",");
        if (feedId != null) {
            builder.addString("feedId", feedId);
        }
        return jobLauncher.run(fileImportJob, builder.toJobParameters());
    }

    /** business_key 去掉结尾 {@code ":"+batchDate}(对照口径)。 */
    private static String stripDate(String businessKey, String batchDate) {
        String suffix = ":" + batchDate;
        return businessKey.endsWith(suffix)
                ? businessKey.substring(0, businessKey.length() - suffix.length())
                : businessKey;
    }

    private List<List<String>> collectTriples(String batchDate) {
        List<List<String>> rows = new ArrayList<>();
        for (ImportedRecordPartitioned r : partitionedRepository.findByBatchDate(batchDate)) {
            rows.add(List.of(
                    String.valueOf(r.getName()),
                    String.valueOf(r.getDescription()),
                    stripDate(r.getBusinessKey(), batchDate)));
        }
        rows.sort(Comparator.comparing(Object::toString));
        return rows;
    }

    @Test
    void defaultPathAndParityFeedProduceByteIdenticalRows() throws Exception {
        String csvBody = "id,name,description\n" + "1,alice,first\n" + "2,bob,second\n" + "3,carol,third\n";
        Path csv = Files.createTempFile("decl-parity", ".csv");
        Files.writeString(csv, csvBody, StandardCharsets.UTF_8);

        String defaultDate = "2026-07-01";
        String feedDate = "2026-07-02";

        // 默认路径(无 feedId)
        JobExecution defaultRun = runImport(csv, defaultDate, null);
        assertEquals(BatchStatus.COMPLETED, defaultRun.getStatus());

        // feed 路径(对照 feed,复刻默认语义)
        JobExecution feedRun = runImport(csv, feedDate, "default-csv");
        assertEquals(BatchStatus.COMPLETED, feedRun.getStatus());

        List<List<String>> setA = collectTriples(defaultDate);
        List<List<String>> setB = collectTriples(feedDate);

        // 守门:默认 vs feed 在 (name, description, business_key 去日期) 上逐行一致
        assertEquals(setA, setB, "feed 路径必须字节级复刻默认导入语义(business_key 口径未漂)");

        // 附加断言:行数相等且 = 3,name 已大写
        assertEquals(3, setA.size(), "默认路径应导入 3 行");
        assertEquals(3, setB.size(), "feed 路径应导入 3 行");
        assertTrue(
                setA.stream().anyMatch(t -> t.get(0).equals("ALICE")),
                "name 应已转大写(默认路径包含 ALICE)");
        assertTrue(
                setB.stream().anyMatch(t -> t.get(0).equals("ALICE")),
                "name 应已转大写(feed 路径包含 ALICE)");
    }

    @Test
    void realMappingWritesAttributesJsonb() throws Exception {
        // 1. 自包含插入测试 feed:源两列 c_name,c_cat → name(UPPER) + category(进 attributes)
        FeedDefinition feed = new FeedDefinition();
        feed.setFeedId(ATTRS_FEED_ID);
        feed.setFeedName("IT attrs feed");
        feed.setFormat("CSV");
        feed.setDelimiter(",");
        feed.setHasHeader(true);
        feed.setTargetTable("imported_records_partition");
        feed.setBusinessKeyFields(null);
        feed.setEnabled(true);
        feed.setCreatedAt(java.time.LocalDateTime.now());
        feed.setUpdatedAt(java.time.LocalDateTime.now());
        feedDefinitionRepository.save(feed);

        FieldMapping nameMapping = new FieldMapping();
        nameMapping.setFeedId(ATTRS_FEED_ID);
        nameMapping.setSourceColumn("c_name");
        nameMapping.setTargetField("name");
        nameMapping.setTransformOp(TransformOp.UPPER);
        nameMapping.setRequired(true);
        nameMapping.setOrderNo(1);
        nameMapping.setEnabled(true);
        fieldMappingRepository.save(nameMapping);

        FieldMapping catMapping = new FieldMapping();
        catMapping.setFeedId(ATTRS_FEED_ID);
        catMapping.setSourceColumn("c_cat");
        catMapping.setTargetField("category");
        catMapping.setTransformOp(TransformOp.NONE);
        catMapping.setRequired(false);
        catMapping.setOrderNo(2);
        catMapping.setEnabled(true);
        fieldMappingRepository.save(catMapping);

        // 2. CSV:表头 c_name,c_cat
        Path csv = Files.createTempFile("decl-attrs", ".csv");
        Files.writeString(csv, "c_name,c_cat\n" + "alice,food\n" + "bob,drink\n", StandardCharsets.UTF_8);

        // 3. run import
        String batchDate = "2026-07-03";
        JobExecution exec = runImport(csv, batchDate, ATTRS_FEED_ID);
        assertEquals(BatchStatus.COMPLETED, exec.getStatus());

        // 4. 断言
        List<ImportedRecordPartitioned> rows = partitionedRepository.findByBatchDate(batchDate);
        rows.sort(Comparator.comparing(ImportedRecordPartitioned::getName));
        assertEquals(2, rows.size(), "应导入 2 行");

        ImportedRecordPartitioned alice = rows.get(0);
        ImportedRecordPartitioned bob = rows.get(1);

        assertEquals("ALICE", alice.getName());
        assertEquals("BOB", bob.getName());

        // attributes JSONB 正确落库与回读
        assertNotNull(alice.getAttributes(), "ALICE attributes 不应为空");
        assertNotNull(bob.getAttributes(), "BOB attributes 不应为空");
        assertEquals("food", alice.getAttributes().get("category"));
        assertEquals("drink", bob.getAttributes().get("category"));

        // business_key:business_key_fields=null 退回 name 口径(name 已大写)
        assertEquals("ALICE:" + batchDate, alice.getBusinessKey());
        assertEquals("BOB:" + batchDate, bob.getBusinessKey());
    }
}
