package com.example.filebatchprocessor.unit.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.filebatchprocessor.model.FileAssetRecord;
import com.example.filebatchprocessor.model.FileReceptionQueue;
import com.example.filebatchprocessor.repository.FileReceptionQueueRepository;
import com.example.filebatchprocessor.service.FileAssetService;
import com.example.filebatchprocessor.service.FileProcessLogService;
import com.example.filebatchprocessor.service.FileReceptionService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileReceptionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReceiveFile() throws Exception {
        FileReceptionQueueRepository repository = mock(FileReceptionQueueRepository.class);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        FileProcessLogService fileProcessLogService = mock(FileProcessLogService.class);
        FileReceptionService service = new FileReceptionService(repository, fileAssetService, fileProcessLogService);

        Path source = tempDir.resolve("incoming.csv");
        Files.writeString(source, "id,name\n1,Alice\n");

        FileAssetRecord fileRecord = new FileAssetRecord();
        when(fileAssetService.registerInboundFile(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(fileRecord);
        when(repository.save(any(FileReceptionQueue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FileReceptionQueue received = service.receiveFile("incoming.csv", source.toString(), "ERP");

        assertNotNull(received);
        verify(repository).save(any(FileReceptionQueue.class));
    }
}
