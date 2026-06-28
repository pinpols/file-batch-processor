package com.example.filebatchprocessor.unit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.FileAlertLog;
import com.example.filebatchprocessor.repository.FileAlertLogRepository;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.service.FileAlertService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FileAlertServiceTest {

    @Mock
    private FileAlertLogRepository alertLogRepository;

    @Mock
    private FileAssetRecordRepository fileAssetRepository;

    @Mock
    private FileDispatchRecordRepository dispatchRecordRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private com.example.filebatchprocessor.service.alert.AlertDispatcher alertDispatcher;

    @InjectMocks
    private FileAlertService fileAlertService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileAlertService, "enabled", true);
        ReflectionTestUtils.setField(fileAlertService, "timeoutMinutes", 120L);
        ReflectionTestUtils.setField(fileAlertService, "unprocessedThreshold", 100L);
        ReflectionTestUtils.setField(fileAlertService, "dispatchAckTimeoutMinutes", 60L);
    }

    @Test
    void shouldCreateAlert() {
        when(alertLogRepository.save(any(FileAlertLog.class))).thenAnswer(inv -> inv.getArgument(0));

        FileAlertLog alert = fileAlertService.createAlert(
                "TEST_ALERT",
                "FILE_TIMEOUT",
                "WARNING",
                "Test alert",
                1L,
                "SOURCE",
                "2026-01-01",
                "TARGET",
                Map.of("key", "value"));

        verify(alertLogRepository).save(any(FileAlertLog.class));
    }

    @Test
    void shouldDeduplicateUnresolvedAlertAndEscalateSeverity() {
        FileAlertLog existing = new FileAlertLog();
        existing.setId(10L);
        existing.setAlertCode("FILE_TIMEOUT");
        existing.setFileRecordId(1L);
        existing.setTargetSystem("TARGET");
        existing.setSeverity("WARNING");
        when(alertLogRepository.findFirstByAlertCodeAndFileRecordIdAndTargetSystemAndResolvedFalseOrderByCreatedAtDesc(
                        eq("FILE_TIMEOUT"), eq(1L), eq("TARGET")))
                .thenReturn(Optional.of(existing));
        when(alertLogRepository.save(any(FileAlertLog.class))).thenAnswer(inv -> inv.getArgument(0));

        fileAlertService.createAlert(
                "FILE_TIMEOUT",
                "FILE_UNPROCESSED",
                "CRITICAL",
                "Still timed out",
                1L,
                "SOURCE",
                "2026-01-01",
                "TARGET",
                Map.of("key", "value"));

        verify(alertLogRepository).save(existing);
        org.junit.jupiter.api.Assertions.assertEquals("CRITICAL", existing.getSeverity());
        org.junit.jupiter.api.Assertions.assertEquals("Still timed out", existing.getTitle());
    }

    @Test
    void shouldAcknowledgeAlert() {
        FileAlertLog alert = new FileAlertLog();
        alert.setId(1L);
        alert.setAcknowledged(false);

        when(alertLogRepository.findById(1L)).thenReturn(java.util.Optional.of(alert));
        when(alertLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileAlertService.acknowledgeAlert(1L, "operator");

        verify(alertLogRepository).save(any(FileAlertLog.class));
    }

    @Test
    void shouldResolveAlert() {
        FileAlertLog alert = new FileAlertLog();
        alert.setId(1L);
        alert.setResolved(false);

        when(alertLogRepository.findById(1L)).thenReturn(java.util.Optional.of(alert));
        when(alertLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileAlertService.resolveAlert(1L, "operator", "Resolved");

        verify(alertLogRepository).save(any(FileAlertLog.class));
    }
}
