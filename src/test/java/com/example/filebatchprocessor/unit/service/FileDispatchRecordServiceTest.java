package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.FileDispatchRecord;
import com.example.filebatchprocessor.repository.FileDispatchRecordRepository;
import com.example.filebatchprocessor.service.FileDispatchRecordService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileDispatchRecordServiceTest {

    @Mock
    private FileDispatchRecordRepository dispatchRecordRepository;

    @InjectMocks
    private FileDispatchRecordService fileDispatchRecordService;

    private FileDispatchRecord testRecord;

    @BeforeEach
    void setUp() {
        testRecord = new FileDispatchRecord();
    }

    @Test
    void shouldFindByDispatchNo() {
        when(dispatchRecordRepository.findByDispatchNo("DISPATCH-001")).thenReturn(Optional.of(testRecord));

        Optional<FileDispatchRecord> result = fileDispatchRecordService.findByDispatchNo("DISPATCH-001");

        assertTrue(result.isPresent());
    }

    @Test
    void shouldFindByLegacyDistributionTaskId() {
        when(dispatchRecordRepository.findByLegacyDistributionTaskId(1L)).thenReturn(Optional.of(testRecord));

        var result = fileDispatchRecordService.findByLegacyDistributionTaskId(1L);

        assertTrue(result.isPresent());
    }
}
