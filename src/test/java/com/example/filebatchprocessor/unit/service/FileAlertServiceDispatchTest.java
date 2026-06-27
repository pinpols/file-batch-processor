package com.example.filebatchprocessor.unit.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.FileAlertLog;
import com.example.filebatchprocessor.repository.FileAlertLogRepository;
import com.example.filebatchprocessor.repository.FileAssetRecordRepository;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.service.FileAlertService;
import com.example.filebatchprocessor.service.alert.AlertDispatcher;
import com.example.filebatchprocessor.service.alert.AlertEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FileAlertServiceDispatchTest {

    private FileAlertService newService(AlertDispatcher dispatcher) {
        FileAlertLogRepository alertRepo = mock(FileAlertLogRepository.class);
        when(alertRepo.save(any(FileAlertLog.class))).thenAnswer(i -> i.getArgument(0));
        FileAlertService svc = new FileAlertService(
                alertRepo,
                mock(FileAssetRecordRepository.class),
                mock(FileDispatchRecordRepository.class),
                new ObjectMapper(),
                dispatcher);
        ReflectionTestUtils.setField(svc, "fileExternalizeMinSeverity", "CRITICAL");
        return svc;
    }

    @Test
    void criticalIsExternalized() {
        AlertDispatcher dispatcher = mock(AlertDispatcher.class);
        FileAlertService svc = newService(dispatcher);
        svc.createAlert(
                "FILE_UNPROCESSED_LONG",
                "FILE_UNPROCESSED",
                "CRITICAL",
                "msg",
                null,
                null,
                null,
                null,
                Map.of());
        verify(dispatcher, times(1)).dispatch(any(AlertEvent.class));
    }

    @Test
    void warningIsNotExternalized() {
        AlertDispatcher dispatcher = mock(AlertDispatcher.class);
        FileAlertService svc = newService(dispatcher);
        svc.createAlert(
                "FILE_TIMEOUT",
                "FILE_UNPROCESSED",
                "WARNING",
                "msg",
                null,
                null,
                null,
                null,
                Map.of());
        verify(dispatcher, never()).dispatch(any(AlertEvent.class));
    }
}
