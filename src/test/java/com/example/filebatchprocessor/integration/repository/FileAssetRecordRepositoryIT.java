package com.example.filebatchprocessor.integration.repository;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.batch.job.enabled=false",
        "batch.alert.enabled=false",
        "orchestration.enabled=false"
})
class FileAssetRecordRepositoryIT extends PostgresContainerSupport {

    @Autowired
    private FileAssetRecordRepository repository;

    @Test
    void shouldFindByIdempotencyKeyAndLatestStoredPath() {
        FileAssetRecord oldVersion = baseRecord("FR-OLD", "/data/inbound/a.csv");
        oldVersion.setLatestVersion(false);
        oldVersion.setVersionNo(1);
        oldVersion.setIdempotencyKey("INBOUND|ERP|NA|HASH-A");
        repository.saveAndFlush(oldVersion);

        FileAssetRecord latest = baseRecord("FR-NEW", "/data/inbound/a.csv");
        latest.setVersionNo(2);
        latest.setIdempotencyKey("INBOUND|ERP|NA|HASH-B");
        repository.saveAndFlush(latest);

        var byIdempotencyKey = repository.findByIdempotencyKey("INBOUND|ERP|NA|HASH-B");
        var byStoredPath = repository.findFirstByStoredPathAndLatestVersionTrueOrderByCreatedAtDesc("/data/inbound/a.csv");

        assertTrue(byIdempotencyKey.isPresent());
        assertEquals("FR-NEW", byIdempotencyKey.get().getFileNo());
        assertTrue(byStoredPath.isPresent());
        assertEquals("FR-NEW", byStoredPath.get().getFileNo());
    }

    @Test
    void shouldEnforceUniqueIdempotencyKey() {
        FileAssetRecord first = baseRecord("FR-ONE", "/data/inbound/one.csv");
        first.setIdempotencyKey("INBOUND|ERP|NA|HASH-DUP");
        repository.saveAndFlush(first);

        FileAssetRecord duplicate = baseRecord("FR-TWO", "/data/inbound/two.csv");
        duplicate.setIdempotencyKey("INBOUND|ERP|NA|HASH-DUP");

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(duplicate));
    }

    private FileAssetRecord baseRecord(String fileNo, String storedPath) {
        FileAssetRecord record = new FileAssetRecord();
        record.setFileNo(fileNo);
        record.setSourceSystem("ERP");
        record.setBizType("FILE_RECEPTION");
        record.setFileDirection("INBOUND");
        record.setOriginalName(storedPath.substring(storedPath.lastIndexOf('/') + 1));
        record.setStoredName(record.getOriginalName());
        record.setStoredPath(storedPath);
        record.setStorageType("LOCAL");
        record.setFileSize(128L);
        record.setFileHash("HASH-" + fileNo);
        record.setHashAlgorithm("MD5");
        record.setIntegrityVerified(Boolean.TRUE);
        record.setVersionNo(1);
        record.setLatestVersion(Boolean.TRUE);
        record.setStatus("ARRIVED");
        record.setArchiveRequired(Boolean.FALSE);
        record.setArchived(Boolean.FALSE);
        record.setDeletable(Boolean.FALSE);
        record.setDeletedFlag(Boolean.FALSE);
        record.setArrivedTime(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }
}
