package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.MigrationStatus;
import com.example.filebatchprocessor.repository.*;
import com.example.filebatchprocessor.service.MigrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

    @Mock
    private MigrationStatusRepository migrationStatusRepository;

    @Mock
    private FileAssetRecordRepository fileAssetRepository;

    @Mock
    private FileDispatchRecordRepository dispatchRecordRepository;

    @Mock
    private FileReceptionQueueRepository receptionQueueRepository;

    @Mock
    private FileDistributionTaskRepository distributionTaskRepository;

    @InjectMocks
    private MigrationService migrationService;

    @BeforeEach
    void setUp() {
    }

    @Test
    void shouldGetMigrationStatus() {
        MigrationStatus status = new MigrationStatus();
        status.setMigrationName("test-migration");
        status.setStatus("COMPLETED");
        when(migrationStatusRepository.findByMigrationName("test-migration")).thenReturn(Optional.of(status));

        Map<String, Object> result = migrationService.getMigrationStatus("test-migration");

        assertNotNull(result);
    }

    @Test
    void shouldGetAllMigrations() {
        lenient().when(migrationStatusRepository.findAll()).thenReturn(List.of());

        List<Map<String, Object>> result = migrationService.getAllMigrations();

        assertNotNull(result);
    }

    @Test
    void shouldGetMigrationHealth() {
        lenient().when(migrationStatusRepository.count()).thenReturn(0L);
        lenient().when(fileAssetRepository.count()).thenReturn(0L);
        lenient().when(dispatchRecordRepository.count()).thenReturn(0L);

        Map<String, Object> result = migrationService.getMigrationHealth();

        assertNotNull(result);
    }
}
