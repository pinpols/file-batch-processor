package com.example.filebatchprocessor.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.filebatchprocessor.model.FileData;
import com.example.filebatchprocessor.repository.FileDataRepository;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileProcessingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveUploadedFileAndMetadata() throws Exception {
        FileDataRepository repository = mock(FileDataRepository.class);
        PartitionedImportService partitionedImportService = mock(PartitionedImportService.class);
        FileProcessingService service = new FileProcessingService(repository, partitionedImportService);
        setUploadDirectory(service, tempDir.toString());

        when(repository.save(any(FileData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file =
                new MockMultipartFile("file", "demo.csv", "text/csv", "k1,name1,desc1\nk2,name2,desc2\n".getBytes());

        FileData saved = service.saveFile(file);

        assertEquals("UPLOADED", saved.getStatus());
        assertTrue(Files.exists(Path.of(saved.getFilePath())));
        verify(repository).save(any(FileData.class));
    }

    @Test
    void shouldProcessFileToNormalizedUppercaseContent() throws Exception {
        FileDataRepository repository = mock(FileDataRepository.class);
        PartitionedImportService partitionedImportService = mock(PartitionedImportService.class);
        FileProcessingService service = new FileProcessingService(repository, partitionedImportService);

        Path source = tempDir.resolve("in.csv");
        Files.writeString(source, " k1,name1,desc1 \n\nk2,name2,desc2\n");

        FileData fileData = new FileData();
        fileData.setId(1L);
        fileData.setFileName("in.csv");
        fileData.setFilePath(source.toString());
        fileData.setStatus("UPLOADED");

        when(repository.findById(1L)).thenReturn(Optional.of(fileData));
        when(repository.save(any(FileData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processFile(1L);

        assertEquals("PROCESSED", fileData.getStatus());
        assertEquals("K1,NAME1,DESC1" + System.lineSeparator() + "K2,NAME2,DESC2", fileData.getContent());
        verify(repository, times(1)).save(fileData);
    }

    @Test
    void shouldSendOnlyProcessedFile() {
        FileDataRepository repository = mock(FileDataRepository.class);
        PartitionedImportService partitionedImportService = mock(PartitionedImportService.class);
        FileProcessingService service = new FileProcessingService(repository, partitionedImportService);

        FileData fileData = new FileData();
        fileData.setId(2L);
        fileData.setStatus("UPLOADED");
        when(repository.findById(2L)).thenReturn(Optional.of(fileData));
        when(repository.save(any(FileData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.sendProcessedFile(2L);
        assertEquals("ERROR", fileData.getStatus());

        fileData.setStatus("PROCESSED");
        service.sendProcessedFile(2L);
        assertEquals("SENT", fileData.getStatus());
    }

    @Test
    void shouldWriteProcessedContentToPartitionedTable() {
        FileDataRepository repository = mock(FileDataRepository.class);
        PartitionedImportService partitionedImportService = mock(PartitionedImportService.class);
        FileProcessingService service = new FileProcessingService(repository, partitionedImportService);

        FileData fileData = new FileData();
        fileData.setId(3L);
        fileData.setFileName("batch.csv");
        fileData.setStatus("SENT");
        fileData.setContent("k1,name1,desc1\nk2,name2,desc2\n");

        when(repository.findById(3L)).thenReturn(Optional.of(fileData));
        when(repository.save(any(FileData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.writeToPartitionedTable(3L);

        assertEquals("COMPLETED", fileData.getStatus());
        verify(partitionedImportService, times(2))
                .importRecord(anyString(), anyString(), anyString(), anyString(), eq("batch.csv"), anyString());
    }

    @Test
    void shouldMarkErrorWhenWriteStateInvalid() {
        FileDataRepository repository = mock(FileDataRepository.class);
        PartitionedImportService partitionedImportService = mock(PartitionedImportService.class);
        FileProcessingService service = new FileProcessingService(repository, partitionedImportService);

        FileData fileData = new FileData();
        fileData.setId(4L);
        fileData.setStatus("UPLOADED");
        when(repository.findById(4L)).thenReturn(Optional.of(fileData));
        when(repository.save(any(FileData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.writeToPartitionedTable(4L);

        assertEquals("ERROR", fileData.getStatus());
        verify(partitionedImportService, never())
                .importRecord(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private void setUploadDirectory(FileProcessingService service, String value) throws Exception {
        Field field = FileProcessingService.class.getDeclaredField("uploadDirectory");
        field.setAccessible(true);
        field.set(service, value);
    }
}
