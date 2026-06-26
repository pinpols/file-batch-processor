package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.FileAssetStateMachineService;
import com.example.filebatchprocessor.service.FileReceptionGuardService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileAssetServiceTest {

    @Mock
    private FileAssetRecordRepository repository;

    @Mock
    private FileAssetStateMachineService stateMachineService;

    @Mock
    private FileReceptionGuardService receptionGuardService;

    @InjectMocks
    private FileAssetService fileAssetService;

    private FileAssetRecord testRecord;

    @BeforeEach
    void setUp() {
        testRecord = new FileAssetRecord();
        testRecord.setId(1L);
    }

    @Test
    void shouldFindById() {
        when(repository.findById(1L)).thenReturn(Optional.of(testRecord));

        Optional<FileAssetRecord> result = fileAssetService.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        Optional<FileAssetRecord> result = fileAssetService.findById(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindLatestByStoredPath() {
        when(repository.findFirstByStoredPathAndLatestVersionTrueOrderByCreatedAtDesc("/path/to/file"))
                .thenReturn(Optional.of(testRecord));

        Optional<FileAssetRecord> result = fileAssetService.findLatestByStoredPath("/path/to/file");

        assertTrue(result.isPresent());
    }

    @Test
    void shouldFindDuplicateInbound() {
        when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        Optional<FileAssetRecord> result = fileAssetService.findDuplicateInbound("SOURCE_A", "abc123");

        assertTrue(result.isEmpty());
    }
}
