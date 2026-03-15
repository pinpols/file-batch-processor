package com.example.filebatchprocessor.unit.service;

import com.example.filebatchprocessor.model.FileProcessLog;
import com.example.filebatchprocessor.repository.FileProcessLogRepository;
import com.example.filebatchprocessor.service.FileProcessLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileProcessLogServiceTest {

    @Test
    void shouldLogProcessEvent() {
        FileProcessLogRepository repository = mock(FileProcessLogRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        
        FileProcessLogService service = new FileProcessLogService(repository, objectMapper);

        when(repository.save(any(FileProcessLog.class))).thenAnswer(inv -> inv.getArgument(0));

        FileProcessLog result = service.log(1L, "TEST", "INFO", "FROM", "TO", "SUCCESS", null, null, 0, null, "message", null);

        assertNotNull(result);
        verify(repository).save(any(FileProcessLog.class));
    }
}
