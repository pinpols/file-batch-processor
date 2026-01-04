package com.example.filebatchprocessor.service;

import com.example.filebatchprocessor.model.FileData;
import com.example.filebatchprocessor.repository.FileDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingService {

    private final FileDataRepository fileDataRepository;

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
                // Read file content
                String content = new String(Files.readAllBytes(Paths.get(fileData.getFilePath())));
                
                // Process the content (example: convert to uppercase)
                String processedContent = content.toUpperCase();
                
                // Update file data
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
            // In a real scenario, implement logic to send the file to a destination
            // For example, upload to SFTP, send via email, etc.
            
            fileData.setStatus("SENT");
            fileData.setProcessTime(LocalDateTime.now());
            fileDataRepository.save(fileData);
            log.info("Sent file: {}", fileData.getFileName());
        });
    }

    @Transactional
    public void writeToPartitionedTable(Long fileId) {
        fileDataRepository.findById(fileId).ifPresent(fileData -> {
            // In a real scenario, implement logic to write to a partitioned table
            // This could involve using JdbcTemplate, JPA, or other data access methods
            
            fileData.setStatus("COMPLETED");
            fileData.setProcessTime(LocalDateTime.now());
            fileDataRepository.save(fileData);
            log.info("Data from file {} written to partitioned table", fileData.getFileName());
        });
    }

    public List<FileData> getPendingFiles() {
        return fileDataRepository.findByStatus("UPLOADED");
    }
}
