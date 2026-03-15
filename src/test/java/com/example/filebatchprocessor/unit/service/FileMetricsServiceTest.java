package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.FileMetricsSnapshot;
import com.example.filebatchprocessor.repository.DlqRecordRepository;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.repository.FileMetricsSnapshotRepository;
import com.example.filebatchprocessor.service.FileMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileMetricsServiceTest {

    @Mock
    private FileAssetRecordRepository fileAssetRepository;

    @Mock
    private FileDispatchRecordRepository dispatchRecordRepository;

    @Mock
    private FileMetricsSnapshotRepository metricsRepository;

    @Mock
    private DlqRecordRepository dlqRecordRepository;

    @InjectMocks
    private FileMetricsService fileMetricsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileMetricsService, "enabled", true);
    }

    @Test
    void shouldCaptureMetrics() {
        lenient().when(fileAssetRepository.count()).thenReturn(100L);
        lenient().when(dispatchRecordRepository.count()).thenReturn(50L);
        lenient().when(dlqRecordRepository.count()).thenReturn(3L);
        when(metricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileMetricsService.captureMetrics();

        verify(metricsRepository).save(any(FileMetricsSnapshot.class));
    }

    @Test
    void shouldGetTodayMetrics() {
        FileMetricsSnapshot snapshot = new FileMetricsSnapshot();
        lenient().when(metricsRepository.findFirstByMetricDateOrderBySnapshotTimeDesc(any(LocalDate.class)))
                .thenReturn(Optional.of(snapshot));

        FileMetricsSnapshot result = fileMetricsService.getTodayMetrics();

        org.junit.jupiter.api.Assertions.assertNotNull(result);
    }

    @Test
    void shouldGetMetricsHistory() {
        lenient().when(metricsRepository.findAll()).thenReturn(List.of());

        var result = fileMetricsService.getMetricsHistory(7);

        org.junit.jupiter.api.Assertions.assertNotNull(result);
    }
}
