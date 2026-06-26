package com.example.filebatchprocessor.integration.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.support.PostgresContainerSupport;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
        properties = {
            "spring.flyway.enabled=true",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.batch.job.enabled=false",
            "batch.alert.enabled=false",
            "orchestration.enabled=false"
        })
class FileDispatchRecordRepositoryIT extends PostgresContainerSupport {

    @Autowired
    private FileAssetRecordRepository fileAssetRecordRepository;

    @Autowired
    private FileDispatchRecordRepository fileDispatchRecordRepository;

    @Test
    void shouldFindByDispatchKeyAndLegacyTaskId() {
        FileAssetRecord fileRecord = fileAssetRecordRepository.saveAndFlush(baseFileRecord("FR-100-A"));

        FileDispatchRecord dispatchRecord = new FileDispatchRecord();
        dispatchRecord.setDispatchNo("FD-0001");
        dispatchRecord.setDispatchKey(fileRecord.getFileNo() + "|1|HTTP|http://target");
        dispatchRecord.setFileRecordId(fileRecord.getId());
        dispatchRecord.setLegacyDistributionTaskId(9001L);
        dispatchRecord.setTargetSystem("HTTP");
        dispatchRecord.setDispatchChannel("HTTP");
        dispatchRecord.setTargetAddress("http://target");
        dispatchRecord.setFileVersionNo(1);
        dispatchRecord.setDispatchStatus("PENDING");
        dispatchRecord.setAckStatus("NOT_REQUIRED");
        dispatchRecord.setAttemptCount(0);
        dispatchRecord.setMaxAttempts(3);
        fileDispatchRecordRepository.saveAndFlush(dispatchRecord);

        var byDispatchKey =
                fileDispatchRecordRepository.findByDispatchKey(fileRecord.getFileNo() + "|1|HTTP|http://target");
        var byLegacyTaskId = fileDispatchRecordRepository.findByLegacyDistributionTaskId(9001L);

        assertTrue(byDispatchKey.isPresent());
        assertEquals("FD-0001", byDispatchKey.get().getDispatchNo());
        assertTrue(byLegacyTaskId.isPresent());
        assertEquals("HTTP", byLegacyTaskId.get().getDispatchChannel());
    }

    @Test
    void shouldEnforceUniqueDispatchKey() {
        FileAssetRecord fileRecord = fileAssetRecordRepository.saveAndFlush(baseFileRecord("FR-100-B"));

        FileDispatchRecord first = new FileDispatchRecord();
        first.setDispatchNo("FD-0002");
        first.setDispatchKey(fileRecord.getFileNo() + "|1|HTTP|dup");
        first.setFileRecordId(fileRecord.getId());
        first.setLegacyDistributionTaskId(9002L);
        first.setTargetSystem("HTTP");
        first.setDispatchChannel("HTTP");
        first.setTargetAddress("http://dup");
        first.setFileVersionNo(1);
        fileDispatchRecordRepository.saveAndFlush(first);

        FileDispatchRecord duplicate = new FileDispatchRecord();
        duplicate.setDispatchNo("FD-0003");
        duplicate.setDispatchKey(fileRecord.getFileNo() + "|1|HTTP|dup");
        duplicate.setFileRecordId(fileRecord.getId());
        duplicate.setLegacyDistributionTaskId(9003L);
        duplicate.setTargetSystem("HTTP");
        duplicate.setDispatchChannel("HTTP");
        duplicate.setTargetAddress("http://dup");
        duplicate.setFileVersionNo(1);

        assertThrows(DataIntegrityViolationException.class, () -> fileDispatchRecordRepository.saveAndFlush(duplicate));
    }

    @Test
    void shouldFindAckTimeoutCandidatesByStatusAndDispatchNo() {
        FileAssetRecord fileRecord = fileAssetRecordRepository.saveAndFlush(baseFileRecord("FR-100-C"));

        FileDispatchRecord dispatchRecord = new FileDispatchRecord();
        dispatchRecord.setDispatchNo("FD-ACK-9004");
        dispatchRecord.setDispatchKey(fileRecord.getFileNo() + "|1|HTTP|ack");
        dispatchRecord.setFileRecordId(fileRecord.getId());
        dispatchRecord.setLegacyDistributionTaskId(9004L);
        dispatchRecord.setTargetSystem("HTTP");
        dispatchRecord.setDispatchChannel("HTTP");
        dispatchRecord.setTargetAddress("http://ack");
        dispatchRecord.setFileVersionNo(1);
        dispatchRecord.setDispatchStatus("SUCCESS");
        dispatchRecord.setAckRequired(Boolean.TRUE);
        dispatchRecord.setAckStatus("PENDING");
        dispatchRecord.setAckTimeoutMinutes(30);
        dispatchRecord.setAckDeadlineAt(LocalDateTime.now().minusMinutes(1));
        dispatchRecord.setCreatedJobInstanceId(null);
        fileDispatchRecordRepository.saveAndFlush(dispatchRecord);

        var byDispatchNo = fileDispatchRecordRepository.findByDispatchNo("FD-ACK-9004");
        var ackPending = fileDispatchRecordRepository.findByAckRequiredTrueAndAckStatus("PENDING");

        assertTrue(byDispatchNo.isPresent());
        assertEquals(9004L, byDispatchNo.get().getLegacyDistributionTaskId());
        assertEquals(1, ackPending.size());
        assertEquals("FD-ACK-9004", ackPending.get(0).getDispatchNo());
    }

    private FileAssetRecord baseFileRecord(String fileNo) {
        FileAssetRecord record = new FileAssetRecord();
        record.setFileNo(fileNo);
        record.setSourceSystem("ERP");
        record.setBizType("FILE_DISTRIBUTION");
        record.setFileDirection("OUTBOUND");
        record.setOriginalName("dispatch.csv");
        record.setStoredName("dispatch.csv");
        record.setStoredPath("/data/outbound/dispatch.csv");
        record.setStorageType("LOCAL");
        record.setFileSize(256L);
        record.setFileHash("HASH-DISPATCH");
        record.setHashAlgorithm("MD5");
        record.setIntegrityVerified(Boolean.TRUE);
        record.setVersionNo(1);
        record.setLatestVersion(Boolean.TRUE);
        record.setStatus("PROCESSED");
        record.setArchiveRequired(Boolean.FALSE);
        record.setArchived(Boolean.FALSE);
        record.setDeletable(Boolean.FALSE);
        record.setDeletedFlag(Boolean.FALSE);
        record.setArrivedTime(LocalDateTime.now());
        record.setProcessedTime(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }
}
