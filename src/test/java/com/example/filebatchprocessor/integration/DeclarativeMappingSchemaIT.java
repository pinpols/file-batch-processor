package com.example.filebatchprocessor.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.mapping.TransformOp;
import com.example.filebatchprocessor.model.FeedDefinition;
import com.example.filebatchprocessor.model.FieldMapping;
import com.example.filebatchprocessor.repository.FeedDefinitionRepository;
import com.example.filebatchprocessor.repository.FieldMappingRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证 FeedDefinition / FieldMapping 实体与 V1_39 列对齐:Flyway 跑 V1_39 建表后,
 * JPA 写入并按 enabled + orderNo 读回,枚举 transformOp 正确往返。
 */
@SpringBootTest
@ActiveProfiles("test")
class DeclarativeMappingSchemaIT extends PostgresContainerSupport {

    @Autowired
    private FeedDefinitionRepository feedDefinitionRepository;

    @Autowired
    private FieldMappingRepository fieldMappingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void persistsAndReadsBackFeedWithMappings() {
        String feedId = "test-feed-" + UUID.randomUUID();

        FeedDefinition feed = new FeedDefinition();
        feed.setFeedId(feedId);
        feed.setFeedName("Test Feed");
        feed.setFormat("CSV");
        feed.setDelimiter(",");
        feed.setHasHeader(true);
        feed.setTargetTable("imported_records_partition");
        feed.setBusinessKeyFields("acct_no,trade_date");
        feed.setEnabled(true);
        feed.setCreatedAt(LocalDateTime.now());
        feed.setUpdatedAt(LocalDateTime.now());
        feedDefinitionRepository.save(feed);

        FieldMapping m0 = new FieldMapping();
        m0.setFeedId(feedId);
        m0.setSourceColumn("ACCT");
        m0.setTargetField("acct_no");
        m0.setTransformOp(TransformOp.TRIM);
        m0.setRequired(true);
        m0.setOrderNo(0);
        m0.setEnabled(true);
        fieldMappingRepository.save(m0);

        FieldMapping m1 = new FieldMapping();
        m1.setFeedId(feedId);
        m1.setSourceColumn("DT");
        m1.setTargetField("trade_date");
        m1.setTransformOp(TransformOp.DATE_FORMAT);
        m1.setTransformArg("yyyyMMdd");
        m1.setRequired(false);
        m1.setOrderNo(1);
        m1.setEnabled(true);
        fieldMappingRepository.save(m1);

        entityManager.flush();
        entityManager.clear();

        Optional<FeedDefinition> foundFeed = feedDefinitionRepository.findByFeedIdAndEnabledTrue(feedId);
        assertTrue(foundFeed.isPresent(), "enabled feed should be found");
        assertEquals("Test Feed", foundFeed.get().getFeedName());
        assertEquals("acct_no,trade_date", foundFeed.get().getBusinessKeyFields());

        List<FieldMapping> mappings =
                fieldMappingRepository.findByFeedIdAndEnabledTrueOrderByOrderNoAsc(feedId);
        assertEquals(2, mappings.size(), "two enabled mappings expected");
        assertEquals(0, mappings.get(0).getOrderNo());
        assertEquals(TransformOp.TRIM, mappings.get(0).getTransformOp());
        assertEquals(1, mappings.get(1).getOrderNo());
        assertEquals(TransformOp.DATE_FORMAT, mappings.get(1).getTransformOp());
        assertEquals("yyyyMMdd", mappings.get(1).getTransformArg());
    }
}
