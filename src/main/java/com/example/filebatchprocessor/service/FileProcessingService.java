package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileData;
import com.example.filebatchprocessor.repository.FileDataRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileDataRepository fileDataRepository;
    private final PartitionedImportService partitionedImportService;

    @Value("${file.upload.directory:./uploads}")
    private String uploadDirectory;

    @Transactional
    public FileData saveFile(MultipartFile file) throws IOException {
        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDirectory);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Save file to filesystem
        String originalFilename = file.getOriginalFilename();
        Path filePath = uploadPath.resolve(originalFilename);
        file.transferTo(filePath);

        // Save file metadata to database
        FileData fileData = new FileData();
        fileData.setFileName(originalFilename);
        fileData.setFilePath(filePath.toString());
        fileData.setStatus("UPLOADED");
        fileData.setProcessTime(LocalDateTime.now());

        return fileDataRepository.save(fileData);
    }

    @Transactional
    public void processFile(Long fileId) {
        fileDataRepository.findById(fileId).ifPresent(fileData -> {
            try {
                String content = Files.readString(Paths.get(fileData.getFilePath()));
                String processedContent = content.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .map(String::toUpperCase)
                        .reduce((a, b) -> a + System.lineSeparator() + b)
                        .orElse("");

                fileData.setContent(processedContent);
                fileData.setStatus("PROCESSED");
                fileData.setProcessTime(LocalDateTime.now());

                fileDataRepository.save(fileData);
                log.info("Processed file: {}", fileData.getFileName());
            } catch (IOException e) {
                log.error("Error processing file: {}", fileData.getFileName(), e);
                fileData.setStatus("ERROR");
                fileDataRepository.save(fileData);
            }
        });
    }

    @Transactional
    public void sendProcessedFile(Long fileId) {
        fileDataRepository.findById(fileId).ifPresent(fileData -> {
            if (!"PROCESSED".equalsIgnoreCase(fileData.getStatus())) {
                fileData.setStatus("ERROR");
                fileData.setProcessTime(LocalDateTime.now());
                fileDataRepository.save(fileData);
                log.warn(
                        "Skip send because file is not processed: id={}, status={}",
                        fileData.getId(),
                        fileData.getStatus());
                return;
            }
            fileData.setStatus("SENT");
            fileData.setProcessTime(LocalDateTime.now());
            fileDataRepository.save(fileData);
            log.info("Sent file: {}", fileData.getFileName());
        });
    }

    @Transactional
    public void writeToPartitionedTable(Long fileId) {
        fileDataRepository.findById(fileId).ifPresent(fileData -> {
            if (!"SENT".equalsIgnoreCase(fileData.getStatus()) && !"PROCESSED".equalsIgnoreCase(fileData.getStatus())) {
                fileData.setStatus("ERROR");
                fileData.setProcessTime(LocalDateTime.now());
                fileDataRepository.save(fileData);
                log.warn(
                        "Skip write because file is not in writable state: id={}, status={}",
                        fileData.getId(),
                        fileData.getStatus());
                return;
            }
            try {
                String content = fileData.getContent();
                if (content == null || content.isBlank()) {
                    content = Files.readString(Paths.get(fileData.getFilePath()));
                }

                AtomicInteger imported = new AtomicInteger();
                String batchDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                String sourceFile = fileData.getFileName() == null ? "unknown" : fileData.getFileName();

                content.lines()
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .forEach(line -> {
                            String[] cols = line.split(",", 3);
                            String businessKey = cols.length > 0 && !cols[0].isBlank()
                                    ? cols[0].trim()
                                    : sourceFile + ":" + imported.get();
                            String name = cols.length > 1 ? cols[1].trim() : businessKey;
                            String description = cols.length > 2 ? cols[2].trim() : "";
                            partitionedImportService.importRecord(
                                    businessKey, name, description, batchDate, sourceFile, sha256Hex(line));
                            imported.incrementAndGet();
                        });

                fileData.setStatus("COMPLETED");
                fileData.setProcessTime(LocalDateTime.now());
                fileDataRepository.save(fileData);
                log.info(
                        "Data from file {} written to partitioned table, imported={} records",
                        fileData.getFileName(),
                        imported.get());
            } catch (Exception ex) {
                fileData.setStatus("ERROR");
                fileData.setProcessTime(LocalDateTime.now());
                fileDataRepository.save(fileData);
                log.error("Failed writing file to partitioned table: id={}", fileData.getId(), ex);
            }
        });
    }

    public List<FileData> getPendingFiles() {
        return fileDataRepository.findByStatus("UPLOADED");
    }

    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
