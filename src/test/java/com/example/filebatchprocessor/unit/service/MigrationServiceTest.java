package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.MigrationStatus;
import com.example.filebatchprocessor.repository.*;
import com.example.filebatchprocessor.service.MigrationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void setUp() {}

    @Test
    void shouldGetMigrationStatus() {
        MigrationStatus status = new MigrationStatus();
        status.setMigrationName("test-migration");
        status.setStatus("COMPLETED");
        when(migrationStatusRepository.findByMigrationName("test-migration")).thenReturn(Optional.of(status));

        Map<String, Object> result = migrationService.getMigrationStatus("test-migration");

        // 断言映射出的实际字段值,而非仅断言非空
        assertEquals("test-migration", result.get("name"));
        assertEquals("COMPLETED", result.get("status"));
    }

    @Test
    void shouldReturnNotStartedWhenMigrationMissing() {
        when(migrationStatusRepository.findByMigrationName("absent")).thenReturn(Optional.empty());

        Map<String, Object> result = migrationService.getMigrationStatus("absent");

        // 缺失迁移应回落到 NOT_STARTED 哨兵态
        assertEquals("NOT_STARTED", result.get("status"));
        assertNull(result.get("name"));
    }

    @Test
    void shouldGetAllMigrations() {
        MigrationStatus a = new MigrationStatus();
        a.setMigrationName("m-a");
        a.setStatus("RUNNING");
        a.setMigrationPhase("DUAL_WRITE");
        when(migrationStatusRepository.findAll()).thenReturn(List.of(a));

        List<Map<String, Object>> result = migrationService.getAllMigrations();

        // 断言每条迁移被正确投影成 map
        assertEquals(1, result.size());
        assertEquals("m-a", result.get(0).get("name"));
        assertEquals("RUNNING", result.get(0).get("status"));
        assertEquals("DUAL_WRITE", result.get(0).get("phase"));
    }

    @Test
    void shouldGetMigrationHealth() {
        when(fileAssetRepository.count()).thenReturn(7L);
        when(dispatchRecordRepository.count()).thenReturn(3L);
        // 无遗留 reception queue → 覆盖率应为 100%,可切读/可下线
        when(receptionQueueRepository.findAll()).thenReturn(List.of());
        when(receptionQueueRepository.count()).thenReturn(0L);

        Map<String, Object> result = migrationService.getMigrationHealth();

        assertEquals(7L, result.get("fileRecordCount"));
        assertEquals(3L, result.get("dispatchRecordCount"));
        assertEquals(100.0, (double) result.get("receptionQueueCoverage"), 0.0001);
        assertEquals(0L, result.get("legacyReceptionQueueCount"));
        assertEquals(true, result.get("readyForReadSwitch"));
        assertEquals(true, result.get("readyForDeprecation"));
    }

    @Test
    void switchToNewModelShouldNormalizeSupportedType() {
        when(migrationStatusRepository.findByMigrationName("READ_SWITCH_FILE_RECORD"))
                .thenReturn(Optional.empty());
        when(migrationStatusRepository.save(any(MigrationStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = migrationService.switchToNewModel("file-record");

        assertEquals("FILE_RECORD", result.get("table"));
        assertEquals("SWITCHED", result.get("status"));
        verify(migrationStatusRepository).findByMigrationName("READ_SWITCH_FILE_RECORD");
    }

    @Test
    void switchToNewModelShouldRejectUnknownType() {
        assertThrows(IllegalArgumentException.class, () -> migrationService.switchToNewModel("unknown-table"));
        verifyNoInteractions(migrationStatusRepository);
    }

    @Test
    void deprecateLegacyTableShouldRejectUnknownTable() {
        assertThrows(IllegalArgumentException.class, () -> migrationService.deprecateLegacyTable("job_instance"));
        verifyNoInteractions(migrationStatusRepository);
    }
}
