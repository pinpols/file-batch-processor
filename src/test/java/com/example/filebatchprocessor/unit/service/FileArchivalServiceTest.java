package com.example.filebatchprocessor.unit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileAssetStatus;
import com.example.filebatchprocessor.model.FileRetentionPolicy;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileRetentionPolicyRepository;
import com.example.filebatchprocessor.service.FileArchivalService;
import com.example.filebatchprocessor.service.FileAssetStateMachineService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileArchivalServiceTest {

    @Mock
    private FileAssetRecordRepository fileAssetRepository;

    @Mock
    private FileRetentionPolicyRepository retentionPolicyRepository;

    @Mock
    private FileAssetStateMachineService stateMachineService;

    @InjectMocks
    private FileArchivalService fileArchivalService;

    private FileAssetRecord testFile;
    private FileRetentionPolicy testPolicy;

    @BeforeEach
    void setUp() {
        testFile = new FileAssetRecord();
        testFile.setId(1L);
        testFile.setFileNo("TEST-001");
        testFile.setStatus("PROCESSED");
        testFile.setProcessedTime(LocalDateTime.now().minusDays(30));

        testPolicy = new FileRetentionPolicy();
        testPolicy.setId(1L);
        testPolicy.setPolicyName("test_policy");
        testPolicy.setFileCategory("INBOUND");
        testPolicy.setRetentionDays(90);
        testPolicy.setArchiveBeforeDelete(true);
        testPolicy.setEnabled(true);
    }

    @Test
    void shouldArchiveFileManually() {
        when(fileAssetRepository.findById(1L)).thenReturn(java.util.Optional.of(testFile));
        when(stateMachineService.transition(eq(1L), eq(FileAssetStatus.ARCHIVED), any(), any()))
                .thenReturn(new FileAssetStateMachineService.TransitionResult(
                        testFile, FileAssetStatus.PROCESSED, FileAssetStatus.ARCHIVED));

        fileArchivalService.archiveFileManually(1L, "operator");

        verify(stateMachineService).transition(eq(1L), eq(FileAssetStatus.ARCHIVED), eq("manual-archive"), any());
    }

    @Test
    void shouldRejectArchiveNonTerminalState() {
        testFile.setStatus("PROCESSING");
        when(fileAssetRepository.findById(1L)).thenReturn(java.util.Optional.of(testFile));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class, () -> fileArchivalService.archiveFileManually(1L, "operator"));
    }

    @Test
    void shouldDeleteFileManually() {
        testFile.setStatus("ARCHIVED");
        when(fileAssetRepository.findById(1L)).thenReturn(java.util.Optional.of(testFile));

        fileArchivalService.deleteFileManually(1L, "operator");

        verify(fileAssetRepository).save(any(FileAssetRecord.class));
    }
}
